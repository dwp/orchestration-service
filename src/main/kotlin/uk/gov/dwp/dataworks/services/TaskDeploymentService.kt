package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import software.amazon.awssdk.services.ecs.model.RunTaskRequest
import software.amazon.awssdk.services.ecs.model.TaskOverride
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.iam.model.AttachRolePolicyRequest
import software.amazon.awssdk.services.iam.model.CreatePolicyRequest
import software.amazon.awssdk.services.iam.model.CreateRoleRequest
import uk.gov.dwp.dataworks.FailedToExecuteRunTaskRequestException
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class TaskDeploymentService {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @Autowired
    private lateinit var configurationService: ConfigurationService

    @Value("classpath:policyDocuments/taskAssumeRolePolicy.json")
    lateinit var taskAssumeRoleDocument: Resource
    private lateinit var taskAssumeRoleString: String

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRolePolicyDocument: Resource
    private lateinit var taskRolePolicyString: String

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    fun taskDefinitionWithOverride(userName: String, jupyterCpu: Int, jupyterMemory: Int, additionalPermissions: List<String>) {
        val containerPort = Integer.parseInt(configurationService.getStringConfig(ConfigKey.USER_CONTAINER_PORT))
        val emrClusterHostName = configurationService.getStringConfig(ConfigKey.EMR_CLUSTER_HOST_NAME)
        val albName = configurationService.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)
        val ecsClusterName = configurationService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME)

        val loadBalancer = awsCommunicator.getLoadBalancerByName(albName)
        val listener = awsCommunicator.getAlbListenerByPort(loadBalancer.loadBalancerArn())
        val targetGroup = awsCommunicator.createTargetGroup(loadBalancer.vpcId(), "$userName-target-group", containerPort)
        // There are 2 LoadBalancer classes in the AWS SDK. The following is the load balancer needed to create an ECS service.
        val ecsLoadBalancer = LoadBalancer.builder()
                .targetGroupArn(targetGroup.targetGroupArn())
                .loadBalancerName(loadBalancer.loadBalancerName())
                .containerName("") //TODO
                .containerPort(containerPort)
                .build()

        awsCommunicator.createAlbRoutingRule(listenerArn = listener.listenerArn(), targetGroupArn = targetGroup.targetGroupArn(), pathPattern = "/$userName/*")
        awsCommunicator.createEcsService(ecsClusterName, "${userName}-analytical-workspace", ecsLoadBalancer)


        try {
            val response = ecsClient.runTask(createRunTaskRequestWithOverrides(userName, emrClusterHostName, jupyterMemory, jupyterCpu, ecsClusterName, additionalPermissions))
            logger.info("ECS tasks run", "instance_arns" to response.tasks().joinToString { it.containerInstanceArn() }, "task_groups" to response.tasks().joinToString { it.group() })
        } catch (e: Exception) {
            logger.error("Error running ECS tasks", e)
            throw FailedToExecuteRunTaskRequestException("Error while processing the Run Task request", e)
        }
    }

    private fun createRunTaskRequestWithOverrides(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, ecsClusterName: String, additionalPermissions: List<String>): RunTaskRequest {
        val usernamePair = "USER" to userName
        val hostnamePair = "EMR_HOST_NAME" to emrHostname

        val screenSize = 1920 to 1080
        val chromeOptsPair = "CHROME_OPTS" to arrayOf(
            "--no-sandbox",
            "--window-position=0,0",
            "--force-device-scale-factor=1",
            "--incognito",
            "--noerrdialogs",
            "--disable-translate",
            "--no-first-run",
            "--fast",
            "--fast-start",
            "--disable-infobars",
            "--disable-features=TranslateUI",
            "--disk-cache-dir=/dev/null",
            "--test-type https://jupyterHub:8443",
            "--kiosk",
            "--window-size=${screenSize.toList().joinToString(",")}"
        ).joinToString(" ")
        val vncScreenSizePair = "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x")

        val chrome = containerOverrideBuilder("headless_chrome", chromeOptsPair, vncScreenSizePair).build()
        val guacd = containerOverrideBuilder("guacd", usernamePair).build()
        val guacamole = containerOverrideBuilder("guacamole", "CLIENT_USERNAME" to userName).build()
        // Jupyter also has configurable resources
        val jupyter = containerOverrideBuilder("jupyterHub", usernamePair, hostnamePair).cpu(jupyterCpu).memory(jupyterMemory).build()

        val overrides = TaskOverride.builder()
                .containerOverrides(guacd, guacamole, chrome, jupyter)
                .taskRoleArn(createTaskRoleOverride(additionalPermissions, userName))
                .build()

        return RunTaskRequest.builder()
                .cluster(ecsClusterName)
                .launchType("EC2")
                .overrides(overrides)
                .taskDefinition("orchestration-service-analytical-workspace")
                .build()
    }

    /**
     * Helper method to wrap a container name and set of overrides into an incomplete [ContainerOverride.Builder] for
     * later consumption.
     */
    private fun containerOverrideBuilder(containerName: String, vararg overrides: Pair<String, String>): ContainerOverride.Builder {
        val overrideKeyPairs = overrides.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
        return ContainerOverride.builder()
                .name(containerName)
                .environment(overrideKeyPairs)
    }

    private fun createTaskRoleOverride(additionalPermissions: List<String>, userName: String): String {
        val iamClient = IamClient.builder().region(Region.AWS_GLOBAL).build()
        parsePolicyDocuments(additionalPermissions)

        val userPolicyDocument = CreatePolicyRequest.builder().policyDocument(taskRolePolicyString).policyName("$userName-task-role-document").build()
        val userTaskPolicy = iamClient.createPolicy(userPolicyDocument).policy()
        val iamRole = iamClient.createRole(CreateRoleRequest.builder().assumeRolePolicyDocument(taskAssumeRoleString).roleName("$userName-iam-role").build()).role()

        iamClient.attachRolePolicy(AttachRolePolicyRequest.builder().policyArn(userTaskPolicy.arn()).roleName(iamRole.roleName()).build())
        logger.info("created iam roles", "role_name" to iamRole.roleName(), "role_arn" to iamRole.arn())
        return iamRole.arn()
    }

    /**
     * Helper method to initialise the lateinit vars [taskAssumeRoleString] and [taskRolePolicyString] by
     * converting the associated `@Value` parameters to Strings and replacing `ADDITIONAL_PERMISSIONS` in
     * [taskRolePolicyString] with the provided [additionalPermissions]
     *
     * @return [Pair] of [taskRolePolicyString] to [taskAssumeRoleString] for ease of access.
     */
    fun parsePolicyDocuments(additionalPermissions: List<String>): Pair<String, String> {
        logger.info("Adding permissions to containers", "permissions" to additionalPermissions.joinToString())
        val permissionsJson = additionalPermissions.joinToString(prefix = "\"", separator = "\",\"", postfix = "\"")

        taskAssumeRoleString = taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        taskRolePolicyString = taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
                .replace("ADDITIONAL_PERMISSIONS", permissionsJson)
        return taskRolePolicyString to taskAssumeRoleString
    }
}
