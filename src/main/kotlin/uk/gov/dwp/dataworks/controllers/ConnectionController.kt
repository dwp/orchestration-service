package uk.gov.dwp.dataworks.controllers


import com.auth0.jwk.UrlJwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService
import java.net.URL
import java.security.interfaces.RSAPublicKey


@RestController
class ConnectionController {

    @Autowired
    private lateinit var configService: ConfigurationService

    @Operation(summary = "Connect to Analytical Environment",
            description = "Provisions the Analytical Environment for a user and returns the required information to connect")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "401", description = "Failure: Unauthorized")
    ])
    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.OK)
    fun connect(@RequestHeader(value = "Authorisation") token: String): DecodedJWT {
        //  fetching the JWKS
        val jwkProvider = UrlJwkProvider(
                URL("https://cognito-idp.${configService.awsRegion}.amazonaws.com/${configService.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)}/.well-known/jwks.json")
        )
        //  decoding token and grab kid from header
        val jwt = JWT.decode(token)
        val jwk = jwkProvider.get(jwt.keyId)
        val publicKey = jwk.publicKey as? RSAPublicKey ?: throw Exception("Invalid key type")
        //  grab right algorithm from header and throw error, if different
        val algorithm = when (jwk.algorithm) {
            "RS256" -> Algorithm.RSA256(publicKey)
            else -> throw Exception("Unsupported Algorithm")
        }
        // verify algorithm
        val verifier = JWT.require(algorithm) // signature
                //  verify source
                .withIssuer("http://cognito-idp.${configService.awsRegion}.amazonaws.com/${configService.getStringConfig(ConfigKey.COGNITO_USER_POOL_ID)}") // iss
                //   not sure????
                .withAudience("api1") // aud
                .build()
        println("success!")
        return verifier.verify(token)
    }
}
