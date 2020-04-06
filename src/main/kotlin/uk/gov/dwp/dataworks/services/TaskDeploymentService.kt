package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDeploymentService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService ::class.java))
    }

    @Autowired
    lateinit var credentialsService: CredentialsService

    private fun createService (ecs_cluster_name: String, user_name: String, ecsClient: EcsClient, containerPort : Int,targetGroupArn: String) {

        val alb: LoadBalancer = LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(containerPort).build()

        val serviceBuilder = CreateServiceRequest.builder().cluster(ecs_cluster_name).loadBalancers(alb).serviceName("${user_name}_test").taskDefinition("mhf_sample_task").loadBalancers(alb).desiredCount(1).build()
        logger.info("Creating Service...")

        try {
            val service = ecsClient.createService(serviceBuilder)
            logger.info("service.responseMetadata = ${service.responseMetadata()}")
        } catch (e: Exception) {
            logger.error("Error while creating the service", e)
            throw e
        }
    }

    fun getVacantPriorityValue (rulesResponse : DescribeRulesResponse) : Int {

        var nextVacantValue : Int = 1
        for (i in rulesResponse.rules()) {
            if (i.priority() != "default") {
                var intVal = Integer.parseInt(i.priority())
                if (intVal == nextVacantValue) nextVacantValue = nextVacantValue + 1
            }
        }
        if (nextVacantValue >= 1000) throw Exception("The upper limit of 1000 rules has been reached for this load balancer.")
        return nextVacantValue;
    }

    fun taskDefinitionWithOverride(ecsClusterName: String, emrClusterHostName: String, albName :String, userName: String, containerPort : Int , jupyterCpu : Int , jupyterMemory: Int ) {

        val configurationService = ConfigurationService()
        val ecsClient = EcsClient.builder().region(credentialsService.getAwsRegion()).build()
        val albClient = ElasticLoadBalancingV2Client.builder().region(credentialsService.getAwsRegion()).build()

        logger.info("Getting alb information...")

        val albRequest = DescribeLoadBalancersRequest.builder().names(configurationService.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)).build()
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

        val userName = KeyValuePair.builder()
                .name("user_name")
                .value(userName)
                .build()

        val emrClusterHostName= KeyValuePair.builder()
                .name("emr_cluster_host_name")
                .value(emrClusterHostName)
                .build()

        val chromeOverride= ContainerOverride.builder()
                .name("headless_chrome")
                .environment(userName)
                .build()
        val jupyterOverride= ContainerOverride.builder()
                .name("jupyterHub")
                .environment(userName, emrClusterHostName)
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .build()
        val guacDOverride = ContainerOverride.builder()
                .name("guacd")
                .environment(userName)
                .build()

        val overrides = TaskOverride.builder()
                .containerOverrides(guacDOverride, chromeOverride, jupyterOverride)
                .build()

        val request = RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-ui-service")
                .build()

        logger.info("Starting Task...")
        try {
            val response = ecsClient.runTask(request)
            logger.info("response.tasks = ${response.tasks()}")
        } catch (e: Exception) {
            logger.error("Error while processing the run task request", e)
            throw e
        }
    }
}
