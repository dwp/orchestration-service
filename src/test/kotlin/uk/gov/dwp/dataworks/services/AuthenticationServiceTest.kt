package uk.gov.dwp.dataworks.services

import com.auth0.jwt.JWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.InjectMocks
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.aws.AwsClients
import java.lang.IllegalArgumentException

@RunWith(SpringRunner::class)
class AuthenticationServiceTest {
    @InjectMocks
    private lateinit var authenticationService: AuthenticationService

    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var awsClients: AwsClients
    @MockBean
    private lateinit var configurationResolver: ConfigurationResolver

    private val cognitoUserNameOnly= "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJrbXNhcm4iOiJUZXN0S21zQXJuIiwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.WL_iERpn47Fe4_oLyF944chhCZt2QtIe_40K8J4CxkpEbVE4ooqHCjwHmIWtycho4GZYgVYOlR58aDcTSpegZld_qKvHHkHux0pSemO6Attqs_pIqJ-GPwUeVvRj1za8bouNtV-rZnyDmW0FPXX-7jnTBExki1UCsS3zF--1WwAN5_gINdQ45bePQOOxAvJG2ZsOz9kEUHLPhOWq-Hq84FV140rBgqhtrxo8r9eoBOuNUW9vXsAiNf73i2ow8-nmoutbdoXlCkJaJ5uw19H7eP8RRKh6ExbIQrRT6zL1pHXeFalTTf1Jlq6pA9s3sVisqXFCOsUmZEPhBgEZe-VBeQ"
    private val cognitoAndNoneCognito= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJrbXNhcm4iOiJUZXN0S21zQXJuIiwiY29nbml0bzp1c2VybmFtZSI6ImNvZ25pdG9Vc2VyTmFtZSIsInVzZXJuYW1lIjoidXNlck5hbWUiLCJpc3MiOiJUZXN0SXNzdWVyIn0.dBLoFeB3EpsdVX0zlDY2GSR_FHIlEMc37bwjTSEFMTx0Xi97pj3VszM_x48BG7GiQosXy-l994-vvTqhYn--p2Qv7EsdtG_mtDY-n1jqJUSuBXZpTVVALUSJYLj7hIDC-CcO63QjqejPjbF-14QmNXXeUZ5FpbEPtjjcnCaq--_Mi-LABG17z6djs-y5KWwXlAfbmKV8E0bkKkEccVdhKb7dCP1uaFH6NpNfPGuycmNg-PKdVmFnTan8sUSuzIRq7pBIPqyMrmwEoxwl-VN0aAEzBXKNrXsQ-FlVE9VB6z1kbtxw2dpn8g-2LAwzP5ohaRUgza8vz-FxywiXpcDCQA"
    private val noneCognitoOnly= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJrbXNhcm4iOiJUZXN0S21zQXJuIiwidXNlcm5hbWUiOiJ1c2VyTmFtZSIsImlzcyI6IlRlc3RJc3N1ZXIifQ.PEqiHlFCuNm7tWwLV7H6iuqZhPoi7tYaHdhOfT9dQ5JWd924ey-LXRa5RcJhjur-2s_HSP_S6eypu9taNmNR_fEcGK2eWgtkRrNpadXs1SKuOJ8740AaYjK8NNfokOfltpcZECjnD9E4bMBnoaFrYvg5lt96aUE1NbI0kBEU2WcNi5jRWxDoKvB9tMe8M8LfdWDX7YfEVzGallp3F__ksYZAKao9lpvWMrXdsqYiVKxwEzzJMES6oVQLsLewz5uVB-O-M6oRF1LPDSCUqPhxWKYcFZgvi2lsuZre4bK_1ZyX2xktRot0QagHZCSJ1ETuF_TxBgYxFCO5zsf5tzGhPA"
    private val noUserName= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJrbXNhcm4iOiJUZXN0S21zQXJuIiwibm9Vc2VyTmFtZSI6Im5vTmFtZSJ9.MXM1xJQRNnmIAQMWR-aPabCRMDzBCzs9T2A5pANpR_Hrg0-fmTcxvqP2uLrBS2Z8Y1VP4r6udzKYZBAfpEVOnGEX83VkbUpbT6wmPz3KaUPgxUwv2hc4zmlztdq0sSTT3MbBgH6EgA7iG2_Ctoimcc1lr6RJI-XUyf_w-SjuLK7WBPePy20W5dcT7bQjpo7zRFrAkewg1EvLAsxvdl6rW8VNrqe9rKhZw9WnTnaCzVCPrWMp3YGf8U8az2aSw1z5eN9ixqFsmGRl2bsKZS8nhjJzvGHG6RrKLI2m6m0myW4RvzHtKV7LnDImtPpYZaJX4uT8SFZ8Eo_O7pGi0OGHNg"
    private val noKmsArn= "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJub2ttc2FybiI6IlRlc3RLbXNBcm4iLCJ1c2VybmFtZSI6IlRlc3RVc2VyTmFtZSJ9.TZ0aEC_ZJIAXV9sxK0pWaU-v0xoKfw6VheMuQPnJQkd3va_qLjmSC7tWTkwrhYcSUcZ0JPPy4O34IONY6QSva9GsmupvJ-ppUx_D9qBT-AU9lI1lABZuFdPipHBmzaeve6SlSY-D4NdyZ0thaZEmh9t2YRp6ob11NtAYfHkqDbU9r_W5p3TvA5f0t6PLfqCrkZTozpz2exhR-iVdM4PbQxUc-WwINRBnuhd2O2y-5eHGIRTzYYtpP1516iIPtwSyT_ocaZE4EYPTbIQ7oLCnnCjfc1mAUAWZjuEwNe9cIErEjr_bUmT1mGwcxhAi1StMODmzmXK9xgoJ-jNyJHlRJw"

    @BeforeEach
    fun setup() {
        whenever(configurationResolver.awsRegion).thenReturn(Region.EU_WEST_2)
        whenever(configurationResolver.getStringConfig(any())).thenReturn("")
    }

    @Test
    fun `Returns cognito username when present`() {
        val decodedJWT = JWT.decode(cognitoUserNameOnly)
        val cognitoUserName = authenticationService.valuesFromJwt(decodedJWT).getValue("userName")
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns cognito username when cognito and none cognito present`() {
        val decodedJWT = JWT.decode(cognitoAndNoneCognito)
        val cognitoUserName = authenticationService.valuesFromJwt(decodedJWT).getValue("userName")
        Assertions.assertThat(cognitoUserName).isEqualTo("cognitoUserName")
    }
    @Test
    fun `Returns non-cognito username when cognito username isnt present`() {
        val decodedJWT = JWT.decode(noneCognitoOnly)
        val cognitoUserName = authenticationService.valuesFromJwt(decodedJWT).getValue("userName")
        Assertions.assertThat(cognitoUserName).isEqualTo("userName")
    }

    @Test
    fun `Throws correct error, when no user name present`() {
        val decodedJWT = JWT.decode(noUserName)
        Assertions.assertThatCode { authenticationService.valuesFromJwt(decodedJWT).getValue("userName") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No username found in JWT token")
    }

    @Test
    fun `Throws correct error, when no KMS arn present`() {
        val decodedJWT = JWT.decode(noKmsArn)
        Assertions.assertThatCode { authenticationService.valuesFromJwt(decodedJWT).getValue("kmsArn") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("No kmsarn found in JWT token")
    }
}
