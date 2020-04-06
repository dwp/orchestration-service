package uk.gov.dwp.dataworks.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.model.Model
import uk.gov.dwp.dataworks.services.TaskDeploymentService

@RestController
class UserContainerController {
    @Autowired
    lateinit var taskDeploymentService: TaskDeploymentService

    @PostMapping("/deployusercontainers")
    fun launchTask(@RequestBody requestBody: Model){
        taskDeploymentService.taskDefinitionWithOverride(requestBody.ecsClusterName,requestBody.emrClusterHostName,requestBody.albName ,requestBody.userName)
    }

}
