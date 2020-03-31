package uk.gov.dwp.dataworks

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.core.env.Environment
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService
import uk.gov.dwp.dataworks.services.TaskDeploymentService


@SpringBootApplication
class Application
fun main(args: Array<String>) {

	SpringApplication.run(Application::class.java, *args)
}
