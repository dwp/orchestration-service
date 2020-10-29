package uk.gov.dwp.dataworks.services

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.net.URL
import java.security.interfaces.RSAPublicKey
import javax.annotation.PostConstruct

@Service
class UserValidationService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(UserValidationService::class.java))
    }

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    internal lateinit var jwkProvider: JwkProvider
    internal lateinit var jwkProviderUrl: URL
    internal lateinit var issuerUrl: String

    @PostConstruct
    fun init() {
        val userPoolId: String = configurationResolver.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)
        issuerUrl = "https://cognito-idp.${configurationResolver.awsRegion}.amazonaws.com/$userPoolId"
        jwkProviderUrl = URL("$issuerUrl/.well-known/jwks.json")
        jwkProvider = UrlJwkProvider(jwkProviderUrl)
        AuthenticationService.logger.info("initialised JWK Provider", "user_pool_id" to userPoolId)
    }

    fun validateUser(jwtToken: String): Boolean {
        val userJwt = JWT.decode(jwtToken)
        val jwk = jwkProvider.get(userJwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey, null)
            else -> throw JwkException("Unsupported JWK algorithm")
        }
        return checkAttributes(userJwt)
    }

    fun checkAttributes(decodedJwt: DecodedJWT): Boolean {
        val groups = decodedJwt.getClaim("cognito:groups").asList(String::class.java)
                ?: throw IllegalArgumentException("No cognito:groups found in JWT token")
        val userName = decodedJwt.getClaim("preferred_username").asString()
                ?: decodedJwt.getClaim("cognito:username").asString()
                ?: decodedJwt.getClaim("username").asString()
                ?: throw IllegalArgumentException("No username found in JWT token")
        val sub = decodedJwt.getClaim("sub").asString()
                ?: throw IllegalArgumentException("No sub found in JWT token")
        val subPrefix = sub.take(3)
        val userFull = listOf(userName, subPrefix).joinToString()

        return checkJwtForGroup(userFull, groups) && checkForEnabledKms(userFull)
    }

    fun checkForEnabledKms(userFull: String): Boolean {
        return awsCommunicator.checkForExistingEnabledKey(userFull)
    }


    fun checkJwtForGroup(userFull: String, groups: List<String>): Boolean {
        for (i in groups){
            if(userFull in i){
                return true
            }
        }
        return false
    }
}
