variable "name_prefix" {
  type        = string
  description = "(Required) Name prefix for resources we create, defaults to repository name"
}

variable "common_tags" {
  type        = map(string)
  description = "(Required) common tags to apply to aws resources"
}

variable "ami_id" {
  type = string
}

variable "instance_type" {
  type = string
}

variable "ap_vpce_sg" {
  type = string
}

variable "vpc" {
  type = object({
    id                   = string
    aws_subnets_private  = list(any)
    interface_vpce_sg_id = string
    s3_prefix_list_id    = string
  })
}

variable "auto_scaling" {
  type = object({
    min_size              = number
    max_size              = number
    max_instance_lifetime = number
  })
}

variable frontend_alb_sg_id {
  type        = string
  description = "(Required) Source ALB Security group"
}

variable guacamole_port {
  type        = number
  description = "Port used for listening by the user guacamole container"
}

variable management_account {
  type        = string
  description = "(Required) - The mgmt account where images reside"
}

variable livy_port {
  type        = number
  description = "Port that EMR livy listens on"
  default     = 8998
}

variable emr_sg_id {
  type        = string
  description = "Security Group id of EMR cluster"
}

variable livy_proxy_sg_id {
  type        = string
  description = "Security Group id of Livy"
}

variable pushgateway_sg_id {
  type        = string
  description = "Security Group ID of the Pushgateway"
}

variable pushgateway_port {
  type        = string
  description = "Port that the Pushgateway listens on "
  default     = 9091
}

variable github_proxy_vpce_sg_id {
  type = string
}

variable hiveserver2_port {
  type        = string
  description = "Port that the Hive Server listens on"
  default     = 10000
}

variable "s3_packages" {
  type = object({
    bucket     = string
    key_prefix = string
    cmk_arn    = string
  })
}

variable "scaling" {
  type = object({
    max  = number
    step = number
  })
}

variable "proxy_port" {
  description = "proxy port"
  type        = string
  default     = "3128"
}

variable "proxy_host" {
  description = "proxy host"
  type        = string
}

variable "region" {
  type        = string
  description = "(Required) The region to deploy into"
  default     = "eu-west-2"
}

variable "account" {
  type        = string
  description = "(Required) The account id of the account we are deploying into"
}

variable "tanium_server_1" {
  description = "tanium server 1"
  type        = string
}

variable "tanium_server_2" {
  description = "tanium server 2"
  type        = string
}

variable "tanium_port_1" {
  description = "tanium port 1"
  type        = string
  default     = "16563"
}

variable "tanium_port_2" {
  description = "tanium port 2"
  type        = string
  default     = "16555"
}

variable "tanium_env" {
  description = "tanium environment"
  type        = string
}

variable "tanium_log_level" {
  description = "tanium log level"
  type        = string
  default     = "41"
}

variable "install_tenable" {
  description = "Install Tenable"
  type        = bool
}

variable "install_trend" {
  description = "Install Trend"
  type        = bool
}

variable "install_tanium" {
  description = "Install Tanium"
  type        = bool
}

variable "tenantid" {
  description = "Trend tenant ID"
  type        = string
}

variable "token" {
  description = "Trend token"
  type        = string
}

variable "tenant" {
  description = "Trend tenant"
  type        = string
}

variable "policyid" {
  description = "Trend Policy ID"
  type        = string
}

variable "tanium_prefix" {
  description = "Tanium prefix"
  type        = list(string)
}

variable "config_bucket_id" {
  description = "Config bucket ID"
  type        = string
}

variable "config_bucket_cmk_arn" {
  description = "Config bucket cmk arn"
  type        = string
}

variable "s3_scripts_bucket" {
  description = "S3 Scripts bucket"
  type        = string
}