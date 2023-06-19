locals {

  environment = terraform.workspace == "default" ? "development" : terraform.workspace

  # Configured as per Tagging doc requirements https://engineering.dwp.gov.uk/policies/hcs-cloud-hosting-policies/resource-identification-tagging/
  hcs_environment = {
    development    = "Dev"
    qa             = "Test"
    integration    = "Stage"
    preprod        = "Stage"
    production     = "Production"
    management     = "SP_Tooling"
    management-dev = "DT_Tooling"
  }

  log_group                                   = "/aws/ecs/${var.name_prefix}/user-container-logs/"
  cw_userhost_agent_namespace                 = "/aws/ecs/${var.name_prefix}/userhost-instance-logs/"
  cw_userhost_agent_log_group_name            = "/aws/ecs/${var.name_prefix}/userhost-instance-logs" 

  cloudwatch_log_group                             = local.log_group

  cw_agent_metrics_collection_interval                  = 60
  cw_agent_cpu_metrics_collection_interval              = 60
  cw_agent_disk_measurement_metrics_collection_interval = 60
  cw_agent_disk_io_metrics_collection_interval          = 60
  cw_agent_mem_metrics_collection_interval              = 60
  cw_agent_netstat_metrics_collection_interval          = 60

  userhost_asg_autoshutdown = {
    development = "False"
    qa          = "False"
    integration = "False"
    preprod     = "False"
    production  = "False"
  }

  userhost_asg_ssmenabled = {
    development = "True"
    qa          = "True"
    integration = "True"
    preprod     = "True"
    production  = "True"
  }
  

  /* cloudwatch_agent_config_file = templatefile("${path.module}/templates/cloudwatch_agent.json",
    {
      cloudwatch_log_group                             = local.log_group
      cwa_namespace                                    = local.cw_userhost_agent_namespace
      cwa_log_group_name                               = "${local.cw_userhost_agent_namespace}-${local.environment}"
      cwa_metrics_collection_interval                  = local.cw_agent_metrics_collection_interval
      cwa_cpu_metrics_collection_interval              = local.cw_agent_cpu_metrics_collection_interval
      cwa_disk_measurement_metrics_collection_interval = local.cw_agent_disk_measurement_metrics_collection_interval
      cwa_disk_io_metrics_collection_interval          = local.cw_agent_disk_io_metrics_collection_interval
      cwa_mem_metrics_collection_interval              = local.cw_agent_mem_metrics_collection_interval
      cwa_netstat_metrics_collection_interval          = local.cw_agent_netstat_metrics_collection_interval
    }
  )
  autoscaling_tags = [
    for key, value in var.common_tags :
    {
      key                 = key
      value               = value
      propagate_at_launch = true
    }
  ] */

}
