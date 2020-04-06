package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region

@Service
class CredentialsService{

    private val awsRegion: Region = kotlin.runCatching { Region.of(System.getenv(ConfigKey.AWS_REGION.toString()))}.getOrDefault(Region.EU_WEST_2)

    @Autowired
    lateinit var configService: ConfigurationService

    fun getDefaultCredentialsProvider():   DefaultCredentialsProvider{
        val credentials = DefaultCredentialsProvider.builder().build()
        return credentials
    }

    fun getSessionCredentials() : StaticCredentialsProvider {
        val access_key = System.getProperty("ACCESS_KEY")
        val secret_key = System.getProperty("SECRET_ACCESS_KEY")
        val session_token = System.getProperty("SESSION_TOKEN")

        val awsSessionCredentials = AwsSessionCredentials.create(access_key, secret_key, session_token)
        val scp = StaticCredentialsProvider.create(awsSessionCredentials)

        return scp
    }
    fun getAwsRegion(): Region{
        return awsRegion
    }

}