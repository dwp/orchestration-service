package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger

@Service
class UserValidationService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(UserValidationService::class.java))
    }

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator
    @Autowired
    private lateinit var jwtParsingService: JwtParsingService

    fun checkAttributes(jwt: String): Boolean {
        val jwtObject= jwtParsingService.parseToken(jwt)
        return checkJwtForGroup(jwtObject.username, jwtObject.cognitoGroups) && checkForEnabledKms(jwtObject.username)
    }

    fun checkForEnabledKms(userName: String): Boolean {
        return awsCommunicator.checkForExistingEnabledKey(userName)
    }

    fun checkJwtForGroup(userName: String, cognitoGroups: List<String>): Boolean {
        for (i in cognitoGroups){
            if(userName in i){
                return true
            }
        }
        return false
    }
}
