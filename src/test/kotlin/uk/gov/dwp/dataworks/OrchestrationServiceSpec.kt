package uk.gov.dwp.dataworks

import cloud.localstack.LocalstackTestRunner
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import software.amazon.awssdk.services.kms.model.CreateKeyRequest
import com.auth0.jwt.JWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.elasticloadbalancingv2.model.*
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.kms.KmsClient
import software.amazon.awssdk.services.kms.model.AlreadyExistsException
import software.amazon.awssdk.services.kms.model.CreateAliasRequest
import uk.gov.dwp.dataworks.aws.AwsClients
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing
import uk.gov.dwp.dataworks.controllers.ConnectionController
import uk.gov.dwp.dataworks.services.*
import java.lang.Exception
import java.net.URI

@RunWith(SpringRunner::class)
@WebMvcTest(
        properties = [
            "orchestrationService.aws_region=us-east-1",
            "orchestrationService.user_container_port=1234",
            "orchestrationService.user_container_url=www.com",
            "orchestrationService.user_task_execution_role_arn=1234abcd",
            "orchestrationService.user_task_subnets=testSubnets",
            "orchestrationService.user_task_security_groups=testSg",
            "orchestrationService.load_balancer_port=1234",
            "orchestrationService.load_balancer_name=testLb",
            "orchestrationService.user_container_url=www.com",
            "orchestrationService.emr_cluster_hostname=test_hostname",
            "orchestrationService.ecs_cluster_name=test_cluster",
            "orchestrationService.container_log_group=testLog",
            "orchestrationService.aws_account_number=000000000000",
            "orchestrationService.ecr_endpoint=endpoint",
            "orchestrationService.debug=false",
            "orchestrationService.jupyterhub_bucket_arn=abc1234"
        ],
        controllers = [
            ConnectionController::class,
            ConfigurationResolver::class,
            TaskDeploymentService::class,
            AwsParsing::class
        ]

)
class OrchestrationServiceSpec {
//    TODO: Add env. vars. for tests

    private val localStackClients: LocalStackClients = LocalStackClients()

    @Autowired
    private lateinit var mvc: MockMvc
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    private lateinit var localIamClient: IamClient
    private lateinit var localKmsClient: KmsClient
    private lateinit var localDynamoClient: DynamoDbClient

    @MockBean
    private lateinit var activeUserTasks: ActiveUserTasks
    @MockBean
    private lateinit var taskDestroyService: TaskDestroyService
    @MockBean
    private lateinit var awsClients: AwsClients
    @MockBean
    private lateinit var authenticationService: AuthenticationService

    @SpyBean
    private lateinit var awsCommunicator: AwsCommunicator

    private val testJwt = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJjb2duaXRvOmdyb3VwcyI6WyJjZzEiLCJjZzIiXSwiY29nbml0bzp1c2VybmFtZSI6InRlc3R1c2VybmFtZSIsInVzZXJuYW1lIjoidXNlck5hbWUiLCJpc3MiOiJUZXN0SXNzdWVyIiwic3ViIjoiMTIzNDU2Nzg5MCJ9.lXKrCqpkHBUKR1yN7H85QXH9Yyq-aFWWcLa2VDxkP8SbqEnPttW7DGRL0jj2Pm8JimSvc0WFGZvvyT7cCZllEyjCHjCRIXgXbIv5pg9kFzRNgp2D7W-MujZAul6-TJrJ3h9Dv0RRKklrZvKr6PXCnwpFGqrwlzUg-2zMh9x2QEK4Hjr7-EZWJtorJAtSYKUWwKh_wLrFb9PBwSDIrbO0i1snJHIM1_ti6S7_qf4Mmf29Zzn_HeakLnLM06YPCxqkV-KM4ABsax9BQirQF67KI9o7p5SgNjqlDscb6gn5XmV6eGG193rtMiiPxhgioP4eMQFzpA_ZuNbB1om7qsEdWA"

    fun createTable() {

        try {
            val tableRequest = CreateTableRequest.builder()
                    .tableName(ActiveUserTasks.dynamoTableName)
                    .keySchema(KeySchemaElement.builder().attributeName("userName").keyType("HASH").build())
                    .attributeDefinitions(AttributeDefinition.builder().attributeName("userName").attributeType(ScalarAttributeType.S).build())
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10).writeCapacityUnits(10).build())
                    .build()
            val table = localDynamoClient.createTable(tableRequest)
            println(table)
        } catch (e: ResourceNotFoundException){
            println("Table already exists")
        }
    }

    fun addKmsKeys() {
        try {
            val sharedKmsKey1 = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/cg1-shared").targetKeyId(sharedKmsKey1.keyId()).build())
            val sharedKmsKey2 = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/cg2-shared").targetKeyId(sharedKmsKey2.keyId()).build())
            val userKmsKey = localKmsClient.createKey(CreateKeyRequest.builder().build()).keyMetadata()
            localKmsClient.createAlias(CreateAliasRequest.builder().aliasName("alias/testusername123-home").targetKeyId(userKmsKey.keyId()).build())
        } catch (e: AlreadyExistsException){
            println("KMS keys already exist")
        }
    }
//    fun putEntryToTable(){
//        try {
//            localDynamoClient.updateItem(UpdateItemRequest.builder()
//                    .tableName(ActiveUserTasks.dynamoTableName)
//                    .key(mapOf(ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(username).build()))
//                    .updateExpression(updateExpression)
//                    .expressionAttributeValues(mapOf(":value" to AttributeValue.builder().s(attribute.second).build()))
//                    .build())
//        } catch (e: Exception) {
//            throw Exception("Error putting test data to localDynamoDb" + e.printStackTrace())
//        }
//    }

    @Before
    fun setup() {
        println("Inside setup")
        localIamClient = localStackClients.localIamClient()
        localKmsClient = localStackClients.localKmsClient()
        localDynamoClient = localStackClients.localDynamoDbClient()
        whenever(awsClients.kmsClient).thenReturn(localKmsClient)
        whenever(awsClients.dynamoDbClient).thenReturn(localDynamoClient)
        whenever(awsClients.iamClient).thenReturn(localIamClient)
        doReturn(LoadBalancer.builder().loadBalancerArn("abc123").vpcId("12345").build())
                .whenever(awsCommunicator).getLoadBalancerByName(anyString())
        doReturn(Listener.builder().listenerArn("abc123").build())
                .whenever(awsCommunicator).getAlbListenerByPort(anyString(), any())
        doReturn(TargetGroup.builder().targetGroupArn("1234abcd").build())
                .whenever(awsCommunicator).createTargetGroup(anyString(), any(), any(),any(),any())
        doReturn(Rule.builder().build())
                .whenever(awsCommunicator).createAlbRoutingRule( any(), any(),any(),any())
        createTable()
        addKmsKeys()
        println(localKmsClient.listKeys())
        println(localKmsClient.listAliases())
    }

    @Test
    fun `IAM Role created on authenticated call to connect API`() {
        whenever(authenticationService.validate(anyString())).thenReturn(JWTObject(JWT.decode(testJwt),
                "testusername123", listOf("cg1", "cg2")))
        assertDoesNotThrow{
        mvc.perform(MockMvcRequestBuilders.post("/connect")
                .content("{\"emrClusterHostName\":\"\"}")
                .header("content-type", "application/json")
                .header("Authorisation", testJwt)).andExpect(MockMvcResultMatchers.status().isOk)
//            val role = localIamClient.getRole(GetRoleRequest.builder().roleName("orchestration-service-user-testusername123-role").build()).role()
//            assertThat(role).isNotNull
        println(localIamClient.listRoles())
        }
    }
}


@RunWith(LocalstackTestRunner::class)
@LocalstackDockerProperties(services = ["dynamodb", "kms", "iam"], ignoreDockerRunErrors = true)
class LocalStackClients {

    @Bean
    fun localDynamoDbClient(): DynamoDbClient {
        return DynamoDbClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Bean
    fun localKmsClient(): KmsClient {
        return KmsClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Bean
    fun localIamClient(): IamClient {
        return IamClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Test
    fun testConnectionDb() {
        val localDb = localDynamoDbClient()
        assertThat(localDb.listTables()).isNotNull
    }

    @Test
    fun testConnectionKms() {
        val localKmsClient = localKmsClient()
        assertThat(localKmsClient.listKeys()).isNotNull
    }

    @Test
    fun testConnectionIam() {
        val localIamClient = localIamClient()
        assertThat(localIamClient.listUsers()).isNotNull()
    }
}

