package uk.gov.dwp.dataworks.aws

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategy
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import uk.gov.dwp.dataworks.AwsIamPolicyJsonObject
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import java.io.File
import java.lang.reflect.Modifier

@Component
class AwsParsing(){
    @Autowired
    private lateinit var  configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    /**
     * Helper method converts JSON IAM Policy to an instance of `AwsIamPolicyJsonObject` data class
     * and inserts extra values before serialising back to JSON string.
     */
    fun parsePolicyDocument(resource: Resource, additionsForSid: Map<String, List<String>>, statementKeyToUpdate: String): String{
        val mapper = ObjectMapper()
                .setPropertyNamingStrategy(AwsPropertyNamingStrategy())
        val obj = mapper.readValue(File(resource.uri), AwsIamPolicyJsonObject::class.java)
        val updatedStatements = obj.Statement.map { statement ->
            val additions = additionsForSid[statement.Sid] ?: return@map statement
            when(statementKeyToUpdate) {
                "Resource" -> return@map statement.let { it.Resource = it.Resource.plus(additions); it }
                "Action" -> return@map statement.let { it.Action = it.Action.plus(additions); it }
                else -> throw IllegalArgumentException("statementKeyToUpdate does not match expected values: \"Resource\" or \"Action\"")
            }
        }
        val updatedObject = obj.let { it.Statement = updatedStatements; it }
        return mapper.writeValueAsString(updatedObject)
    }

    /**
     * Helper method to parse user access details into ARN format - returns list of these and the JupyterBucket ARN
     */
    fun createArnStringsList(pathPrefix: List<String>, pathSuffix: String, jupyterBucketArn: String): List<String>{
        var kmsArnListList = pathPrefix.map{
            "arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/${it}-${pathSuffix}"
        }
        return kmsArnListList.plus(jupyterBucketArn)
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
