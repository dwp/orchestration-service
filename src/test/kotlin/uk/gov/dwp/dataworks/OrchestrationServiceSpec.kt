package uk.gov.dwp.dataworks

import cloud.localstack.LocalstackTestRunner
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.dynamodb.model.*
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.kms.KmsClient
import uk.gov.dwp.dataworks.aws.AwsClients
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.aws.AwsParsing
import uk.gov.dwp.dataworks.controllers.ConnectionController
import uk.gov.dwp.dataworks.services.ActiveUserTasks
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.TaskDestroyService
import java.net.URI

@RunWith(SpringRunner::class)
@WebMvcTest(AwsCommunicator::class, ConfigurationResolver::class)
class OrchestrationServiceSpec {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @Autowired
    private lateinit var activeUserTasks: ActiveUserTasks
    @Autowired
    private lateinit var taskDestroyService: TaskDestroyService
    @Autowired
    private lateinit var connectionController: ConnectionController
    @Autowired
    private lateinit var localStackClients: LocalStackClients
    @Autowired
    private lateinit var awsParsing: AwsParsing
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    @MockBean
    private lateinit var awsClients: AwsClients

    private val localIamClient = localStackClients.localIamClient()
    private val localKmsClient = localStackClients.localKmsClient()
    private val localDynamoClient = localStackClients.localDynamoDbClient()

    fun createTable() {
        val tableRequest = CreateTableRequest.builder()
                .tableName(ActiveUserTasks.dynamoTableName)
                .keySchema(KeySchemaElement.builder().attributeName("username").keyType("String").build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("username").attributeType(ScalarAttributeType.S).build())
                .attributeDefinitions(AttributeDefinition.builder().attributeName("year").attributeType(ScalarAttributeType.N).build())
                .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(10).writeCapacityUnits(10).build())
                .build()
        val table = localDynamoClient.createTable(tableRequest)
    }

    fun putEntryToTable(){
        try {
            localDynamoClient.updateItem(UpdateItemRequest.builder()
                    .tableName(ActiveUserTasks.dynamoTableName)
                    .key(mapOf(ActiveUserTasks.dynamoPrimaryKey to AttributeValue.builder().s(username).build()))
                    .updateExpression(updateExpression)
                    .expressionAttributeValues(mapOf(":value" to AttributeValue.builder().s(attribute.second).build()))
                    .build())
        } catch (e: Exception) {
            throw Exception("Error putting test data to localDynamoDb" + e.printStackTrace())
        }
    }

    @Before
    fun setup() {
        createTable()
        whenever(awsClients.kmsClient).thenReturn(localKmsClient)
        whenever(awsClients.dynamoDbClient).thenReturn(localDynamoClient)
        whenever(awsClients.iamClient).thenReturn(localIamClient)
    }

    @Test
    fun `whatever I write here`(){
        try {

        } catch (e: Exception) {
            throw Exception("Error putting test data to localDynamoDb" + e.printStackTrace())
        }
    }
}


@RunWith(LocalstackTestRunner::class)
@LocalstackDockerProperties(services=["dynamodb", "kms", "iam"], ignoreDockerRunErrors = true)
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

