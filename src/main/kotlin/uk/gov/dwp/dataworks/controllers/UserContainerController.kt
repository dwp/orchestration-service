package uk.gov.dwp.dataworks.controllers

import jdk.jfr.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.entity.UserRequestFramework
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import java.awt.PageAttributes

@RestController
class UserContainerController {
    @Autowired
    lateinit var taskDeploymentService: TaskDeploymentService

    @Autowired
    lateinit var configService: ConfigurationService

    @PostMapping("/frontendrequest")
    fun launchTask(@RequestBody requestBody: UserRequestFramework){
        taskDeploymentService.taskDefinitionWithOverride(requestBody.getEcs_cluster_name(),requestBody.getEmr_cluster_host_name(),requestBody.getalb_name() ,requestBody.getUser_name())
    }

}
