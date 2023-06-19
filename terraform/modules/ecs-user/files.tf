data "local_file" "cloudwatch_agent_script" {
  filename = "files/userhost/cloudwatch_agent.sh"
}

resource "aws_s3_object" "cloudwatch_agent_script" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/cloudwatch_agent.sh"
  content    = data.local_file.cloudwatch_agent_script.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "userhost_config_hcs_script" {
  filename = "files/userhost/userhost_config_hcs.sh"
}

resource "aws_s3_object" "userhost_config_hcs_script" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/userhost_config_hcs.sh"
  content    = data.local_file.userhost_config_hcs_script.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "userhost_logging_script" {
  filename = "files/userhost/userhost_logging.sh"
}

resource "aws_s3_object" "userhost_logging_script" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/userhost_logging_script.sh"
  content    = data.local_file.userhost_logging_script.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "userhost_logrotate_script" {
  filename = "files/userhost/userhost.logrotate"
}

resource "aws_s3_object" "userhost_logrotate_script" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/userhost.logrotate"
  content    = data.local_file.userhost_logrotate_script.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "sysdig_service" {
  filename = "audit/sysdig.service"
}

resource "aws_s3_object" "sysdig_service" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/sysdig.service"
  content    = data.local_file.sysdig_service.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "json_lua" {
  filename = "audit/json.lua"
}

resource "aws_s3_object" "json_lua" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/json.lua"
  content    = data.local_file.json_lua.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "spylog_lua" {
  filename = "audit/spy_log.lua"
}

resource "aws_s3_object" "spylog_lua" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/spy_log.lua"
  content    = data.local_file.spylog_lua.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}

data "local_file" "ecs_healthcheck" {
  filename = "scripts/ecs_instance_health_check.py"
}

resource "aws_s3_object" "ecs_healthcheck" {
  bucket     = data.terraform_remote_state.management.outputs.config_bucket.id
  key        = "component/userhost/ecs_instance_health_check.py"
  content    = data.local_file.ecs_healthcheck.content
  kms_key_id = data.terraform_remote_state.management.outputs.config_bucket.cmk_arn
}