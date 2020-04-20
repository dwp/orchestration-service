package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.exceptions.JWTVerificationException
import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.*
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import uk.gov.dwp.dataworks.model.JWTObject
import uk.gov.dwp.dataworks.services.*

@RunWith(SpringRunner::class)
@WebMvcTest(ConnectionController::class)
class ConnectionControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var configService: ConfigurationService
    @MockBean
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService

    private val decodedJWT = mock<DecodedJWT>()

    @BeforeEach
    fun setup() {
        val jwtObject = JWTObject(decodedJWT, "test_user")
        whenever(authService.validate(any())).thenReturn(jwtObject)
        doReturn(false).whenever(existingUserServiceCheck.check(any(), anyString()))
        doReturn("Test").whenever(configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME))
    }

    @Test
    fun `Endpoints return '405 not supported' for GET requests`() {
        mvc.perform(get("/connect"))
                .andExpect(status().isMethodNotAllowed)
        mvc.perform(get("/disconnect"))
                .andExpect(status().isMethodNotAllowed)
    }

    @Test
    fun `400 returned when no auth token included`() {
        mvc.perform(post("/connect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorization' for method parameter of type String"))

        mvc.perform(post("/disconnect"))
                .andExpect(status().isBadRequest)
                .andExpect(status().reason("Missing request header 'Authorization' for method parameter of type String"))
    }

    @Test
    fun `401 returned when bad token`() {
        whenever(authService.validate(any())).thenThrow(JWTVerificationException(""))
        mvc.perform(post("/connect")
                .content("{}")
                .header("content-type", "application/json")
                .header("Authorization", "testBadToken"))
                .andExpect(status().isUnauthorized)
        mvc.perform(post("/disconnect")
                .header("Authorization", "testBadToken"))
                .andExpect(status().isUnauthorized)
    }

    @Test
    fun `200 returned with well formed request`() {
        mvc.perform(post("/connect")
                .content("{}")
                .header("content-type", "application/json")
                .header("Authorization", "testGoodToken"))
                .andExpect(status().isOk)
        mvc.perform(post("/disconnect")
                .header("Authorization", "testGoodToken"))
                .andExpect(status().isOk)
    }
}
