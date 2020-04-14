package uk.gov.dwp.dataworks.services

import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse

@Service
class EcsDescribeServicesCall{
    fun servicesResponse(ecsClient: EcsClient, ecsClusterName: String, userName: String): DescribeServicesResponse {
        return ecsClient.describeServices(DescribeServicesRequest.builder().cluster(ecsClusterName).services("${userName}-ui-service").build())
    }
}
