package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.Resource
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.Application
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDeploymentServiceTest {

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var authService: AuthenticationService

    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService

    @Value("classpath:policyDocuments/taskRolePolicy.json")
    lateinit var taskRoleDocument: Resource
    @Value("classpath:policyDocuments/taskAssumeRolePolicy.json")
    lateinit var taskAssumeRoleDocument: Resource
    @Value("classpath:policyDocuments/jupyterBucketAccessPolicy.json")
    lateinit var bucketAccessPolicyDocument: Resource

    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator

    private val decodedJWT = mock<DecodedJWT>()

    @BeforeEach
    fun setup() {
        val jwtObject = JWTObject(decodedJWT, "test_user")
        whenever(authService.validate(any())).thenReturn(jwtObject)
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
    }

    @Test
    fun `Loads policy documents from classpath correctly`() {
        val taskRolePolicy = taskDeploymentService.taskRolePolicyDocument.inputStream.bufferedReader().use { it.readText() }
        assertThat(taskRolePolicy).isNotNull()

        val taskAssumeRoleDocument = taskDeploymentService.taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() }
        assertThat(taskAssumeRoleDocument).isNotNull()
    }

    @Test
    fun `Single set of additional attributes are replaced appropriately`() {
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(taskRoleDocument, mapOf("ADDITIONAL_PERMISSIONS" to listOf("permissionOne", "permissionTwo")))
        assertThat(taskRolePolicyString).doesNotContain("ADDITIONAL_PERMISSIONS")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    @Test
    fun `Multiple additional attributes are replaced appropriately`() {
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(bucketAccessPolicyDocument, mapOf("ACCESS_RESOURCES" to listOf("permissionOne", "permissionTwo"), "LIST_RESOURCE" to listOf("permissionThree", "permissionFour")))
        assertThat(taskRolePolicyString).doesNotContain("ACCESS_RESOURCES")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
        assertThat(taskRolePolicyString).doesNotContain("LIST_RESOURCE")
        assertThat(taskRolePolicyString).contains("\"permissionThree\",\"permissionFour\"")
    }

    @Test
    fun `No additional attributes returns resource to string`() {
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(taskAssumeRoleDocument, emptyMap())
        assertThat(taskRolePolicyString).isEqualTo(taskAssumeRoleDocument.inputStream.bufferedReader().use { it.readText() })
    }

    fun createDescribeRulesResponse(array: Collection<Rule>): DescribeRulesResponse {
        val list: Collection<Rule> = array
        val describeRulesResponse: DescribeRulesResponse = DescribeRulesResponse.builder().rules(list).build();
        return describeRulesResponse;
    }

    fun create1000(): Collection<Rule> {
        var oneThousandCol: Collection<Rule> = emptyList()
        var i = 0
        while (i <= 999) {
            oneThousandCol = oneThousandCol.plus(Rule.builder().priority(i.toString()).build())
            i++
        }
        return oneThousandCol
    }
}
