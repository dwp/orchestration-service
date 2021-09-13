# Permits connection from analytical-env to AP frontend
resource "aws_vpc_endpoint" "ap_frontend_vpce" {
  vpc_id              = var.vpc.id
  service_name        = var.ap_frontend_vpce
  private_dns_enabled = true
  security_group_ids  = [aws_security_group.ap_frontend_vpce_sg.id]
  subnet_ids          = var.vpc.aws_subnets_private[*].id
  vpc_endpoint_type   = "Interface"
}

resource "aws_route53_zone" "ap_zone" {
  name    = "ap.${var.parent_domain_name}"
  comment = "Managed by Terraform"

  vpc {
    vpc_id = var.vpc.id
  }
}

resource "aws_route53_record" "ap_vpce_custom_dns" {
  zone_id = aws_route53_zone.ap_zone.id
  name    = "*.${aws_route53_zone.ap_zone.name}"
  type      = "CNAME"
  ttl       = "300"
  records   = [aws_vpc_endpoint.ap_frontend_vpce.dns_entry[0]["dns_name"]]
}
