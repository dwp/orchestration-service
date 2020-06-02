resource "aws_lambda_function" "cleanup_lambda" {
  filename         = data.archive_file.cleanup_lambda_zip.output_path
  function_name    = var.name_prefix
  role             = aws_iam_role.cleanup_lambda_role.arn
  handler          = "lambda_function.lambda_handler"
  runtime          = "python3.8"
  source_code_hash = data.archive_file.cleanup_lambda_zip.output_base64sha256
  tags             = merge(var.common_tags, { Name = var.name_prefix, "ProtectSensitiveData" = "True" })
  environment {
    variables = {
      TABLE_NAME = var.table_name
    }
  }
}
