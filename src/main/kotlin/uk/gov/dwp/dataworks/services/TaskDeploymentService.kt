package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.*

@Service
class TaskDeploymentService {
    @Autowired
    lateinit var credentialsService: CredentialsService

    val awsRegion: Region = kotlin.runCatching { Region.of(System.getenv(ConfigKey.AWS_REGION.toString()))}.getOrDefault(Region.EU_WEST_2)

    fun createService (ecs_cluster_name: String, user_name: String, ecsClient: EcsClient) {

//        ecsClient.
//        val alb : LoadBalancer = LoadBalancer().toBuilder().build()
//        .loadBalancers().
        val serviceBuilder = CreateServiceRequest.builder().cluster(ecs_cluster_name).serviceName("${user_name}_test").taskDefinition("mhf_sample_task").desiredCount(1).build()

        println("Creating Service...")
        try {
            val service = ecsClient.createService(serviceBuilder)
            println("service.responseMetadata = ${service.responseMetadata()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun taskDefinitionWithOverride(ecs_cluster_name: String, emr_cluster_host_name: String , user_name: String, jupyterCpu : Int=512, jupyterMemory: Int = 512) {

        val credentials: AwsCredentialsProvider = credentialsService.getSessionCredentials()

        val ecsClient = EcsClient.builder().credentialsProvider(credentials).region(awsRegion).build()

        createService(emr_cluster_host_name, user_name, ecsClient)

        val clusterResponse = ecsClient.describeClusters(DescribeClustersRequest.builder().clusters(ecs_cluster_name).build());
   //     val ecsClient = EcsClient.builder().region(awsRegion).build()
        val clusterArn = clusterResponse.clusters()[0].clusterArn()
        println("clusterResponse = ${clusterResponse}")
        println("clusterArn = ${clusterArn}")


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
                .name("guacD")
                .environment(userName)
                .build()

        val overrides: TaskOverride = TaskOverride.builder()
                .containerOverrides(guacDOverride, chromeOverride, jupyterOverride)
                .build()

        val request: RunTaskRequest = RunTaskRequest.builder()
                .taskDefinition("mhf_sample_task") // USING FOR TESTING BEFORE TERRAFORM
                .cluster(ecs_cluster_name)
                .launchType("EC2")
                .overrides(overrides)
                .build()
            //    .taskDefinition("ui_service")
        println("Starting Task...")
        try {
            val response: RunTaskResponse = ecsClient.runTask(request)
            println("response.tasks = ${response.tasks()}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
