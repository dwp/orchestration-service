package uk.gov.dwp.dataworks

import cloud.localstack.LocalstackTestRunner
import cloud.localstack.docker.annotation.LocalstackDockerProperties
import org.assertj.core.api.Assertions.assertThat
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.context.annotation.Bean
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.iam.IamClient
import software.amazon.awssdk.services.kms.KmsClient
import uk.gov.dwp.dataworks.services.ConfigurationResolver
import java.net.URI

@RunWith(LocalstackTestRunner::class)
@LocalstackDockerProperties(services = ["dynamodb", "iam", "kms"])
class MyCloudAppTest {

    @Bean
    fun localDynamoDbClient(): DynamoDbClient {
        return DynamoDbClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4569"))
                .build()
    }
    @Bean
    fun localKmsClient(): KmsClient {
        return KmsClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }
    @Bean
    fun localIamClient(): IamClient {
        return IamClient.builder()
                .region(ConfigurationResolver().awsRegion)
                .endpointOverride(URI("http://localhost:4566"))
                .build()
    }

    @Test
    fun testConnection() {
        val localDb = localDynamoDbClient()
        assertThat(localDb.listTables()).isNotNull
        val localKmsClient = localKmsClient()
        assertThat(localKmsClient.listKeys()).isNotNull
        val localIamClient = localIamClient()
        assertThat(localIamClient.listAccessKeys()).isNotNull

    }
}
