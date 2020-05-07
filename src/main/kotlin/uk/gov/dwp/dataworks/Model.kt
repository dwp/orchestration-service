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

@JsonDeserialize(builder = StatementObject.StatementObjectBuilder::class)
data class StatementObject(var Sid: String, var Effect: String, var Action: List<String>, var Resource: List<String>) {
    @JsonPOJOBuilder(buildMethodName = "StatementObjectBuilder", withPrefix = "set")
    class StatementObjectBuilder {
        private lateinit var Sid: String
        private lateinit var Effect: String
        private lateinit var Action: List<String>
        private lateinit var Resource: List<String>

        @JsonProperty("Sid")
        fun setSid(Sid: String): StatementObjectBuilder {
            this.Sid = Sid
            return this
        }

        @JsonProperty("Effect")
        fun setEffect(Effect: String): StatementObjectBuilder {
            this.Effect = Effect
            return this
        }

        @JsonProperty("Action")
        fun setAction(Action: List<String>): StatementObjectBuilder {
            this.Action = Action
            return this
        }

        @JsonProperty("Resource")
        fun setResource(Resource: List<String>): StatementObjectBuilder {
            this.Resource = Resource
            return this
        }

        fun StatementObjectBuilder(): StatementObject {
            return StatementObject(Sid, Effect, Action, Resource)
        }
    }
}

@JsonDeserialize(builder = AwsIamPolicyJsonObject.JsonObjectBuilder::class)
data class AwsIamPolicyJsonObject(var Version: String, var Statement: List<StatementObject>){
    @JsonPOJOBuilder(buildMethodName = "JsonObjectBuilder", withPrefix = "set")
    class JsonObjectBuilder{
        private lateinit var Version: String
        private lateinit var Statement: List<StatementObject>

        @JsonProperty("Version")
        fun setVersion(Version: String): JsonObjectBuilder {
            this.Version = Version
            return this
        }

        @JsonProperty("Statement")
        fun setStatement( Statement: List<StatementObject>):  JsonObjectBuilder {
            this.Statement = Statement
            return this
        }

        fun JsonObjectBuilder(): AwsIamPolicyJsonObject {
            return AwsIamPolicyJsonObject(Version, Statement)
        }
    }
}
