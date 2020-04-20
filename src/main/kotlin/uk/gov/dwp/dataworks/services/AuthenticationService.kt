package uk.gov.dwp.dataworks.services

import com.auth0.jwk.JwkException
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.model.JWTObject
import java.net.URL
import java.security.interfaces.RSAPublicKey
import javax.annotation.PostConstruct

/**
 * Service used to verify and validate JWT tokens included in requests
 */
@Service
class AuthenticationService {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AuthenticationService::class.java))
    }

    @Autowired
    private lateinit var configService: ConfigurationService;

    private lateinit var jwkProvider: JwkProvider
    private lateinit var issuerUrl: String

    @PostConstruct
    fun init() {
        val userPoolId: String = configService.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)
        issuerUrl = "https://cognito-idp.${configService.awsRegion}.amazonaws.com/$userPoolId"
        jwkProvider = UrlJwkProvider(URL("$issuerUrl/.well-known/jwks.json"))
        logger.info("initialised JWK Provider", "user_pool_id" to userPoolId)
    }

    fun validate(jwtToken: String): JWTObject {
        val userJwt = JWT.decode(jwtToken)
        val jwk = jwkProvider.get(userJwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey, null)
            else -> throw JwkException("Unsupported JWK algorithm")
        }

        val jwt = JWT.require(algorithm)
                .withIssuer(issuerUrl)
                .build()
                .verify(userJwt)

        return JWTObject(jwt, cognitoUsernameFromJwt(userJwt))
    }

    /**
     * Helper method to extract the Cognito username from a JWT Payload.
     */
    fun cognitoUsernameFromJwt(jwt: DecodedJWT): String {
        val tokenJson = ObjectMapper().readTree(jwt.payload)
        return tokenJson.get("cognito:username").asText()
    }
}