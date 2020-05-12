package uk.gov.dwp.dataworks.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.AwsIamPolicyJsonObject
import uk.gov.dwp.dataworks.logging.DataworksLogger
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import uk.gov.dwp.dataworks.services.TaskDeploymentService
import java.io.File
import java.lang.reflect.Modifier

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
                .setPropertyNamingStrategy(AwsPropertyNamingStrategy())
        val obj = mapper.readValue(resource.url, AwsIamPolicyJsonObject::class.java)
        obj.Statement.forEach { statement ->
            when(statementKeyToUpdate) {
                "Resource" -> try{ statement.Resource.plus(sidAndAdditions[statement.Sid]) }
                            catch(e: Exception) { throw java.lang.IllegalArgumentException("${statement.Sid} not found in JSON template") }
                "Action" -> try{ statement.Action.plus(sidAndAdditions[statement.Sid]) }
                            catch(e: Exception){ throw java.lang.IllegalArgumentException("${statement.Sid} not found in JSON template") }
                else -> throw IllegalArgumentException("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
            }
        }
        return mapper.writeValueAsString(obj)
    }
}

/**
 * Class to override Jackson methods and ensure Proper Case is respected in serialisation of JSON keys - as required by AWS
 */
class AwsPropertyNamingStrategy : PropertyNamingStrategy() {
    override fun nameForField(config: MapperConfig<*>?, field: AnnotatedField, defaultName: String): String {
        return convertForField(defaultName)
    }

    override fun nameForGetterMethod(config: MapperConfig<*>?, method: AnnotatedMethod, defaultName: String): String {
        return convertForMethod(method, defaultName)
    }

    override fun nameForSetterMethod(config: MapperConfig<*>?, method: AnnotatedMethod, defaultName: String): String {
        return convertForMethod(method, defaultName)
    }

    private fun convertForField(defaultName: String): String {
        return defaultName
    }

    private fun convertForMethod(method: AnnotatedMethod, defaultName: String): String {
        if (isGetter(method)) {
            return method.name.substring(3)
        }
        return if (isSetter(method)) {
            method.name.substring(3)
        } else defaultName
    }

    private fun isGetter(method: AnnotatedMethod): Boolean {
        if (Modifier.isPublic(method.modifiers) && method.genericParameterTypes.size == 0) {
            if (method.name.matches(Regex("^get[A-Z].*")) && method.rawReturnType != Void.TYPE) return true
            if (method.name.matches(Regex("^is[A-Z].*")) && method.rawReturnType != Boolean::class.javaPrimitiveType) return true
        }
        return false
    }

    private fun isSetter(method: AnnotatedMethod): Boolean {
        return Modifier.isPublic(method.modifiers) && method.rawReturnType == Void.TYPE && method.genericParameterTypes.size == 1 && method.name.matches(Regex("^set[A-Z].*"))
    }
}
