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
import java.lang.Exception
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


    val nonConsecutiveCol : Collection<Rule> = listOf(Rule.builder().priority("1").build(), Rule.builder().priority("2").build(), Rule.builder().priority("4").build())

    @Test
    fun `testPriorityNumberForNoRuleSetExpectOne`(){
        val actual =  taskDeploymentService.getPriorityVal(createDescribeRulesResponse(ArrayList<Rule>()))
        Assert.assertEquals(1, actual)
    }

    @Test
    fun `testPriorityNumberForNonConsecutiveRuleSetExpectThree`(){
        val actual =  taskDeploymentService.getPriorityVal(createDescribeRulesResponse(nonConsecutiveCol))
        Assert.assertEquals(3, actual)
    }

    @Test (expected = Exception::class)
    fun `testPriorityNumberFor1000PlusExpectError`(){
        val actual =  taskDeploymentService.getPriorityVal(createDescribeRulesResponse(create1000()))
        Assert.assertEquals("The upper limit of 1000 rules has been reached for this load balancer.", actual)
    }

    fun  `createDescribeRulesResponse`(array: Collection<Rule>): DescribeRulesResponse {
        val  list: Collection<Rule> = array
        val describeRulesResponse: DescribeRulesResponse = DescribeRulesResponse.builder().rules(list).build();
            return describeRulesResponse;
    }

    fun create1000() : Collection<Rule> {
        var oneThousandCol: Collection<Rule> = emptyList()
        var i = 1
        while(i<1000) {
            oneThousandCol = oneThousandCol.plus(Rule.builder().priority(i.toString()).build())
            i++
        }
        return oneThousandCol
    }
}
