resource "random_id" "capacity_provider_suffix" {
  byte_length = 2
}

resource "aws_ecs_cluster" "user_host" {
  name               = var.name_prefix
  capacity_providers = [aws_ecs_capacity_provider.user_host.name]
  default_capacity_provider_strategy {
    capacity_provider = aws_ecs_capacity_provider.user_host.name
    weight            = 100
  }
  tags = merge(var.common_tags, { Name = var.name_prefix, Persistence = "True", AutoShutdown = "False" })
}

resource "aws_ecs_capacity_provider" "user_host" {
  name = "${var.name_prefix}-${random_id.capacity_provider_suffix.hex}"
  auto_scaling_group_provider {
    auto_scaling_group_arn         = aws_autoscaling_group.user_host.arn
    managed_termination_protection = "ENABLED"

    managed_scaling {
      status                    = "ENABLED"
      maximum_scaling_step_size = var.scaling.max
      minimum_scaling_step_size = var.scaling.step
      target_capacity           = 80
    }

  }
  depends_on = [aws_autoscaling_group.user_host]
  tags       = merge(var.common_tags, { "Name" : "${var.name_prefix}-user-host-provider" })
}
