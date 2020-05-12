package uk.gov.dwp.dataworks

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType
import kotlin.reflect.full.declaredMemberProperties


data class DeployRequest @JsonCreator constructor(
        val jupyterCpu: Int = 512,
        val jupyterMemory: Int = 512,
        val additionalPermissions: List<String> = emptyList()
)

data class JWTObject(val verifiedJWT: DecodedJWT, val userName: String, val cognitoGroup: List<String>)

data class UserTask(val correlationId: String,
                    val userName: String,
                    val targetGroupArn: String?,
                    val albRoutingRuleArn: String?,
                    val ecsClusterName: String?,
                    val ecsServiceName: String?,
                    val iamRoleName: String?,
                    val iamPolicyArn: String?) {
    companion object {
        fun from(map: Map<String, String>) = object {
            val correlationId: String by map
            val userName: String by map
            val targetGroupArn: String by map
            val albRoutingRuleArn: String by map
            val ecsClusterName: String by map
            val ecsServiceName: String by map
            val iamRoleName: String by map
            val iamPolicyArn: String by map
            val data = UserTask(correlationId, userName, targetGroupArn, albRoutingRuleArn, ecsClusterName,
                    ecsServiceName, iamRoleName, iamPolicyArn)
        }.data

        fun attributes(): List<AttributeDefinition> {
            return UserTask::class.declaredMemberProperties
                    .map { AttributeDefinition.builder().attributeName(it.name).attributeType(ScalarAttributeType.S).build() }
        }
    }
}

data class StatementObject(
        @JsonProperty("Sid") var Sid: String,
        @JsonProperty("Effect") var Effect: String,
        @JsonProperty("Action") var Action: List<String>,
        @JsonProperty("Resource") var Resource: List<String>
)

data class AwsIamPolicyJsonObject(
        @JsonProperty("Version") var Version: String,
        @JsonProperty("Statement") var Statement: List<StatementObject>
)
