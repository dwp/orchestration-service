data "aws_iam_policy_document" "task_definition_role" {
  statement {
    actions = [
      "ec2:DescribeImages",
    ]

    resources = [
      "*",
    ]
  }
}

data "aws_iam_policy_document" "ui_task_role" {
  statement {
    actions = [
      "ec2:DescribeImages",
    ]

    resources = [
      "*",
    ]
  }
}

data "aws_iam_policy_document" "ui_task_execution_role" {
  statement {
    actions = [
      "ecr:GetAuthorizationToken",
      "ecr:BatchCheckLayerAvailability",
      "ecr:GetDownloadUrlForLayer",
      "ecr:BatchGetImage",
      "logs:CreateLogStream",
      "logs:PutLogEvents"
    ]

    resources = [
      "*",
    ]
  }
}

data "aws_iam_policy_document" "ecs_ui_service_role" {
  statement {
    actions = [
      "ec2:DescribeImages",
    ]

    resources = [
      "*",
    ]
  }
}


data "aws_iam_policy_document" "assume_role_ec2" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      identifiers = ["ec2.amazonaws.com"]
      type        = "Service"
    }
  }
}