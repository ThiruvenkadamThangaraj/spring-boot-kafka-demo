# ========================================
# AWS Lambda Functions
# Serverless functions for event processing and automation
# ========================================

# Lambda Execution Role
resource "aws_iam_role" "lambda_execution" {
  name = "${var.project_name}-lambda-execution-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "lambda.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = {
    Name = "${var.project_name}-lambda-execution-role-${var.environment}"
  }
}

# Attach AWS managed policy for Lambda basic execution
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

# Lambda VPC access policy
resource "aws_iam_role_policy_attachment" "lambda_vpc" {
  role       = aws_iam_role.lambda_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaVPCAccessExecutionRole"
}

# Custom Lambda Policy
resource "aws_iam_role_policy" "lambda_custom" {
  name = "${var.project_name}-lambda-custom-policy-${var.environment}"
  role = aws_iam_role.lambda_execution.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "rds:DescribeDBInstances",
          "secretsmanager:GetSecretValue",
          "s3:GetObject",
          "s3:PutObject",
          "dynamodb:PutItem",
          "dynamodb:GetItem",
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sns:Publish"
        ]
        Resource = "*"
      }
    ]
  })
}

# CloudWatch Log Groups for Lambda
resource "aws_cloudwatch_log_group" "lambda_data_processor" {
  name              = "/aws/lambda/${var.project_name}-data-processor-${var.environment}"
  retention_in_days = 7
  
  tags = {
    Name = "${var.project_name}-lambda-data-processor-logs-${var.environment}"
  }
}

resource "aws_cloudwatch_log_group" "lambda_health_checker" {
  name              = "/aws/lambda/${var.project_name}-health-checker-${var.environment}"
  retention_in_days = 7
  
  tags = {
    Name = "${var.project_name}-lambda-health-checker-logs-${var.environment}"
  }
}

# Lambda Function: Data Processor
# This function processes data from microservices and performs transformations
resource "aws_lambda_function" "data_processor" {
  filename         = "${path.module}/../lambda/data-processor.zip"
  function_name    = "${var.project_name}-data-processor-${var.environment}"
  role             = aws_iam_role.lambda_execution.arn
  handler          = "index.handler"
  runtime          = var.lambda_runtime
  timeout          = var.lambda_timeout
  memory_size      = var.lambda_memory_size
  source_code_hash = fileexists("${path.module}/../lambda/data-processor.zip") ? filebase64sha256("${path.module}/../lambda/data-processor.zip") : null
  
  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }
  
  environment {
    variables = {
      ENVIRONMENT     = var.environment
      DB_SECRET_ARN   = aws_secretsmanager_secret.db_password.arn
      REGION          = var.aws_region
      LOG_LEVEL       = "INFO"
    }
  }
  
  tags = {
    Name = "${var.project_name}-data-processor-${var.environment}"
  }
  
  depends_on = [
    aws_cloudwatch_log_group.lambda_data_processor,
    aws_iam_role_policy_attachment.lambda_basic
  ]
}

# Lambda Function: Health Checker
# This function performs health checks on microservices
resource "aws_lambda_function" "health_checker" {
  filename         = "${path.module}/../lambda/health-checker.zip"
  function_name    = "${var.project_name}-health-checker-${var.environment}"
  role             = aws_iam_role.lambda_execution.arn
  handler          = "index.handler"
  runtime          = var.lambda_runtime
  timeout          = 30
  memory_size      = 128
  source_code_hash = fileexists("${path.module}/../lambda/health-checker.zip") ? filebase64sha256("${path.module}/../lambda/health-checker.zip") : null
  
  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }
  
  environment {
    variables = {
      ENVIRONMENT     = var.environment
      ALB_DNS_NAME    = aws_lb.main.dns_name
      SERVICES        = jsonencode([for k, v in var.microservices : v.name])
      LOG_LEVEL       = "INFO"
    }
  }
  
  tags = {
    Name = "${var.project_name}-health-checker-${var.environment}"
  }
  
  depends_on = [
    aws_cloudwatch_log_group.lambda_health_checker,
    aws_iam_role_policy_attachment.lambda_basic
  ]
}

# Lambda Security Group
resource "aws_security_group" "lambda" {
  name        = "${var.project_name}-lambda-sg-${var.environment}"
  description = "Security group for Lambda functions"
  vpc_id      = aws_vpc.main.id
  
  egress {
    description = "Allow all outbound"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  tags = {
    Name = "${var.project_name}-lambda-sg-${var.environment}"
  }
}

# EventBridge Rule to trigger health checker every 5 minutes
resource "aws_cloudwatch_event_rule" "health_check" {
  name                = "${var.project_name}-health-check-${var.environment}"
  description         = "Trigger health check Lambda every 5 minutes"
  schedule_expression = "rate(5 minutes)"
  
  tags = {
    Name = "${var.project_name}-health-check-rule-${var.environment}"
  }
}

# EventBridge Target
resource "aws_cloudwatch_event_target" "health_check" {
  rule      = aws_cloudwatch_event_rule.health_check.name
  target_id = "lambda"
  arn       = aws_lambda_function.health_checker.arn
}

# Lambda Permission for EventBridge
resource "aws_lambda_permission" "eventbridge" {
  statement_id  = "AllowExecutionFromEventBridge"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.health_checker.function_name
  principal     = "events.amazonaws.com"
  source_arn    = aws_cloudwatch_event_rule.health_check.arn
}

# Lambda Invocation URL (for data processor - can be invoked via API Gateway)
resource "aws_lambda_function_url" "data_processor" {
  function_name      = aws_lambda_function.data_processor.function_name
  authorization_type = "AWS_IAM"
  
  cors {
    allow_credentials = true
    allow_origins     = ["*"]
    allow_methods     = ["POST"]
    allow_headers     = ["*"]
    max_age           = 86400
  }
}

# Outputs
output "lambda_data_processor_arn" {
  description = "ARN of data processor Lambda function"
  value       = aws_lambda_function.data_processor.arn
}

output "lambda_health_checker_arn" {
  description = "ARN of health checker Lambda function"
  value       = aws_lambda_function.health_checker.arn
}

output "lambda_data_processor_url" {
  description = "Function URL of data processor Lambda"
  value       = aws_lambda_function_url.data_processor.function_url
}
