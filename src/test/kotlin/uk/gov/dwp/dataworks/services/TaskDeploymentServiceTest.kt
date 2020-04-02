package uk.gov.dwp.dataworks.services


import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesResponse
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import uk.gov.dwp.dataworks.Application
import java.util.*


@RunWith(SpringRunner::class)
@ContextConfiguration(classes = [Application::class])
@SpringBootTest(properties = ["orchestrationService.cognito_user_pool_id=id"])
class TaskDeploymentServiceTest{

    @Autowired
    private lateinit var configService: ConfigurationService
    @Autowired
    private lateinit var authService: AuthenticationService
    @Autowired
    private lateinit var taskDeploymentService: TaskDeploymentService


    @Test
    fun `testPriorityNumberForNoRuleSetExpectOne`(){
        val actual =  taskDeploymentService.getPriorityVal(createDescribeRulesResponse())
        Assert.assertEquals(1, actual)
    }

    fun  `createDescribeRulesResponse`(): DescribeRulesResponse {
        val  list: List<Rule> = ArrayList<Rule>()
        val describeRulesResponse: DescribeRulesResponse = DescribeRulesResponse.builder().rules(list).build();
            return describeRulesResponse;
    }

}
