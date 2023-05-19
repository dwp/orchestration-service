#
## ---------------------------------------------------------------------------------------------------------------------
## Analytical UI Container Log Group
## ---------------------------------------------------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "user_container_log_group" {
  name              = local.log_group
  tags              = var.common_tags
  retention_in_days = 180
}

## ---------------------------------------------------------------------------------------------------------------------
## ECS Instance Log Group
## ---------------------------------------------------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "userhost_instance_log_group" {
  name              = local.cw_userhost_agent_log_group_name
  retention_in_days = 180
  tags              = var.common_tags
}
