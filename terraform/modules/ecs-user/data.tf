
data "aws_region" "current" {}

data "aws_caller_identity" "current" {}

data "terraform_remote_state" "aws_analytical_env_infra" {
  backend = "s3"
  workspace = terraform.workspace

  config = {
    bucket         = "{{terraform.state_file_bucket}}"
    key            = "terraform/dataworks/aws-analytical-environment_infra.tfstate"
    region         = "{{terraform.state_file_region}}"
    encrypt        = true
    kms_key_id     = "arn:aws:kms:{{terraform.state_file_region}}:{{terraform.state_file_account}}:key/{{terraform.state_file_kms_key}}"
    dynamodb_table = "remote_state_locks"
  }
}