variable "name_prefix" {
  type        = string
  description = "(Required) Name prefix for resources we create, defaults to repository name"
}
variable "region" {
  type        = string
  description = "(Required) The region to deploy into"
}
variable "chrome_image" {
  type = string
}
variable "guacd_image" {
  type = string
}
variable "jupyterhub_image" {
  type = string

}

