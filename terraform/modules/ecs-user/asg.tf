resource "aws_autoscaling_group" "user_host" {
  name                  = "${var.name_prefix}-asg"
  max_size              = var.auto_scaling.max_size
  min_size              = var.auto_scaling.min_size
  max_instance_lifetime = var.auto_scaling.max_instance_lifetime

  vpc_zone_identifier = var.vpc.aws_subnets_private[*].id

  protect_from_scale_in = true

  launch_template {
    id      = aws_launch_template.user_host.id
    version = "$Latest"
  }

  tags = local.autoscaling_tags

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_schedule" "scale_up_6am" {
  scheduled_action_name  = "scale_up_6am"
  min_size               = var.auto_scaling.min_size
  desired_capacity       = var.auto_scaling.min_size
  max_size               = var.auto_scaling.max_size
  recurrence             = "5 6 * * * "
  autoscaling_group_name = aws_autoscaling_group.user_host.name
}

resource "aws_autoscaling_schedule" "scale_down_midnight" {
  scheduled_action_name  = "scale_down_midnight"
  min_size               = 0
  max_size               = var.auto_scaling.max_size
  recurrence             = "5 0 * * * "
  autoscaling_group_name = aws_autoscaling_group.user_host.name
}

resource "aws_launch_template" "user_host" {
  name_prefix                          = "${var.name_prefix}-"
  image_id                             = var.ami_id
  instance_type                        = var.instance_type
  instance_initiated_shutdown_behavior = "terminate"
  tags                                 = merge(var.common_tags, { Name = "${var.name_prefix}-lt" })

  user_data = base64encode(templatefile("userdata.tpl", {

    cwa_metrics_collection_interval                  = local.cw_agent_metrics_collection_interval
    cwa_namespace                                    = local.cw_userhost_agent_namespace
    cwa_cpu_metrics_collection_interval              = local.cw_agent_cpu_metrics_collection_interval
    cwa_disk_measurement_metrics_collection_interval = local.cw_agent_disk_measurement_metrics_collection_interval
    cwa_disk_io_metrics_collection_interval          = local.cw_agent_disk_io_metrics_collection_interval
    cwa_mem_metrics_collection_interval              = local.cw_agent_mem_metrics_collection_interval
    cwa_netstat_metrics_collection_interval          = local.cw_agent_netstat_metrics_collection_interval
    cwa_log_group_name                               = local.cw_userhost_agent_log_group_name

    s3_scripts_bucket                                = var.s3_scripts_bucket

    s3_script_cloudwatch_shell                       = aws_s3_object.cloudwatch_agent_script.id
    s3_script_userhost_config_hcs                    = aws_s3_object.userhost_config_hcs_script.id
    s3_script_userhost_logging                       = aws_s3_object.userhost_logging_script.id
    s3_script_userhost_logrotate                     = aws_s3_object.userhost_logrotate_script.id
    s3_script_sysdig_service                         = aws_s3_object.sysdig_service.id
    s3_script_json_lua                               = aws_s3_object.json_lua.id
    s3_script_spylog_lua                             = aws_s3_object.spylog_lua.id
    s3_script_ecs_instance_health_check              = aws_s3_object.ecs_healthcheck.id
    
    s3_script_hash_cloudwatch_shell                  = md5(data.local_file.cloudwatch_agent_script.content)
    s3_script_hash_userhost_config_hcs               = md5(data.local_file.userhost_config_hcs_script.content)
    s3_script_hash_userhost_logging                  = md5(data.local_file.userhost_logging_script.content)
    s3_script_hash_userhost_logrotate                = md5(data.local_file.userhost_logrotate_script.content)
    s3_script_hash_sysdig_service                    = md5(data.local_file.sysdig_service.content)
    s3_script_hash_json_lua                          = md5(data.local_file.json_lua.content)
    s3_script_hash_spy_log_lua                       = md5(data.local_file.spylog_lua.content)
    s3_script_hash_ecs_instance_health_check         = md5(data.local_file.ecs_healthcheck.content)
    
    proxy_host                                       = var.proxy_host
    proxy_port                                       = var.proxy_port
    hcs_environment                                  = local.hcs_environment[local.environment]
    install_tenable                                  = var.install_tenable
    install_trend                                    = var.install_trend
    install_tanium                                   = var.install_tanium
    tanium_server_1                                  = var.tanium_server_1
    tanium_server_2                                  = var.tanium_server_2
    tanium_env                                       = var.tanium_env
    tanium_port                                      = var.tanium_port_1
    tanium_log_level                                 = var.tanium_log_level
    tenant                                           = var.tenant
    tenantid                                         = var.tenantid
    token                                            = var.token
    policyid                                         = var.policyid
  }))

  block_device_mappings {
    device_name = "/dev/sda1"

    ebs {
      delete_on_termination = true
      encrypted             = true
      volume_size           = 128
    }
  }

  block_device_mappings {
    device_name = "/dev/sda1"
    no_device   = true
  }

  iam_instance_profile {
    arn = aws_iam_instance_profile.user_host.arn
  }

  tag_specifications {
    resource_type = "instance"
    tags          = merge(var.common_tags, { Name = var.name_prefix, "SSMEnabled" = local.userhost_asg_ssmenabled[local.environment], "Persistence" = "True" })
  }

  tag_specifications {
    resource_type = "volume"
    tags          = merge(var.common_tags, { Name = var.name_prefix })
  }

  network_interfaces {
    associate_public_ip_address = false
    delete_on_termination       = true

    security_groups = [
      aws_security_group.user_host.id
    ]
  }

  lifecycle {
    create_before_destroy = true
  }
}
