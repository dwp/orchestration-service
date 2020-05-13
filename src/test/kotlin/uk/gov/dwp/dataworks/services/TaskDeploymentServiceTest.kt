package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@SpringBootTest
@RunWith(SpringRunner::class)
class TaskDeploymentServiceTest {
//    @Autowired
    @InjectMocks
    private lateinit var taskDeploymentService: TaskDeploymentService

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var awsCommunicator: AwsCommunicator
    @MockBean
    private lateinit var configurationResolver: ConfigurationResolver

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        doReturn("testArn").whenever(configurationResolver).getStringConfig(ConfigKey.JUPYTER_S3_ARN)
        doReturn("1234").whenever(configurationResolver).getStringConfig(ConfigKey.AWS_ACCOUNT_NUMBER)
        doReturn("test").whenever(configurationResolver).getStringConfig(any())
    }

    @Test
    fun `Can work through debug endpoint without cognitoGroups`(){
        val emptyCognitoGroup = taskDeploymentService.parseMap(emptyList(), "testUser")
        assertThat(emptyCognitoGroup)
                .isEqualTo(mapOf(Pair("jupyter-s3-access-document", listOf("testArn/*")), Pair("jupyter-s3-list", listOf("testArn"))))
    }
}
