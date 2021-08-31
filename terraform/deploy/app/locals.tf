locals {
  log_driver = "awslogs"
  log_options = {
    "awslogs-region"        = var.region
    "awslogs-group"         = "/ecs/service/${var.name_prefix}"
    "awslogs-stream-prefix" = "ecs"
  }
  guacamole_port = 8443
  ecr_endpoint   = "${local.account[local.management_account[local.environment]]}.dkr.ecr.${var.region}.amazonaws.com"

  ecs_user_host_instance_type = {
    development = "m5.4xlarge"
    qa          = "m5.2xlarge"
    integration = "m5.2xlarge"
    preprod     = "m5.2xlarge"
    production  = "m5.8xlarge"
  }

  scaling = {
    development = { max : 10, step : 1 },
    qa          = { max : 10, step : 1 },
    integration = { max : 10, step : 1 },
    preprod     = { max : 10, step : 1 },
    production  = { max : 10, step : 1 }
  }
}
