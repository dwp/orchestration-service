output "outputs" {
  value = {
    ecs_cluster              = {
        arn = aws_ecs_cluster.user_host.arn
    }
    security_group_id        = aws_security_group.user_host.id
  }
}
