package uk.gov.dwp.dataworks.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.ecs.EcsClient

@Service
class ExistingUserServiceCheck{
    @Autowired
    private lateinit var ecsDescribeServicesCall: EcsDescribeServicesCall
    @Autowired
    private lateinit var configService : ConfigurationService

    fun check(userName: String, ecsClusterName: String):Boolean{
        val ecsClient = EcsClient.builder().region(configService.awsRegion).build()
        val listOfService = ecsDescribeServicesCall.servicesResponse(ecsClient, ecsClusterName, userName).services()

        println(listOfService)

        if(listOfService.size > 0 && listOfService[0].status()=="ACTIVE"){
            return true
        }
        return false
    }
}
