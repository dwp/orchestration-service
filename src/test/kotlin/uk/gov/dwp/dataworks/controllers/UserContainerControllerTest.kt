package uk.gov.dwp.dataworks.controllers

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Spy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import uk.gov.dwp.dataworks.model.JWTObject
import uk.gov.dwp.dataworks.services.*
import java.lang.Exception

@RunWith(SpringRunner::class)
@WebMvcTest(UserContainerController::class)
class UserContainerControllerTest {
    @Autowired
    private lateinit var mvc: MockMvc

    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var configService: ConfigurationService
    @MockBean
    private lateinit var taskDeploymentService: TaskDeploymentService
    @MockBean
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck

    private val decodedJWT = mock<DecodedJWT>()

    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(JWTObject(decodedJWT, "test_user"))
        doReturn(false).whenever(existingUserServiceCheck.check(anyString(), anyString()))
    }

    @Test
    fun `Endpoints return '405 not supported' for GET requests`() {
        mvc.perform(MockMvcRequestBuilders.get("/deployusercontainers"))
                .andExpect(MockMvcResultMatchers.status().isMethodNotAllowed)
    }

    @Test
    fun `200 returned with well formed request`() {
        mvc.perform(MockMvcRequestBuilders.post("/deployusercontainers")
                .content("{\"userName\" : \"test_user\"}")
                .header("content-type", "application/json"))
                .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test (expected = Exception::class)
    fun `Throws Exception with bad request`() {
        mvc.perform(MockMvcRequestBuilders.post("/deployusercontainers")
                .content("{\"missingUserName\" : \"test\"}")
                .header("content-type", "application/json"))
    }
    @Test (expected = Exception::class)
    fun `Throws Exception with empty request`() {
        mvc.perform(MockMvcRequestBuilders.post("/deployusercontainers")
                .content("{}")
                .header("content-type", "application/json"))
    }
}
