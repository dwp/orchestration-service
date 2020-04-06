package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Service
import software.amazon.awssdk.regions.Region
import uk.gov.dwp.dataworks.exceptions.SystemArgumentException
import uk.gov.dwp.dataworks.services.ConfigKey.AWS_REGION

/**
 * Class used to source configuration from Java Environment Variables. This allows us to interpolate
 * variables required at runtime without the need for a static application.properties file. Thus,
 * this means we can pass secrets to the API and not commit them to the code base.
 */
@Service
class ConfigurationService {
    @Autowired
    private lateinit var env: Environment

    private val stringConfigs: MutableMap<ConfigKey, String> = mutableMapOf()
    private val listConfigs: MutableMap<ConfigKey, List<String>> = mutableMapOf()
    val awsRegion: Region = kotlin.runCatching { Region.of(getStringConfig(AWS_REGION)) }.getOrDefault(Region.EU_WEST_2)

    final fun getStringConfig(configKey: ConfigKey): String {
        return stringConfigs.computeIfAbsent(configKey) {
            env.getProperty(configKey.key) ?: throw SystemArgumentException("No value found for ${configKey.key}")
        }
    }

    final fun getListConfig(configKey: ConfigKey): List<String> {
        return listConfigs.computeIfAbsent(configKey) {
            val sysConfig = env.getProperty(configKey.key) ?: throw SystemArgumentException("No value found for ${configKey.key}")
            sysConfig.split(",").toList()
        }
    }

    fun getAllConfig(): Map<ConfigKey, Any> {
        ConfigKey.values().forEach {
            if(it.isList)
                getListConfig(it)
            else
                getStringConfig(it)
        }
        return stringConfigs.plus(listConfigs)
    }

    fun getIfEmpty(value: String, configKey: ConfigKey): String{
        return if(value != "") value else getStringConfig(configKey)
    }

    fun clear() {
        stringConfigs.clear()
        listConfigs.clear()
    }
}

enum class ConfigKey(val key: String, val isList: Boolean) {
    AWS_REGION("AWS_REGION", false),
    COGNITO_USER_POOL_ID("COGNITO_USER_POOL_ID", false),

    //   Access token env vars
    ACCESS_KEY("ACCESS_KEY", false),
    SECRET_ACCESS_KEY("SECRET_ACCESS_KEY", false),
    SESSION_TOKEN("SESSION_TOKEN", false)

}
