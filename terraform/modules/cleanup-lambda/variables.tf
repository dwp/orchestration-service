variable "name_prefix" {
  type        = string
  description = "(Required) Name prefix for resources created"
}

variable "common_tags" {
  type        = map(string)
  description = "(Required) common tags to apply to aws resources"
}

variable "region" {
  type        = string
  description = "(Required) The region to deploy into"
}

variable "account" {
  type        = string
  description = "(Required) The account number of the environment"
}

variable "table_name" {
  type        = string
  description = "(Required) The DynamoDB active user table name"
}
