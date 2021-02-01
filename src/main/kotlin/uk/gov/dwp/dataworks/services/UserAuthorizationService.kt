package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.rdsdata.model.Field
import software.amazon.awssdk.services.rdsdata.model.RdsDataException
import software.amazon.awssdk.services.rdsdata.model.SqlParameter
import uk.gov.dwp.dataworks.aws.AwsCommunicator
import uk.gov.dwp.dataworks.logging.DataworksLogger
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct

enum class ToolingPermission(val permissionName: String) {
    FILE_TRANSFER("file_transfer"),
    CLIPBOARD_OUT("clipboard_out")
}

enum class Effect(val action: String) {
    ALLOW("allow"),
    DENY("deny")
}

val mySQLTimestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Service
class UserAuthorizationService {
    @Autowired
    private lateinit var awsCommunicator: AwsCommunicator

    @Autowired
    private lateinit var configurationResolver: ConfigurationResolver

    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(UserAuthorizationService::class.java))
    }

    @PostConstruct
    private fun initialisePermissionsTable() {
        awsCommunicator.rdsExecuteStatement(
            """
            INSERT IGNORE INTO
                `ToolingPermission`
             VALUES
                ${ToolingPermission.values().joinToString(",") { """(DEFAULT, "$it")""" }}
            """.trimIndent()
        )
        logger.info("Initialised ToolingPermission table")
    }

    private fun isPermissionOverridden(permission: ToolingPermission): Boolean {
        val overrides = configurationResolver
            .getListConfigOrDefault(ConfigKey.TOOLING_PERMISSION_OVERRIDES, listOf())
        return if (overrides.contains(permission.permissionName)) {
            logger.warn(
                "Override enabled for permission `$permission`! " +
                        "Remove `tooling_permission_overrides` env var to enable checking for this permission"
            )
            true
        } else {
            false
        }
    }

    fun hasUserToolingPermission(username: String, permission: ToolingPermission): Boolean {
        if (isPermissionOverridden(permission)) return true

        val sql = """
            SELECT gtp.effect, gtp.validUntil
            FROM `User` u
            INNER JOIN `UserGroup` ug
                ON u.id = ug.userId
            INNER JOIN `GroupToolingPermission` gtp
                ON gtp.groupId = ug.groupId
            INNER JOIN `ToolingPermission` tp
            ON tp.id = gtp.permissionId
            WHERE 
                u.username = :username
                AND
                tp.permissionName = :permissionName
                AND
                gtp.effect = 'allow'
            """.trimIndent()
        val parameters = listOf(
            SqlParameter.builder()
                .name("username")
                .value(Field.builder().stringValue(username).build())
                .build(),
            SqlParameter.builder()
                .name("permissionName")
                .value(Field.builder().stringValue(permission.permissionName).build())
                .build()
        )
        try {
            val result = awsCommunicator.rdsExecuteStatement(sql, parameters)
            if (!result.hasRecords()) {
                logger.info("User `$username` does not have a permission record for permission `$permission`")
                return false
            }

            val validAllowRecords = result.records()
                .filter {
                    it[0].stringValue() == Effect.ALLOW.action
                            && LocalDateTime.parse(it[1].stringValue(), mySQLTimestampFormatter) > LocalDateTime.now()
                }

            if (validAllowRecords.isEmpty()) {
                logger.debug("Permission `$permission` denied for `$username`")
                return false
            }

            return true
        } catch (e: RdsDataException) {
            return false
        }
    }
}
