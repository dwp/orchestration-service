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
import java.lang.reflect.Modifier

@Component
class AwsParsing(){
    @Autowired
    private lateinit var  configurationResolver: ConfigurationResolver

    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    /**
     * Helper method to initialise the lateinit vars [taskRolePolicyString] and [jupyterBucketAccessRolePolicyString] by
     * converting the JSON to an instance of `JsonObject` data class and adding any requirements before serialising back to JSON
     *
     * @return [taskRolePolicyString] and [jupyterBucketAccessRolePolicyString] respectively.
     */
    fun parsePolicyDocument(resource: Resource, sidAndAdditions: Map<String, List<String>>, key: String): String{
        val mapper = ObjectMapper()
                .setPropertyNamingStrategy(AwsPropertyNamingStrategy())
        val obj = mapper.readValue(resource.url, AwsIamPolicyJsonObject::class.java)
        obj.Statement.forEach {statement ->
            sidAndAdditions.forEach {
                if(it.key == statement.Sid) {
                    if (key == "Resource") statement.Resource = statement.Resource.plus(it.value)
                    else if (key == "Action") statement.Action = statement.Action.plus(it.value)
                    else throw IllegalArgumentException("Key does not match expected values: \"Resource\" or \"Action\"")
                }
            }
        }
        return mapper.writeValueAsString(obj)
    }

    /**
     * Helper method to parse user access details into ARN format - returns list of these and the JupyterBucket ARN
     */
    fun createArnStringsList(pathPrefix: List<String>, pathSuffix: String, jupyterBucketArn: String): List<String>{
        var returnList = listOf(jupyterBucketArn)
        pathPrefix.forEach{
            returnList = returnList.plus("arn:aws:kms:${configurationResolver.awsRegion}:${awsCommunicator.getAccNumber()}:alias/${it}-${pathSuffix}")
        }
        return returnList
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
