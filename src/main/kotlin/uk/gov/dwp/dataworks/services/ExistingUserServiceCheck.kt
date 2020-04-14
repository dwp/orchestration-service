package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient

@Service
class ExistingUserServiceCheck{
    @Autowired
    private lateinit var ecsDescribeServicesCall: EcsDescribeServicesCall
    val configService = ConfigurationService()

    fun check(userName: String, ecsClusterName: String):Boolean{
        val ecsClient = EcsClient.builder().region(configService.awsRegion).build()

        for (i in ecsDescribeServicesCall.servicesResponse(ecsClient, ecsClusterName).services()){
            if(i.serviceName()=="${userName}-ui-service" && i.status()=="ACTIVE"){
                return true
            }
        }
        return false
    }
}

