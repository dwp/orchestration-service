package uk.gov.dwp.dataworks.controllers


import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.exceptions.JWTVerificationException
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import uk.gov.dwp.dataworks.services.*


@RestController
class ConnectionController {

    @Autowired
    private lateinit var authService: AuthenticationService
    @Autowired
    private lateinit var existingUserServiceCheck: ExistingUserServiceCheck
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService
    @Autowired
    private lateinit var configService: ConfigurationService

    @Operation(summary = "Connect to Analytical Environment",
            description = "Provisions the Analytical Environment for a user and returns the required information to connect")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/connect")
    @ResponseStatus(HttpStatus.OK)
    fun connect(@RequestHeader("Authorization") token: String, @RequestBody requestBody: uk.gov.dwp.dataworks.model.Model): String {
        val jwtObject = authService.validate(token)
        if (existingUserServiceCheck.check(jwtObject.userName, configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME))){
            UserContainerController.logger.info("Redirecting user to running containers, as they exist")
        } else {
            taskDeploymentService.taskDefinitionWithOverride(
                    jwtObject.userName,
                    requestBody.containerPort,
                    requestBody.jupyterCpu,
                    requestBody.jupyterMemory,
                    requestBody.additionalPermissions
            )
            UserContainerController.logger.info("Submitted request", "cluster_name" to configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME), "user_name" to jwtObject.userName)
        }
        return "${configService.getStringConfig(ConfigKey.USER_CONTAINER_URL)}/${jwtObject.userName}"
    }

    @Operation(summary = "Disconnect from Analytical Environment",
            description = "Performs clean-up tasks after a user has disconnected")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "401", description = "Failure, unauthorized")
    ])
    @PostMapping("/disconnect")
    @ResponseStatus(HttpStatus.OK)
    fun disconnect(@RequestHeader("Authorization") token: String) {
        authService.validate(token)
    }

    @ExceptionHandler(JWTVerificationException::class, SigningKeyNotFoundException::class)
    @ResponseStatus(value = HttpStatus.UNAUTHORIZED, reason = "Invalid authentication token")
    fun handleInvalidToken() {
        // Do nothing - annotations handle response
    }
}
