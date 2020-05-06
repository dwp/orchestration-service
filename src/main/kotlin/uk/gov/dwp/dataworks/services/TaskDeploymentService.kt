package uk.gov.dwp.dataworks.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.model.ContainerDefinition
import software.amazon.awssdk.services.ecs.model.ContainerOverride
import software.amazon.awssdk.services.ecs.model.KeyValuePair
import software.amazon.awssdk.services.ecs.model.LoadBalancer
import uk.gov.dwp.dataworks.JsonObject
import software.amazon.awssdk.services.ecs.model.NetworkMode
import software.amazon.awssdk.services.ecs.model.PortMapping
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetTypeEnum
import uk.gov.dwp.dataworks.UserTask
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.io.File
import java.lang.reflect.Modifier
import java.util.UUID

@Service
class TaskDeploymentService {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Value("classpath:policyDocuments/jupyterBucketAccesssPolicy.json")
    lateinit var jupyterBucketAccessDocument: Resource
    private lateinit var jupyterBucketAccessRolePolicyString: String

    @Autowired
    private lateinit var authService: AuthenticationService

    @Value("classpath:policyDocuments/taskAssumeRolePolicy.json")
    lateinit var taskAssumeRoleDocument: Resource
    private lateinit var taskAssumeRoleString: String

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRolePolicyDocument: Resource
    private lateinit var taskRolePolicyString: String

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(TaskDeploymentService::class.java))
    }

    fun runContainers(userName: String, jupyterCpu: Int, jupyterMemory: Int, additionalPermissions: List<String>) {
        val correlationId = "$userName-${UUID.randomUUID()}"
        // Retrieve required params from environment
        val containerPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.USER_CONTAINER_PORT))
        val taskExecutionRoleArn = configurationResolver.getStringConfig(ConfigKey.USER_TASK_EXECUTION_ROLE_ARN)
        val taskRoleArn = configurationResolver.getStringConfig(ConfigKey.USER_TASK_ROLE_ARN)
        val taskSubnets = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SUBNETS)
        val taskSecurityGroups = configurationResolver.getListConfig(ConfigKey.USER_TASK_VPC_SECURITY_GROUPS)
        val albPort = Integer.parseInt(configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_PORT))
        val albName = configurationResolver.getStringConfig(ConfigKey.LOAD_BALANCER_NAME)
        val ecsClusterName = configurationResolver.getStringConfig(ConfigKey.ECS_CLUSTER_NAME)
        val emrClusterHostname = configurationResolver.getStringConfig(ConfigKey.EMR_CLUSTER_HOSTNAME)

        //Create an entry in DynamoDB for current deployment
        activeUserTasks.initialiseDeploymentEntry(correlationId, userName)

        // IAM permissions
        val accessPair = Pair("jupyter-s3-access-document",
                listOf(
                    "${configurationResolver.getStringConfig(ConfigKey.JUPYTER_S3_ARN)}/*",
                    "arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/${userName}-Home")
                    .plus(setArns(cognitoGroups)
                )
        )
        val listPair = Pair("jupyter-s3-list", listOf(configurationResolver.getStringConfig(ConfigKey.JUPYTER_S3_ARN)))
        val jupyterMap = mapOf(accessPair, listPair)
        jupyterBucketAccessRolePolicyString = parsePolicyDocument(jupyterBucketAccessDocument, jupyterMap, "Resources")
        taskRolePolicyString = parsePolicyDocument(taskRolePolicyDocument, mapOf("ecs-task-role-policy" to additionalPermissions), "Actions")
        taskAssumeRoleString = File(taskAssumeRoleDocument.uri).toString()
        val jupyterIamPolicy = awsCommunicator.createIamPolicy(correlationId, "$userName-jupyter-s3-document", jupyterBucketAccessRolePolicyString)
        val iamPolicy = awsCommunicator.createIamPolicy(correlationId, "$userName-task-role-document", taskRolePolicyString)
        val iamRole = awsCommunicator.createIamRole(correlationId, "$userName-iam-role", taskAssumeRoleString)
        awsCommunicator.attachIamPolicyToRole(correlationId, iamPolicy, iamRole)
        awsCommunicator.attachIamPolicyToRole(correlationId, jupyterIamPolicy, iamRole)
        try {
            // Load balancer & Routing
            val loadBalancer = awsCommunicator.getLoadBalancerByName(albName)
            val listener = awsCommunicator.getAlbListenerByPort(loadBalancer.loadBalancerArn(), albPort)
            val targetGroup = awsCommunicator.createTargetGroup(correlationId, userName, loadBalancer.vpcId(), containerPort, TargetTypeEnum.IP)

            // There are 2 distinct LoadBalancer classes in the AWS SDK - ELBV2 and ECS. They represent the same LB but in different ways.
            // The following is the load balancer needed to create an ECS service.
            val ecsLoadBalancer = LoadBalancer.builder()
                    .targetGroupArn(targetGroup.targetGroupArn())
                    .containerName("guacamole")
                    .containerPort(containerPort)
                    .build()
            awsCommunicator.createAlbRoutingRule(correlationId, userName, listener.listenerArn(), targetGroup.targetGroupArn())

            // IAM permissions
            parsePolicyDocuments(additionalPermissions)
            val iamPolicy = awsCommunicator.createIamPolicy(correlationId, userName, taskRolePolicyString)
            val iamRole = awsCommunicator.createIamRole(correlationId, userName, taskAssumeRoleString)
            awsCommunicator.attachIamPolicyToRole(correlationId, iamPolicy, iamRole)

            val containerDefinitions = buildContainerDefinitions(userName, emrClusterHostname, jupyterMemory, jupyterCpu, containerPort)
            val taskDefinition = awsCommunicator.registerTaskDefinition(correlationId,"orchestration-service-user-$userName-td", taskExecutionRoleArn , taskRoleArn, NetworkMode.AWSVPC, containerDefinitions)

            // ECS
            awsCommunicator.createEcsService(correlationId, userName, ecsClusterName, taskDefinition, ecsLoadBalancer, taskSubnets, taskSecurityGroups)
        } catch (e: Exception) {
            logger.error("Failed to create resources for user", e, "correlation_id" to correlationId, "user_name" to userName)
            // Pause to allow eventual consistency
            Thread.sleep(5000)
            taskDestroyService.destroyServices(userName)
            throw e
        }
    }

    private fun buildContainerDefinitions(userName: String, emrHostname: String, jupyterMemory: Int, jupyterCpu: Int, guacamolePort: Int): Collection<ContainerDefinition> {
        val ecrEndpoint = configurationResolver.getStringConfig(ConfigKey.ECR_ENDPOINT)
        val screenSize = 1920 to 1080

        val jupyterHub = ContainerDefinition.builder()
                .name("jupyterHub")
                .image("$ecrEndpoint/aws-analytical-env/jupyterhub")
                .cpu(jupyterCpu)
                .memory(jupyterMemory)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(8000).hostPort(8000).build())
                .environment(pairsToKeyValuePairs("USER" to userName, "EMR_HOST_NAME" to emrHostname))
                .build()

        val headlessChrome = ContainerDefinition.builder()
                .name("headless_chrome")
                .image("$ecrEndpoint/aws-analytical-env/headless-chrome")
                .cpu(256)
                .memory(256)
                .essential(true)
                .portMappings(PortMapping.builder().containerPort(5900).hostPort(5900).build())
                .environment(pairsToKeyValuePairs(
                        "VNC_OPTS" to "-rfbport 5900 -xkb -noxrecord -noxfixes -noxdamage -display :1 -nopw -wait 5 -shared -permitfiletransfer -tightfilexfer -noclipboard -nosetclipboard",
                        "CHROME_OPTS" to arrayOf(
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
                                "--test-type https://localhost:8000",
                                "--kiosk",
                                "--window-size=${screenSize.toList().joinToString(",")}").joinToString(" "),
                        "VNC_SCREEN_SIZE" to screenSize.toList().joinToString("x")))
                .build()

        val guacd = ContainerDefinition.builder()
                .name("guacd")
                .image("$ecrEndpoint/aws-analytical-env/guacd")
                .cpu(128)
                .memory(128)
                .essential(true)
                .portMappings(PortMapping.builder().hostPort(4822).containerPort(4822).build())
                .build()

        val guacamole = ContainerDefinition.builder()
                .name("guacamole")
                .image("$ecrEndpoint/aws-analytical-env/guacamole")
                .cpu(256)
                .memory(256)
                .essential(true)
                .environment(pairsToKeyValuePairs(
                        "GUACD_HOSTNAME" to "localhost",
                        "GUACD_PORT" to "4822",
                        "KEYSTORE_DATA" to authService.getB64KeyStoreData(),
                        "VALIDATE_ISSUER" to "true",
                        "ISSUER" to authService.issuerUrl,
                        "CLIENT_PARAMS" to "hostname=localhost,port=5900,disable-copy=true",
                        "CLIENT_USERNAME" to userName))
                .portMappings(PortMapping.builder().hostPort(guacamolePort).containerPort(guacamolePort).build())
                .build()

        return listOf(jupyterHub, headlessChrome, guacd, guacamole)
    }

    private fun pairsToKeyValuePairs(vararg pairs: Pair<String, String>): Collection<KeyValuePair> {
        return pairs.map { KeyValuePair.builder().name(it.first).value(it.second).build() }
    }

    /**
     * Helper method to initialise the lateinit vars [taskAssumeRoleString] and [taskRolePolicyString] by
     * converting the associated `@Value` parameters to Strings and replacing `ADDITIONAL_PERMISSIONS` in
     * [taskRolePolicyString] with the provided [listOfParameters]
     *
     * @return [taskRolePolicyString] and [jupyterBucketAccessRolePolicyString].
     */
    fun parsePolicyDocument(resource: Resource, sidAndAdditions: Map<String, List<String>>, key: String): String{
        val mapper = ObjectMapper()
                .setPropertyNamingStrategy(CustomPropertyNamingStrategy())
        val obj = mapper.readValue(resource.url, JsonObject::class.java)
        obj.Statement.forEach {statement ->
            sidAndAdditions.forEach {
                if(it.key == statement.Sid) {
                    if (key == "Resource") statement.Resource = statement.Resource.plus(it.value)
                    else if (key == "Action") statement.Action = statement.Action.plus(it.value)
                    else throw IllegalArgumentException("Key does not match expected values: \"Resource\" or \"Action\"")
                }
            }
        }
        return mapper .writeValueAsString(obj)
        taskAssumeRoleString = taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        taskRolePolicyString = taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
                .replace("ADDITIONAL_PERMISSIONS", replaceString)
        return taskRolePolicyString to taskAssumeRoleString
    }

    fun setArns(groups: List<String>): List<String>{
        var list: List<String> = emptyList()
        groups.forEach{
            list = list.plus("arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/${it}-Shared")
        }
        return list
    }
}


class CustomPropertyNamingStrategy : PropertyNamingStrategy() {
    override fun nameForField(config: MapperConfig<*>?, field: AnnotatedField, defaultName: String): String {
        return convertForField(defaultName)
    }

    override fun nameForGetterMethod(config: MapperConfig<*>?, method: AnnotatedMethod, defaultName: String): String {
        return convertForMethod(method, defaultName)
    }

    override fun nameForSetterMethod(config: MapperConfig<*>?, method: AnnotatedMethod, defaultName: String): String {
        return convertForMethod(method, defaultName)
    }

    private fun convertForField(defaultName: String): String {
        return defaultName
    }

    private fun convertForMethod(method: AnnotatedMethod, defaultName: String): String {
        if (isGetter(method)) {
            return method.name.substring(3)
        }
        return if (isSetter(method)) {
            method.name.substring(3)
        } else defaultName
    }

    private fun isGetter(method: AnnotatedMethod): Boolean {
        if (Modifier.isPublic(method.modifiers) && method.genericParameterTypes.size == 0) {
            if (method.name.matches(Regex("^get[A-Z].*")) && method.rawReturnType != Void.TYPE) return true
            if (method.name.matches(Regex("^is[A-Z].*")) && method.rawReturnType != Boolean::class.javaPrimitiveType) return true
        }
        return false
    }

    private fun isSetter(method: AnnotatedMethod): Boolean {
        return Modifier.isPublic(method.modifiers) && method.rawReturnType == Void.TYPE && method.genericParameterTypes.size == 1 && method.name.matches(Regex("^set[A-Z].*"))
    }
}
