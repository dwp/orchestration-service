package uk.gov.dwp.dataworks.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.ecs.EcsClient
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest
import software.amazon.awssdk.services.ecs.model.Service
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Action
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateRuleRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.CreateTargetGroupRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeListenersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeRulesRequest
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Listener
import software.amazon.awssdk.services.elasticloadbalancingv2.model.LoadBalancer
import software.amazon.awssdk.services.elasticloadbalancingv2.model.PathPatternConditionConfig
import software.amazon.awssdk.services.elasticloadbalancingv2.model.Rule
import software.amazon.awssdk.services.elasticloadbalancingv2.model.RuleCondition
import software.amazon.awssdk.services.elasticloadbalancingv2.model.TargetGroup
import uk.gov.dwp.dataworks.UpperRuleLimitReachedException
import uk.gov.dwp.dataworks.logging.DataworksLogger
import software.amazon.awssdk.services.ecs.model.LoadBalancer as EcsLoadBalancer

/**
 * Component Class to encapsulate any and all communications with AWS via the SDK API. This class allows separation of
 * duties, where any AWS communications are stored and handled.
 *
 * Ideally this will leave the remaining code in the repo to be clean and concise, as much processing is outsorced to
 * this component.
 *
 * Unit testing also becomes easier, as this component can be mocked and all AWS calls can be synthetically manipulated.
 */
@Component
class AwsCommunicator {
    companion object {
        val logger: DataworksLogger = DataworksLogger(LoggerFactory.getLogger(AwsCommunicator::class.java))
    }

    @Autowired
    private lateinit var configurationService: ConfigurationService

    private val albClient = ElasticLoadBalancingV2Client.builder().region(configurationService.awsRegion).build()
    private val ecsClient = EcsClient.builder().region(configurationService.awsRegion).build()

    /**
     * Retrieve a [LoadBalancer] from AWS given it's name. If multiple LoadBalancers are found with the same
     * name, an exception will be thrown.
     * @throws //TODO
     */
    fun getLoadBalancerByName(albName: String): LoadBalancer {
        val albs = albClient.describeLoadBalancers(DescribeLoadBalancersRequest.builder().names(albName).build())
        if (albs.loadBalancers().size == 1) return albs.loadBalancers()[0]
        else throw Exception() //TODO
    }

    /**
     * Retrieves a [Listener] from a [LoadBalancer] based on the name of the ALB and the port of the listener.
     * Note that, for simplicity, this method expects only a single binding on the listener and will always
     * return the first one found.
     */
    fun getAlbListenerByPort(loadBalancerArn: String, listenerPort: Int): Listener {
        val albListeners = albClient.describeListenersPaginator(DescribeListenersRequest.builder().loadBalancerArn(loadBalancerArn).build())
        return albListeners.listeners().filter { it.port() == listenerPort }.elementAtOrElse(0) { throw Exception() } //TODO
    }

    /**
     * Creates and returns a [TargetGroup] in the given VPC. This target group can later be assigned to
     * a [LoadBalancer] using it's ARN or [ElasticLoadBalancingV2Client.registerTargets].
     */
    fun createTargetGroup(vpcId: String, targetGroupName: String, targetPort: Int): TargetGroup {
        // Create HTTPS target group in VPC to port containerPort
        val targetGroupResponse = albClient.createTargetGroup(
                CreateTargetGroupRequest.builder()
                        .name(targetGroupName)
                        .protocol("HTTPS")
                        .vpcId(vpcId)
                        .port(targetPort)
                        .build())
        val targetGroup = targetGroupResponse.targetGroups().first { it.port() == targetPort }
        logger.info("Created target group",
                "vpc_id" to vpcId,
                "target_group_arn" to targetGroup.targetGroupArn(),
                "protocol" to targetGroup.protocolAsString(),
                "load_balancer_arns" to targetGroup.loadBalancerArns().joinToString(),
                "target_group_name" to targetGroupName,
                "target_port" to targetPort.toString())
        return targetGroup
    }

    /**
     * Creates a [LoadBalancer] routing rule for the [Listener] with given [listenerArn] and [TargetGroup]
     * of given [targetGroupArn].
     * The rule created will be a path-pattern forwarder based on [pathPattern].
     *
     * **See Also:** [AWS docs](https://docs.aws.amazon.com/elasticloadbalancing/latest/application/load-balancer-listeners.html)
     */
    fun createAlbRoutingRule(listenerArn: String, targetGroupArn: String, pathPattern: String) {
        // Build path pattern condition
        val albRuleCondition = RuleCondition.builder()
                .field("path-pattern")
                .pathPatternConfig(PathPatternConditionConfig.builder().values(pathPattern).build())
                .build()

        //Set up forwarding rule
        val albRuleAction = Action.builder().type("forward").targetGroupArn(targetGroupArn).build()

        //Get rules on listener & calculate vacant priority.
        val albRules = albClient.describeRules(DescribeRulesRequest.builder().listenerArn(listenerArn).build())
        val rulePriority = calculateVacantPriorityValue(albRules.rules())

        //Create the complete rule
        val rule = albClient.createRule(CreateRuleRequest.builder()
                .listenerArn(listenerArn)
                .priority(rulePriority)
                .conditions(albRuleCondition)
                .actions(albRuleAction)
                .build()).rules()[0]
        logger.info("Created alb routing rule",
                "actions" to rule.actions().joinToString(),
                "priority" to rule.priority(),
                "arn" to rule.ruleArn(),
                "conditions" to rule.conditions().joinToString())
    }

    /**
     * Helper method to calculate the lowest non-used priority in a given set of [Rules][Rule]. This excludes
     * the `default` rule and assumes that all rules have come from the same listener.
     */
    fun calculateVacantPriorityValue(rules: Iterable<Rule>): Int {
        val rulePriorities = rules.map { it.priority() }.filter { it != "default" }.map { Integer.parseInt(it) }.toSet()
        for (priority in 0..999) {
            if (!rulePriorities.contains(priority)) return priority
        }
        throw UpperRuleLimitReachedException("The upper limit of 1000 rules has been reached on this listener.")
    }

    /**
     * Creates an ECS service with the name [clusterName], friendly service name of [serviceName] and sits
     * it behind the load balancer [loadBalancer].
     *
     * For ease of use, the task definition is retrieved from the [task definition env var][ConfigKey.USER_CONTAINER_TASK_DEFINITION]
     */
    fun createEcsService(clusterName: String, serviceName: String, loadBalancer: EcsLoadBalancer): Service {
        // Create ECS service request
        val serviceBuilder = CreateServiceRequest.builder()
                .cluster(clusterName)
                .loadBalancers(loadBalancer)
                .serviceName(serviceName)
                .taskDefinition(configurationService.getStringConfig(ConfigKey.USER_CONTAINER_TASK_DEFINITION))
                .desiredCount(1)
                .build()

        //Create the service
        val ecsService = ecsClient.createService(serviceBuilder).service()
        logger.info("Created ECS Service",
                "cluster_name" to clusterName,
                "service_name" to serviceName,
                "cluster_arn" to ecsService.clusterArn(),
                "task_definition" to ecsService.taskDefinition())
        return ecsService
    }
}