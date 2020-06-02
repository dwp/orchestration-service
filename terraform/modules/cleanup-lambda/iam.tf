resource "aws_iam_role" "cleanup_lambda_role" {
  name               = "${var.name_prefix}-role"
  assume_role_policy = data.aws_iam_policy_document.assume_role_cleanup_lambda
  tags               = var.common_tags
}

data "aws_iam_policy_document" "assume_role_cleanup_lambda" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      identifiers = ["lambda.amazonaws.com"]
      type        = "Service"
    }
  }
}

resource "aws_iam_role_policy_attachment" "cleanup_lambda_basic_policy_attach" {
  role = aws_iam_role.cleanup_lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "cleanup_lambda_dynamo_policy" {
  role = aws_iam_role.cleanup_lambda_role.id
  policy = data.aws_iam_policy_document.cleanup_lambda_dynamo_policy_document.json
}

data aws_iam_policy_document cleanup_lambda_dynamo_policy_document {
  statement {
    actions = [
      "dynamodb:Scan"
    ]
    resources = ["arn:aws:dynamodb:${var.region}:${var.account}:table/${var.table_name}"]
  }
}

resource "aws_iam_role_policy" "cleanup_lambda_logging_policy" {
  role = aws_iam_role.cleanup_lambda_role.id
  policy = data.aws_iam_policy_document.cleanup_lambda_logging_policy_document.json
}

data aws_iam_policy_document cleanup_lambda_logging_policy_document {
  statement {
    actions = [
      "logs:CreateLogGroup"
    ]
    resources = ["arn:aws:logs:${var.region}:${var.account}:*"]
  }
  statement {
    actions = [
      "logs:PutLogEvents",
      "logs:CreateLogStream"
    ]
    resources = ["arn:aws:dynamodb:${var.region}:${var.account}:log-group:${var.name_prefix}-cleanup-lambda:*"]
  }
}
