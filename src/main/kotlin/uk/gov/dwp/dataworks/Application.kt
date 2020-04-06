package uk.gov.dwp.dataworks

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import uk.gov.dwp.dataworks.services.ConfigKey
import uk.gov.dwp.dataworks.services.ConfigurationService


@SpringBootApplication
class Application
fun main(args: Array<String>) {

	SpringApplication.run(Application::class.java, *args)
}
