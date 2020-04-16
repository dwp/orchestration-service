package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import uk.gov.dwp.dataworks.exceptions.FailedToExecuteCreateServiceRequestException
import uk.gov.dwp.dataworks.exceptions.FailedToExecuteRunTaskRequestException
import uk.gov.dwp.dataworks.exceptions.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.lang.StringBuilder

@Service
class TaskDeploymentService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService ::class.java))
    }

    val configurationService = ConfigurationService()

    private fun createService (ecs_cluster_name: String, user_name: String, ecsClient: EcsClient, containerPort : Int,targetGroupArn: String) {

        val alb: LoadBalancer = LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(containerPort).build()

        val serviceBuilder = CreateServiceRequest.builder().cluster(ecs_cluster_name).loadBalancers(alb).serviceName("${user_name}-analytical-workspace").taskDefinition(configurationService.getStringConfig(ConfigKey.USER_CONTAINER_TASK_DEFINITION)).loadBalancers(alb).desiredCount(1).build()
        logger.info("Creating Service...")

        try {
            val service = ecsClient.createService(serviceBuilder)
            logger.info("service.responseMetadata = ${service.responseMetadata()}")
        } catch (e: Exception) {
            logger.error("Error while creating the service", e)
            throw FailedToExecuteCreateServiceRequestException()
        }
    }

    fun getVacantPriorityValue (rulesResponse : DescribeRulesResponse) : Int {

        val rulePriorities = rulesResponse.rules().map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        if (rulePriorities.size >= 1000) throw UpperRuleLimitReachedException()
        for(priority in 0..1000) {
            if(!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException()
    }

    fun taskDefinitionWithOverride(ecsClusterName: String, emrClusterHostName: String, albName :String, userName: String, containerPort : Int , jupyterCpu : Int , jupyterMemory: Int, additionalPermissions: List<String>) {

        val ecsClient = EcsClient.builder().region(configurationService.awsRegion).build()
        val albClient = ElasticLoadBalancingV2Client.builder().region(configurationService.awsRegion).build()

        logger.info("Getting alb information...")

        val albRequest = DescribeLoadBalancersRequest.builder().names(albName).build()
        val albResponse = albClient.describeLoadBalancers(albRequest)

        logger.info("Getting Listener information...")

        val albListenerRequest = DescribeListenersRequest.builder().loadBalancerArn(albResponse.loadBalancers()[0].loadBalancerArn()).build()
        val albListenerResponse = albClient.describeListeners(albListenerRequest)

        logger.info("Creating target group...")

        val tgRequest = CreateTargetGroupRequest.builder().name("$userName-target-group").protocol("HTTPS").vpcId(albResponse.loadBalancers()[0].vpcId()).port(containerPort).build()
        albClient.createTargetGroup(tgRequest)

        logger.info("Getting target group arn...")

        val albTargetGroupRequest = albClient.describeTargetGroups(DescribeTargetGroupsRequest.builder().names("$userName-target-group").build())
        val albTargetGroupArn = albTargetGroupRequest.targetGroups()[0].targetGroupArn()

        val pathPattern = PathPatternConditionConfig.builder().values("/$userName/*").build()
        val albRuleCondition = RuleCondition.builder().field("path-pattern").pathPatternConfig(pathPattern).build()
        val userTargetGroup = TargetGroupTuple.builder().targetGroupArn(albTargetGroupArn).build()
        val forwardAction = ForwardActionConfig.builder().targetGroups(userTargetGroup).build()
        val albRuleAction = Action.builder().type("forward").forwardConfig(forwardAction).build()

        logger.info("Creating listener rule...")

        var rulesResponse = albClient.describeRules(DescribeRulesRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).build())
        albClient.createRule(CreateRuleRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).priority(getVacantPriorityValue(rulesResponse)).conditions(albRuleCondition).actions(albRuleAction).build())

       createService(ecsClusterName, userName, ecsClient, containerPort ,albTargetGroupArn)

        logger.info("Starting Task...")
        try {
            val response = ecsClient.runTask(createRunTaskRequestWithOverides(userName,emrClusterHostName,jupyterMemory,jupyterCpu,ecsClusterName,additionalPermissions))
            logger.info("response.tasks = ${response.tasks()}")
        } catch (e: Exception) {
            logger.error("Error while processing the run task request", e)
            throw FailedToExecuteRunTaskRequestException()
        }
    }

    fun createRunTaskRequestWithOverides(userName: String,emrClusterHostName: String,jupyterMemory: Int,jupyterCpu: Int,ecsClusterName: String, additionalPermissions: List<String>):RunTaskRequest{
        val userNamePair = KeyValuePair.builder()
                .name("user_name")
                .value(userName)
                .build()

        val emrClusterHostName= KeyValuePair.builder()
                .name("emr_cluster_host_name")
                .value(emrClusterHostName)
                .build()

        val chromeOverride= ContainerOverride.builder()
                .name("headless_chrome")
                .environment(userNamePair)
                .build()
        val jupyterOverride= ContainerOverride.builder()
                .name("jupyterHub")
                .environment(userNamePair, emrClusterHostName)
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .build()
        val guacDOverride = ContainerOverride.builder()
                .name("guacd")
                .environment(userNamePair)
                .build()

        val overrides = TaskOverride.builder()
                .containerOverrides(guacDOverride, chromeOverride, jupyterOverride)
                .taskRoleArn(createTaskRoleOverride(additionalPermissions, userName))
                .build()

        return RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-analytical-workspace")
                .build()
    }

    fun createTaskRoleOverride(list: List<String>, userName: String): String{
        val iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()
        fun additionalPermissions(): String{
            val listToString = StringBuilder()
            if(list.isNotEmpty()) for (i in list) listToString.append(",\"$i\"")
            return listToString.toString()
        }
        val assumeRolePolicyDocument = "{" +
        "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [" +
                "    {" +
                "        \"Effect\": \"Allow\"," +
                "        \"Action\": [" +
                "           \"sts:AssumeRole\"" +
                "       ]," +
                "       \"Principal\": {"  +
                "           \"Service\": ["  +
                "           \"ecs-tasks.amazonaws.com\"" +
                "           ]" +
                "        }" +
                "     }" +
                "   ]" +
                "}"

        val taskRolePolicyDocument = "{" +
                "  \"Version\": \"2012-10-17\"," +
                "  \"Statement\": [" +
                    "     {" +
                    "        \"Effect\": \"Allow\"," +
                    "        \"Action\": [" +
                    "            \"ecr:BatchCheckLayerAvailability\"," +
                    "            \"ecr:GetDownloadUrlForLayer\"," +
                    "            \"ecr:BatchGetImage\"," +
                    "            \"logs:CreateLogStream\"," +
                    "            \"logs:PutLogEvents\"" +
                                additionalPermissions() +
                    "       ]," +
                    "       \"Resource\": \"*\"" +
                    "      }" +
                    "   ]" +
                    "}"

        val userPolicyDocument = CreatePolicyRequest.builder().policyDocument(taskRolePolicyDocument).policyName("$userName-task-role-document").build()
        val userTaskPolicy = iamClient.createPolicy(userPolicyDocument)
        val iamRole = iamClient.createRole(CreateRoleRequest.builder().assumeRolePolicyDocument(assumeRolePolicyDocument).roleName("$userName-iam-role").build())
        iamClient.attachRolePolicy(AttachRolePolicyRequest.builder().policyArn(userTaskPolicy.policy().arn()).roleName(iamRole.role().roleName()).build())
        return iamRole.role().arn()
    }
}
