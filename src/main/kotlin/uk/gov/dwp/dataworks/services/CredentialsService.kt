package uk.gov.dwp.dataworks.services

import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider

@Service
class CredentialsService{
    fun getDefaultCredentialsProvider():   DefaultCredentialsProvider{
        val credentials = DefaultCredentialsProvider.builder().build()
        return credentials
    }

    fun getSessionCredentials() : StaticCredentialsProvider {
        lateinit var configService: ConfigurationService

        val access_key = configService.getStringConfig(ConfigKey.ACCESS_KEY)
        val secret_key = configService.getStringConfig(ConfigKey.SECRET_ACCESS_KEY)
        val session_token = configService.getStringConfig(ConfigKey.SESSION_TOKEN)

        val awsSessionCredentials = AwsSessionCredentials.create(access_key, secret_key, session_token)
        val scp = StaticCredentialsProvider.create(awsSessionCredentials)

        return scp
    }

}