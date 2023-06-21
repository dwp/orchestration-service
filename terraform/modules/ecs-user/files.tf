data "local_file" "cloudwatch_agent_script" {
  filename = "${path.module}/files/userhost/cloudwatch_agent.sh"
}

resource "aws_s3_object" "cloudwatch_agent_script" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/cloudwatch_agent.sh"
  content    = data.local_file.cloudwatch_agent_script.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "userhost_config_hcs_script" {
  filename = "${path.module}/files/userhost/userhost_config_hcs.sh"
}

resource "aws_s3_object" "userhost_config_hcs_script" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/userhost_config_hcs.sh"
  content    = data.local_file.userhost_config_hcs_script.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "userhost_logging_script" {
  filename = "${path.module}/files/userhost/userhost_logging.sh"
}

resource "aws_s3_object" "userhost_logging_script" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/userhost_logging_script.sh"
  content    = data.local_file.userhost_logging_script.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "userhost_logrotate_script" {
  filename = "${path.module}/files/userhost/userhost.logrotate"
}

resource "aws_s3_object" "userhost_logrotate_script" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/userhost.logrotate"
  content    = data.local_file.userhost_logrotate_script.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "sysdig_service" {
  filename = "${path.module}/audit/sysdig.service"
}

resource "aws_s3_object" "sysdig_service" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/sysdig.service"
  content    = data.local_file.sysdig_service.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "json_lua" {
  filename = "${path.module}/audit/json.lua"
}

resource "aws_s3_object" "json_lua" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/json.lua"
  content    = data.local_file.json_lua.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "spylog_lua" {
  filename = "${path.module}/audit/spy_log.lua"
}

resource "aws_s3_object" "spylog_lua" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/spy_log.lua"
  content    = data.local_file.spylog_lua.content
  kms_key_id = var.config_bucket_cmk_arn
}

data "local_file" "ecs_healthcheck" {
  filename = "${path.module}/scripts/ecs_instance_health_check.py"
}

resource "aws_s3_object" "ecs_healthcheck" {
  bucket     = var.config_bucket_id
  key        = "component/userhost/ecs_instance_health_check.py"
  content    = data.local_file.ecs_healthcheck.content
  kms_key_id = var.config_bucket_cmk_arn
}