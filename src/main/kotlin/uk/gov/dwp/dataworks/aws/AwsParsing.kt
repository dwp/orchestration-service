package uk.gov.dwp.dataworks.aws

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.AwsIamPolicyJsonObject
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.ConfigurationResolver

@Component
class AwsParsing(){
    @Autowired
    private lateinit var  configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsParsing::class.java))
    }

    /**
     * Helper method converts JSON IAM Policy to an instance of `AwsIamPolicyJsonObject` data class
     * and inserts extra values before serialising back to JSON string.
     */
    fun parsePolicyDocument(pathToResource: String, sidAndAdditions: Map<String, List<String>>, statementKeyToUpdate: String): String {
        val resource = ClassPathResource(pathToResource)
        val mapper = ObjectMapper()
        val obj = mapper.readValue(resource.file, AwsIamPolicyJsonObject::class.java)
        obj.Statement.filter { sidAndAdditions.containsKey(it.Sid) }
                .forEach{ statement ->
                when(statementKeyToUpdate) {
                    "Resource" -> sidAndAdditions[statement.Sid]?.let { statement.Resource.addAll(it) }
                    "Action" -> sidAndAdditions[statement.Sid]?.let { statement.Action.addAll(it) }
                    else -> throw IllegalArgumentException("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
            }
        }
        return mapper.writeValueAsString(obj)
    }
}
