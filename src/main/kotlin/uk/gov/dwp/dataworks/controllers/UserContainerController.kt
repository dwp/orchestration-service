package uk.gov.dwp.dataworks.controllers

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.model.JWTObject
import uk.gov.dwp.dataworks.model.Model
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService
import uk.gov.dwp.dataworks.services.ExistingUserServiceCheck
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import uk.gov.dwp.dataworks.services.TaskDeploymentService.Companion.logger

@RestController
class UserContainerController {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(UserContainerController::class.java))
    }

    @Autowired
    lateinit var taskDeploymentService: TaskDeploymentService
    @Autowired
    lateinit var existingUserServiceCheck: ExistingUserServiceCheck
    @Autowired
    lateinit var configService: ConfigurationService
    @Autowired
    lateinit var jwtObject: JWTObject

    @Operation(summary = "Requests the user containers",
            description = "Provisions the user containers for remote desktops")
    @ApiResponses(value = [
        ApiResponse(responseCode = "200", description = "Success"),
        ApiResponse(responseCode = "400", description = "Failure, bad request")
    ])
    @PostMapping("/deployusercontainers")
    fun launchTask(@RequestBody requestBody: Model): String{
        if (existingUserServiceCheck.check(requestBody.userName, configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME))){
            logger.info("Redirecting user to running containers, as they exist")
        } else {
            taskDeploymentService.taskDefinitionWithOverride(
                    requestBody.userName,
                    requestBody.containerPort,
                    requestBody.jupyterCpu,
                    requestBody.jupyterMemory,
                    requestBody.additionalPermissions
            )
            logger.info("Submitted request", "cluster_name" to configService.getStringConfig(ConfigKey.ECS_CLUSTER_NAME), "user_name" to requestBody.userName)
        }
        return jwtObject.userUrl
    }

}
