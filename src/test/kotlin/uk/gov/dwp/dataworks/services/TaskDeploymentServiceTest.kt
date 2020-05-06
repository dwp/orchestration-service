package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
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
import uk.gov.dwp.dataworks.MultipleLoadBalancersMatchedException
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
        val jwtObject = JWTObject(decodedJWT, "test_user", listOf("testGroup"))
        whenever(authService.validate(any())).thenReturn(jwtObject)
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        whenever(awsCommunicator.getAccNumber()).thenReturn("123456")
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
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(taskRoleDocument, mapOf("ecs-task-role-policy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
    }

    @Test
    fun `Multiple additional attributes are replaced appropriately`() {
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(bucketAccessPolicyDocument, mapOf("jupyter-s3-list" to listOf("permissionOne", "permissionTwo"), "jupyter-s3-access-document" to listOf("permissionThree", "permissionFour")), "Resource")
        assertThat(taskRolePolicyString).doesNotContain("[]")
        assertThat(taskRolePolicyString).contains("\"permissionOne\",\"permissionTwo\"")
        assertThat(taskRolePolicyString).contains("\"permissionThree\",\"permissionFour\"")
    }

    @Test
    fun `Wrong key attribute throws correct Exception`() {
        assertThatCode {  taskDeploymentService.parsePolicyDocument(bucketAccessPolicyDocument, mapOf("jupyter-s3-list" to listOf("permissionOne", "permissionTwo")), "") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Key does not match expected values: \"Resource\" or \"Action\"")
    }

    @Test
    fun `Returns proper case for JSON keys, as required by AWS`(){
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(taskRoleDocument, mapOf("ecs-task-role-policy" to listOf("permissionOne", "permissionTwo")), "Action")
        assertThat(taskRolePolicyString).contains("Statement").contains("Resource").contains("Effect").contains("Version").contains("Action")
    }

    @Test
    fun `Attributes are assigned to the correct key`(){
        val taskRolePolicyString = taskDeploymentService.parsePolicyDocument(bucketAccessPolicyDocument, mapOf("jupyter-s3-list" to listOf("permissionOne")), "Action")
        assertThat(taskRolePolicyString).contains("\"Action\":[\"s3:ListBucket\",\"permissionOne\"]")
    }

    @Test
    fun `List of arns is returned from setArn function with list passed in`() {
        val arnList = taskDeploymentService.setArns(listOf("group1", "group2"))
        assertThat(arnList[0]).isEqualTo("arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/group1-Shared")
        assertThat(arnList[1]).isEqualTo("arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/group2-Shared")
    }

    @Test
    fun `Empty list is returned from setArn function with no list`() {
        val arnList = taskDeploymentService.setArns(emptyList())
        assertThat(arnList).isEmpty()
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
