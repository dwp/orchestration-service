package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.awscore.AwsRequest
import software.amazon.awssdk.core.SdkRequest
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*

import java.net.http.HttpRequest

@Service
class TaskDeploymentService {
    @Autowired
    lateinit var credentialsService: CredentialsService

    val awsRegion: Region = kotlin.runCatching { Region.of(System.getenv(ConfigKey.AWS_REGION.toString()))}.getOrDefault(Region.EU_WEST_2)

    private fun createService (ecs_cluster_name: String, user_name: String, ecsClient: EcsClient, targetGroupArn: String) {

        val alb: LoadBalancer = LoadBalancer.builder().targetGroupArn(targetGroupArn).containerPort(443).build()

        val serviceBuilder = CreateServiceRequest.builder().cluster(ecs_cluster_name).loadBalancers(alb).serviceName("${user_name}_test").taskDefinition("mhf_sample_task").loadBalancers(alb).desiredCount(1).build()
        println("Creating Service...")

        try {
            val service = ecsClient.createService(serviceBuilder)
            println("service.responseMetadata = ${service.responseMetadata()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPriorityVal (rulesResponse : DescribeRulesResponse) : Int {

        var consecVals : Int = 1
        for (i in rulesResponse.rules()) {
            if (i.priority() != "default") {
                var intVal = Integer.parseInt(i.priority())
                if (intVal == consecVals) consecVals = consecVals + 1
            }
        }
        if (consecVals >= 1000) throw Exception("The upper limit of 1000 rules has been reached for this load balancer.")
        return consecVals;
    }

    fun taskDefinitionWithOverride(ecs_cluster_name: String, emr_cluster_host_name: String, albName :String, user_name: String, jupyterCpu : Int=512, jupyterMemory: Int = 512) {

        val credentials: AwsCredentialsProvider = credentialsService.getDefaultCredentialsProvider()

        val ecsClient: EcsClient = EcsClient.builder().credentialsProvider(credentials).region(awsRegion).build()
        val albClient: ElasticLoadBalancingV2Client = ElasticLoadBalancingV2Client.builder().credentialsProvider(credentials).region(awsRegion).build()

        println("Getting alb information...")

        val albRequest = DescribeLoadBalancersRequest.builder().names("orchestration-service-lb").build()
        val albResponse: DescribeLoadBalancersResponse = albClient.describeLoadBalancers(albRequest)

        println("Getting Listener information...")

        val albListenerRequest = DescribeListenersRequest.builder().loadBalancerArn(albResponse.loadBalancers()[0].loadBalancerArn()).build()
        val albListenerResponse = albClient.describeListeners(albListenerRequest)

        println("Creating target group...")

        val tgRequest = CreateTargetGroupRequest.builder().name("$user_name-target-group").protocol("HTTPS").vpcId(albResponse.loadBalancers()[0].vpcId()).port(443).build()
        val targetGroupResponse = albClient.createTargetGroup(tgRequest)

        println("Getting target group arn...")

        val albTargetGroupRequest = albClient.describeTargetGroups(DescribeTargetGroupsRequest.builder().names("$user_name-target-group").build())
        val albTargetGroupArn = albTargetGroupRequest.targetGroups()[0].targetGroupArn()

        val pathPattern = PathPatternConditionConfig.builder().values("/$user_name/*").build()
        val albRuleCondition :RuleCondition = RuleCondition.builder().field("path-pattern").pathPatternConfig(pathPattern).build()
        val userTargetGroup = TargetGroupTuple.builder().targetGroupArn(albTargetGroupArn).build()
        val forwardAction = ForwardActionConfig.builder().targetGroups(userTargetGroup).build()
        val albRuleAction = Action.builder().type("forward").forwardConfig(forwardAction).build()

        println("Creating listener rule...")

        var rulesResponse = albClient.describeRules(DescribeRulesRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).build())
        val createRule = albClient.createRule(CreateRuleRequest.builder().listenerArn(albListenerResponse.listeners()[0].listenerArn()).priority(getPriorityVal(rulesResponse)).conditions(albRuleCondition).actions(albRuleAction).build())

       createService(ecs_cluster_name, user_name, ecsClient, albTargetGroupArn)


        val userName: KeyValuePair = KeyValuePair.builder()
                .name("user_name")
                .value(user_name)
                .build()

        val emrClusterHostName: KeyValuePair = KeyValuePair.builder()
                .name("emr_cluster_host_name")
                .value(emr_cluster_host_name)
                .build()

        val chromeOverride: ContainerOverride = ContainerOverride.builder()
                .name("headless_chrome")
                .environment(userName)
                .build()
        val jupyterOverride: ContainerOverride = ContainerOverride.builder()
                .name("jupyterHub")
                .environment(userName, emrClusterHostName)
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .build()
        val guacDOverride: ContainerOverride = ContainerOverride.builder()
                .name("guacd")
                .environment(userName)
                .build()

        val overrides: TaskOverride = TaskOverride.builder()
                .containerOverrides(guacDOverride, chromeOverride, jupyterOverride)
                .build()

        val request: RunTaskRequest = RunTaskRequest.builder()
//                .taskDefinition("mhf_sample_task") // USING FOR TESTING BEFORE TERRAFORM
                .cluster(ecs_cluster_name)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-ui-service")
                .build()

        println("Starting Task...")
        try {
            val response: RunTaskResponse = ecsClient.runTask(request)
            println("response.tasks = ${response.tasks()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
