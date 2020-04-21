locals {
  cognito_endpoint = "https://cognito-idp.${data.aws_region.current}.amazonaws.com/${var.cognito_user_pool_id}"
  guacamole_port   = 8080
}
