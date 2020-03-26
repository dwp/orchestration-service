package uk.gov.dwp.dataworks.controllers

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService

@RestController
class UserContainerController {
    @Autowired
    lateinit var configService: ConfigurationService

    @GetMapping("/hello")
    fun hello(): String {
        return "hello!"
    }

}