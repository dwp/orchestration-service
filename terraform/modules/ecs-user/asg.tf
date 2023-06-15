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

data "template_cloudinit_config" "ecs_config" {
  gzip          = true
  base64_encode = true

  part {
    content_type = "text/cloud-config"

    content = <<EOF
write_files:
  - encoding: b64
    content: ${base64encode(local.cloudwatch_agent_config_file)}
    owner: root:root
    path: /etc/amazon/amazon-cloudwatch-agent/amazon-cloudwatch-agent.d/sysdig.json
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/audit/sysdig.service")}
    owner: root:root
    path: /etc/systemd/system/sysdig.service
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/audit/json.lua")}
    owner: root:root
    path: /usr/share/sysdig/chisels/json.lua
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/audit/spy_log.lua")}
    owner: root:root
    path: /usr/share/sysdig/chisels/spy_log.lua
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/scripts/ecs_instance_health_check.py")}
    owner: root:root
    path: /usr/local/src/ecs_instance_health_check.py
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/files/userhost/userhost_config_hcs.sh")}
    owner: root:root
    path: /usr/local/src/config_hcs.sh
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/files/userhost/userhost_logging.sh")}
    owner: root:root
    path: /usr/local/src/logging.sh
    permissions: '0644'
  - encoding: b64
    content: ${filebase64("${path.module}/files/userhost/userhost.logrotate")}
    owner: root:root
    path: /etc/logrotate.d/userhost/userhost.logrotate
    permissions: '0644'
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash
    sed -i '/^\[Service\]/a MountFlags=shared' /usr/lib/systemd/system/docker.service
    systemctl daemon-reload
    systemctl enable sysdig
    systemctl start sysdig
    systemctl enable amazon-cloudwatch-agent
    systemctl start amazon-cloudwatch-agent
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash
    echo ECS_CLUSTER=${var.name_prefix} >> /etc/ecs/ecs.config
    echo ECS_AWSVPC_BLOCK_IMDS=true >> /etc/ecs/ecs.config
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # grab R packages from S3
    export AWS_DEFAULT_REGION=${data.aws_region.current.name}
    aws s3 sync s3://${var.s3_packages.bucket}/${var.s3_packages.key_prefix}/ /opt/dataworks/packages/r/
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # rename ec2 instance to be unique
    export AWS_DEFAULT_REGION=${data.aws_region.current.name}
    export INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
    UUID=$(dbus-uuidgen | cut -c 1-8)
    export HOSTNAME=${var.name_prefix}-user-host-$UUID
    hostnamectl set-hostname $HOSTNAME
    aws ec2 create-tags --resources $INSTANCE_ID --tags Key=Name,Value=$HOSTNAME
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # Configure swap
    dd if=/dev/zero of=/swapfile bs=128M count=256
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # Start long-running ECS instance health check as a background task
    export REGION=${data.aws_region.current.name}
    export INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
    nohup python /usr/local/src/ecs_instance_health_check.py &
EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # extend relevant vg to allow docker to extract images
    lvextend -l 75%FREE /dev/rootvg/varvol
    xfs_growfs /dev/mapper/rootvg-varvol

    lvextend -l 100%FREE /dev/rootvg/rootvol
    xfs_growfs /dev/mapper/rootvg-rootvol
EOF
  }
  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # accept anything that wasn't specifically covered
    # temp change until we configure iptables to mirror sg
    iptables -P INPUT ACCEPT
    iptables -P FORWARD ACCEPT
    iptables -P OUTPUT ACCEPT

    # flushing all rules
    iptables -F

    # presisting rules for next boot
    service iptables save

EOF
  }

  part {
    content_type = "text/x-shellscript"
    content      = <<EOF
    #!/bin/bash

    # Logging and HCS config

    echo "Creating directories"
    mkdir -p /var/log/userhost

    echo "Setup hcs pre-requisites"
    chmod u+x /usr/local/src/config_hcs.sh
    /usr/local/src/config_hcs.sh ${local.hcs_environment[local.environment]} ${var.proxy_host} ${var.proxy_port} ${var.tanium_server_1} ${var.tanium_server_2} ${var.tanium_env} ${var.tanium_port_1} ${var.tanium_log_level} ${var.install_tenable} ${var.install_trend} ${var.install_tanium} ${var.tenantid} ${var.token} ${var.policyid} ${var.tenant}

    echo "Creating userhost user"
    useradd userhost -m

    echo "Changing permissions"
    chown userhost:userhost -R  /var/log/userhost

EOF
  }
}

resource "aws_launch_template" "user_host" {
  name_prefix                          = "${var.name_prefix}-"
  image_id                             = var.ami_id
  instance_type                        = var.instance_type
  instance_initiated_shutdown_behavior = "terminate"
  tags                                 = merge(var.common_tags, { Name = "${var.name_prefix}-lt" })

  user_data = data.template_cloudinit_config.ecs_config.rendered

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
