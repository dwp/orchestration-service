package uk.gov.dwp.dataworks.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.nhaarman.mockitokotlin2.whenever
import io.swagger.v3.oas.integration.StringOpenApiConfigurationLoader.LOGGER
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.aws.AwsClients
import java.time.ZonedDateTime
import java.util.UUID


@RunWith(SpringRunner::class)
class AuthenticationServiceTest {
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var awsClients: AwsClients
    @MockBean
    private lateinit var configurationResolver: ConfigurationResolver
    @MockBean
    private lateinit var authService: AuthenticationService

    private val cognitoUserNameOnly= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoiY29nbml0b1VzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciJ9.atgHhs2UIhHq4pngA3q5yZSnTckSfan2LFixG85bnC1KJlZdacTTdJlYlowy63fRru7iyqJkRW1ALFJ8YownLpQn6NW4vLGrwz33PNIyxl0_r-DMQDlN1AENO-Hb46d8bu9S9x9Py6ujgVjhuoXC8_cgJFeMhXQUePhDOVa2nGfPQ85JUuCV4zu8XApDNITmWhfjFMBquJFYvIj51t2h8NlZyDsq3P2H0rjPxWDa3H21a5am_Mkh0qc5bCK8K41mzv77vv1ZPKtqWz1m5rfw65y4mtMDOHWpXczreJsnIaytWdPkgPOREPCVe8AaDHkFyKWyHEQ_-su3qQXmmnUorg"
    private val cognitoAndNoneCognito= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJjb2duaXRvOnVzZXJuYW1lIjoiY29nbml0b1VzZXJOYW1lIiwidXNlck5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.g2qv6-6c32j7Qggk5pZ7NEU6FyCaaOdZU92vXle4Ff3YAn1qyFVcd5oaaAuul-pkJFs7njnUpMlF7ijJlDqIWYwiOcbUI0G9oAwp482AYbZR67hkQXj7v6xUd1iRJ3SuCVh9KfJA799qxG0RxhtX6A9xlT87SEJwjg8AjKwgr3ttQdEz1ZF13xriIA1R2-TmmgOGfXrjUHQNCKDQGf-3gBLcZMtNB2NmX_-qS7FkWflzaWl8-zfdqdI00ztNQYzHLYDUv5grsKM9Bwdys_9SYq-N2ZuHA61pHlDPe_DyYiwgn5TPuqTMH_OSJcX_S0PJRAQMhakWPqPhvH992SXdnQ"
    private val noneCognitoOnly= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJ1c2VyTmFtZSI6InVzZXJOYW1lIiwiaXNzIjoiVGVzdElzc3VlciJ9.PS37nzlPzI-Yk-K4tlD9meXwOyG04gksmMIcw9KJ_waGyH0sXQidF_jdWBvwzmqKuMmY5pFEFbLCXj_V3I0IoYeUx4ccSQJTPGmg3sok_eeYZrAkxRWvLyTF4BAP3IcWwK8-DD9Ren7uB14kZcKEvDY73ieS85vZUdSTCiG32aibYxzULeV1c31KkZ7uvMo4yOp8q3QZ9x-5gnI4ALsGE4r0thelyRZyka6_JytvQCKHt8GLvEOCWCV3xJ4o_g2bbp7anaY4M6dBnM8P2wuYw_XBXLQTvgBHiZz7zLrVWjhYwZUVVC3f_wWAtMUclfot6qrzTt7rjofwxKAfH4H1JQ"

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
    }

    @Test
    fun `Returns cognito username when present`() {
        val decodedJWT = JWT.decode(cognitoUserNameOnly)
        val cognitoUserName = authService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns cognito username when cognito and none cognito present`() {
        val decodedJWT = JWT.decode(cognitoAndNoneCognito)
        val cognitoUserName = authService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns non-cognito username when cognito username isnt present`() {
        val decodedJWT = JWT.decode(noneCognitoOnly)
        val cognitoUserName = authService.cognitoUsernameFromJwt(decodedJWT)
        Assertions.assertThat(cognitoUserName).isEqualTo("userName")
    }
}