package uk.gov.dwp.dataworks.services

import com.auth0.jwt.interfaces.DecodedJWT
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse
import software.amazon.awssdk.services.ecs.model.Service

@RunWith(SpringRunner::class)
@WebMvcTest(ExistingUserServiceCheck::class)
class ExistingUserServiceCheckTest{
    @Autowired
    private lateinit var mvc: MockMvc
    @Autowired
    private lateinit var existingUserServiceCheckForTest: ExistingUserServiceCheck

    @MockBean
    private lateinit var ecsDescribeServicesCall: EcsDescribeServicesCall
    @MockBean
    private lateinit var authService: AuthenticationService
    @MockBean
    private lateinit var configService: ConfigurationService

    @BeforeEach
    fun setup() {
        whenever(authService.validate(any())).thenReturn(mock<DecodedJWT>())
    }

    private val testUserName = "testUser"
    private fun createTestService(name: String?, status: String?): Service {
        return Service.builder().serviceName(name).status(status).build()
    }

    private fun createDescribeServiceResponse(name: String, state: String): DescribeServicesResponse {
        return DescribeServicesResponse.builder().services(listOf(createTestService(name, state))).build()
    }

    @Test
    fun `Existing Service prevent duplicates from spinning up`(){
        whenever(ecsDescribeServicesCall.servicesResponse(any(), anyString(), anyString())).thenReturn(createDescribeServiceResponse( "$testUserName-ui-service", "ACTIVE"))
        Assert.assertEquals(true, existingUserServiceCheckForTest.check(testUserName, "test"))
    }

    @Test
    fun `Existing inactive service allows creation`(){
        whenever(ecsDescribeServicesCall.servicesResponse(any(), anyString(), anyString())).thenReturn(createDescribeServiceResponse( "$testUserName-ui-service", "INACTIVE"))
        Assert.assertEquals(false, existingUserServiceCheckForTest.check(testUserName, "test"))
    }

    @Test
    fun `No matching service allows creation`(){
        whenever(ecsDescribeServicesCall.servicesResponse(any(), anyString(), anyString())).thenReturn(DescribeServicesResponse.builder().build())
        Assert.assertEquals(false, existingUserServiceCheckForTest.check(testUserName, "test"))
    }
}
