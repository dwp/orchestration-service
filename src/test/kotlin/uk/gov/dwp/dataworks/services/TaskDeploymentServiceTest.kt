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
