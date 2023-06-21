data "aws_availability_zones" "available" {}

data "aws_ecs_cluster" "ecs_main_cluster" {
  cluster_name = "main"
}

data "aws_ec2_managed_prefix_list" "list" {
  name = "dwp-*-aws-cidrs-*"
}

data "aws_secretsmanager_secret_version" "terraform_secrets" {
  provider  = aws.management_dns
  secret_id = "/concourse/dataworks/terraform"
}
