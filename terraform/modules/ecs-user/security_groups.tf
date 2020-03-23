resource "aws_security_group" "user_host" {
  vpc_id = var.vpc.id
}
