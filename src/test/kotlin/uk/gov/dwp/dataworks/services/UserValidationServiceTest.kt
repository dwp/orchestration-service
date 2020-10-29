package uk.gov.dwp.dataworks.services

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.InjectMocks
import org.mockito.Mock
import org.springframework.test.context.junit4.SpringRunner
import uk.gov.dwp.dataworks.JWTObject
import uk.gov.dwp.dataworks.aws.AwsCommunicator

@RunWith(SpringRunner::class)
class UserValidationServiceTest {
    @InjectMocks
    private lateinit var userValidationService: UserValidationService

    @Mock
    private lateinit var jwtParsingService: JwtParsingService
    @Mock
    private lateinit var jwtObject: JWTObject
    @Mock
    private lateinit var awsCommunicator: AwsCommunicator


    @Before
    fun setup() {
        whenever(jwtParsingService.parseToken(any())).doReturn(jwtObject)
        whenever(jwtObject.username).doReturn("testuser123")
    }

    @Test
    fun `returns false when parsing service doesn't find enabled kms and finds group`() {
        whenever(awsCommunicator.checkForExistingEnabledKey(anyString())).doReturn(false)
        whenever(jwtObject.cognitoGroups).doReturn(listOf("group1", "testuser123"))
        assertFalse(userValidationService.checkAttributes("fake_token"))
    }

    @Test
    fun `returns false when parsing service doesn't find group and finds enabled kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey(anyString())).doReturn(true)
        whenever(jwtObject.cognitoGroups).doReturn(listOf("group1", "group2"))
        assertFalse(userValidationService.checkAttributes("fake_token"))
    }

    @Test
    fun `returns false when parsing service doesn't find group or enabled kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey(anyString())).doReturn(false)
        whenever(jwtObject.cognitoGroups).doReturn(listOf("group1", "group2"))
        assertFalse(userValidationService.checkAttributes("fake_token"))
    }

    @Test
    fun `returns true when parsing service finds group and enabled kms`() {
        whenever(awsCommunicator.checkForExistingEnabledKey(anyString())).doReturn(true)
        whenever(jwtObject.cognitoGroups).doReturn(listOf("testuser123", "group2"))
        assertTrue(userValidationService.checkAttributes("fake_token"))
    }

}
