resource "aws_ecs_task_definition" "service" {
  family                = "${var.name_prefix}-ui_service"
  container_definitions = {
    <<
    PATTERN
      [
      {
          "name": "headless_chrome",
          "image": "service-first",
          "cpu": 512,
          "memory": 2014,
          "essential": true,
      },
      {

          "name": "jupyterHub",
          "image": "service-first",
          "cpu": 512,
          "memory": 2014,
          "essential": true,

         },
      {
          "name": "guacD",
          "image": "service-first",
          "cpu": 512,
          "memory": 2014,
          "essential": true,
          "portMappings": [
            {
              "containerPort": 8000,
              "hostPort": 0
            }
          ]
      }
  ]
PATTERN
  }
  network_mode = "bridge"

}


//resource "aws_ecs_task_definition" "td" {
//  family                = "${var.name_prefix}-td"
//  container_definitions = module.container_definition.json
//  execution_role_arn    = aws_iam_role.ecs_task_execution_role.arn
//  task_role_arn         = aws_iam_role.ecs_task_role.arn
//  network_mode          = "awsvpc"
//  dynamic "placement_constraints" {
//    for_each = var.placement_constraints
//    content {
//      expression = lookup(placement_constraints.value, "expression", null)
//      type       = placement_constraints.value.type
//    }
//  }
//  cpu                      = var.container_cpu
//  memory                   = var.container_memory
//  requires_compatibilities = ["FARGATE"]
//  dynamic "proxy_configuration" {
//    for_each = var.proxy_configuration
//    content {
//      container_name = proxy_configuration.value.container_name
//      properties     = lookup(proxy_configuration.value, "properties", null)
//      type           = lookup(proxy_configuration.value, "type", null)
//    }
//  }
//  tags = merge(var.common_tags, { Name = "${var.name_prefix}-td" })
//}
