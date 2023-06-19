#!/bin/bash

echo ECS_CLUSTER=${var.name_prefix} >> /etc/ecs/ecs.config
echo ECS_AWSVPC_BLOCK_IMDS=true >> /etc/ecs/ecs.config

echo "Creating directories"
mkdir -p /opt/userhost
mkdir -p /opt/userhost/config

# Force LC update when any of these files are changed
echo "${s3_script_hash_cloudwatch_shell}" > /dev/null
echo "${s3_script_hash_userhost_config_hcs}" > /dev/null
echo "${s3_script_hash_userhost_logging}" > /dev/null
echo "${s3_script_hash_userhost_logrotate}" > /dev/null
echo "${s3_script_hash_sysdig_service}" > /dev/null
echo "${s3_script_hash_json_lua}" > /dev/null
echo "${s3_script_hash_spy_log_lua}" > /dev/null
echo "${s3_script_hash_ecs_instance_health_check}" > /dev/null

echo "Downloading startup scripts"
S3_CLOUDWATCH_SHELL="s3://${s3_scripts_bucket}/${s3_script_cloudwatch_shell}"
S3_USERHOST_CONFIG_HCS="s3://${s3_scripts_bucket}/${s3_script_userhost_config_hcs}"
S3_USERHOST_LOGGING="s3://${s3_scripts_bucket}/${s3_script_userhost_logging}"
S3_USERHOST_LOGROTATE="s3://${s3_scripts_bucket}/${s3_script_userhost_logrotate}"
S3_SYSDIG_SERVICE="s3://${s3_scripts_bucket}/${s3_script_sysdig_service}"
S3_JSON_LUA="s3://${s3_scripts_bucket}/${s3_script_json_lua}"
S3_SPYLOG_LUA="s3://${s3_scripts_bucket}/${s3_script_spylog_lua}"
S3_ECS_INSTANCE_HEALTH_CHECK="s3://${s3_scripts_bucket}/${s3_script_ecs_instance_health_check}"

$(which aws) s3 cp "$S3_CLOUDWATCH_SHELL" /opt/userhost/cloudwatch.sh
$(which aws) s3 cp "$S3_USERHOST_CONFIG_HCS" /usr/local/src/config_hcs.sh
$(which aws) s3 cp "$S3_USERHOST_LOGGING" /usr/local/src/logging.sh
$(which aws) s3 cp "$S3_USERHOST_LOGROTATE" /etc/logrotate.d/userhost/userhost.logrotate
$(which aws) s3 cp "$S3_SYSDIG_SERVICE" /etc/systemd/system/sysdig.service
$(which aws) s3 cp "$S3_JSON_LUA" /usr/share/sysdig/chisels/json.lua
$(which aws) s3 cp "$S3_SPYLOG_LUA" /usr/share/sysdig/chisels/spy_log.lua
$(which aws) s3 cp "$S3_ECS_INSTANCE_HEALTH_CHECK" /usr/local/src/ecs_instance_health_check.py


sed -i '/^\[Service\]/a MountFlags=shared' /usr/lib/systemd/system/docker.service
systemctl daemon-reload
systemctl enable sysdig
systemctl start sysdig

# grab R packages from S3
export AWS_DEFAULT_REGION=${data.aws_region.current.name}
aws s3 sync s3://${var.s3_packages.bucket}/${var.s3_packages.key_prefix}/ /opt/dataworks/packages/r/

# rename ec2 instance to be unique
export AWS_DEFAULT_REGION=${data.aws_region.current.name}
export INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
UUID=$(dbus-uuidgen | cut -c 1-8)
export HOSTNAME=${var.name_prefix}-user-host-$UUID
hostnamectl set-hostname $HOSTNAME
aws ec2 create-tags --resources $INSTANCE_ID --tags Key=Name,Value=$HOSTNAME

# Configure swap
dd if=/dev/zero of=/swapfile bs=128M count=256
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile

# Start long-running ECS instance health check as a background task
export REGION=${data.aws_region.current.name}
export INSTANCE_ID=$(curl http://169.254.169.254/latest/meta-data/instance-id)
nohup python /usr/local/src/ecs_instance_health_check.py &

# extend relevant vg to allow docker to extract images
lvextend -l 75%FREE /dev/rootvg/varvol
xfs_growfs /dev/mapper/rootvg-varvol
lvextend -l 100%FREE /dev/rootvg/rootvol
xfs_growfs /dev/mapper/rootvg-rootvol

# accept anything that wasn't specifically covered
# temp change until we configure iptables to mirror sg
iptables -P INPUT ACCEPT
iptables -P FORWARD ACCEPT
iptables -P OUTPUT ACCEPT
# flushing all rules
iptables -F
# presisting rules for next boot
service iptables save


echo "Setup cloudwatch logs"
chmod u+x /opt/userhost/cloudwatch.sh
/opt/userhost/cloudwatch.sh \
    "${cwa_metrics_collection_interval}" "${cwa_namespace}" "${cwa_cpu_metrics_collection_interval}" \
    "${cwa_disk_measurement_metrics_collection_interval}" "${cwa_disk_io_metrics_collection_interval}" \
    "${cwa_mem_metrics_collection_interval}" "${cwa_netstat_metrics_collection_interval}" "${cwa_log_group_name}" \
    "$AWS_DEFAULT_REGION"

# Logging and HCS config
echo "Creating directories"
mkdir -p /var/log/userhost
echo "Setup hcs pre-requisites"
chmod u+x /usr/local/src/config_hcs.sh
/usr/local/src/config_hcs.sh \
    "${hcs_environment}" "${proxy_host}" \
    "${proxy_port}" "${tanium_server_1}" "${tanium_server_2}" "${tanium_env}" \
    "${tanium_port_1}" "${tanium_log_level}" "${install_tenable}" "${install_trend}" \
    "${install_tanium}" "${tenantid}" "${token}" "${policyid}" "${tenant}"
echo "Creating userhost user"
useradd userhost -m
echo "Changing permissions"
chown userhost:userhost -R  /var/log/userhost
