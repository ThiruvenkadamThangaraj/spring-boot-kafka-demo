# ========================================
# AWS Step Functions State Machine
# Orchestrates complex workflows across microservices
# ========================================

# Step Functions Execution Role
resource "aws_iam_role" "step_functions" {
  name = "${var.project_name}-step-functions-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "states.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = {
    Name = "${var.project_name}-step-functions-role-${var.environment}"
  }
}

# Step Functions Policy
resource "aws_iam_role_policy" "step_functions" {
  name = "${var.project_name}-step-functions-policy-${var.environment}"
  role = aws_iam_role.step_functions.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Action = [
          "lambda:InvokeFunction",
          "ecs:RunTask",
          "ecs:StopTask",
          "ecs:DescribeTasks",
          "iam:PassRole",
          "sns:Publish",
          "sqs:SendMessage",
          "logs:CreateLogDelivery",
          "logs:GetLogDelivery",
          "logs:UpdateLogDelivery",
          "logs:DeleteLogDelivery",
          "logs:ListLogDeliveries",
          "logs:PutResourcePolicy",
          "logs:DescribeResourcePolicies",
          "logs:DescribeLogGroups"
        ]
        Resource = "*"
      }
    ]
  })
}

# CloudWatch Log Group for Step Functions
resource "aws_cloudwatch_log_group" "step_functions" {
  name              = "/aws/stepfunctions/${var.project_name}-${var.environment}"
  retention_in_days = 7
  
  tags = {
    Name = "${var.project_name}-step-functions-logs-${var.environment}"
  }
}

# Step Functions State Machine: Microservices Workflow
resource "aws_sfn_state_machine" "microservices_workflow" {
  name     = "${var.project_name}-workflow-${var.environment}"
  role_arn = aws_iam_role.step_functions.arn
  
  definition = jsonencode({
    Comment = "Orchestrates microservices workflow for data processing"
    StartAt = "SamplingService"
    States = {
      SamplingService = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.data_processor.arn
          Payload = {
            "service" = "sampling"
            "action"  = "process"
            "data.$"  = "$.input"
          }
        }
        ResultPath = "$.samplingResult"
        Next       = "EvaluationService"
        Retry = [
          {
            ErrorEquals     = ["Lambda.ServiceException", "Lambda.TooManyRequestsException"]
            IntervalSeconds = 2
            MaxAttempts     = 3
            BackoffRate     = 2
          }
        ]
        Catch = [
          {
            ErrorEquals = ["States.ALL"]
            Next        = "HandleError"
            ResultPath  = "$.error"
          }
        ]
      }
      
      EvaluationService = {
        Type     = "Task"
        Resource = "arn:aws:states:::lambda:invoke"
        Parameters = {
          FunctionName = aws_lambda_function.data_processor.arn
          Payload = {
            "service"          = "evaluation"
            "action"           = "evaluate"
            "samplingData.$"   = "$.samplingResult.Payload"
          }
        }
        ResultPath = "$.evaluationResult"
        Next       = "CheckEvaluationResult"
      }
      
      CheckEvaluationResult = {
        Type = "Choice"
        Choices = [
          {
            Variable      = "$.evaluationResult.Payload.status"
            StringEquals  = "pass"
            Next          = "Success"
          },
          {
            Variable      = "$.evaluationResult.Payload.status"
            StringEquals  = "fail"
            Next          = "ParallelRemediation"
          }
        ]
        Default = "HandleError"
      }
      
      ParallelRemediation = {
        Type = "Parallel"
        Branches = [
          {
            StartAt = "EvidenceService"
            States = {
              EvidenceService = {
                Type     = "Task"
                Resource = "arn:aws:states:::lambda:invoke"
                Parameters = {
                  FunctionName = aws_lambda_function.data_processor.arn
                  Payload = {
                    "service"           = "evidence"
                    "action"            = "collect"
                    "evaluationData.$"  = "$.evaluationResult.Payload"
                  }
                }
                End = true
              }
            }
          },
          {
            StartAt = "RemediationService"
            States = {
              RemediationService = {
                Type     = "Task"
                Resource = "arn:aws:states:::lambda:invoke"
                Parameters = {
                  FunctionName = aws_lambda_function.data_processor.arn
                  Payload = {
                    "service"           = "remediation"
                    "action"            = "remediate"
                    "evaluationData.$"  = "$.evaluationResult.Payload"
                  }
                }
                End = true
              }
            }
          },
          {
            StartAt = "JiraService"
            States = {
              JiraService = {
                Type     = "Task"
                Resource = "arn:aws:states:::lambda:invoke"
                Parameters = {
                  FunctionName = aws_lambda_function.data_processor.arn
                  Payload = {
                    "service"           = "jira"
                    "action"            = "createTicket"
                    "evaluationData.$"  = "$.evaluationResult.Payload"
                  }
                }
                End = true
              }
            }
          }
        ]
        ResultPath = "$.remediationResults"
        Next       = "Success"
      }
      
      Success = {
        Type = "Succeed"
      }
      
      HandleError = {
        Type     = "Task"
        Resource = "arn:aws:states:::sns:publish"
        Parameters = {
          TopicArn = aws_sns_topic.alerts.arn
          Subject  = "Step Functions Workflow Error"
          Message = {
            "error.$" = "$.error"
          }
        }
        Next = "Fail"
      }
      
      Fail = {
        Type  = "Fail"
        Error = "WorkflowFailed"
        Cause = "An error occurred in the microservices workflow"
      }
    }
  })
  
  logging_configuration {
    log_destination        = "${aws_cloudwatch_log_group.step_functions.arn}:*"
    include_execution_data = true
    level                  = "ALL"
  }
  
  tracing_configuration {
    enabled = true
  }
  
  tags = {
    Name = "${var.project_name}-workflow-${var.environment}"
  }
}

# SNS Topic for Alerts
resource "aws_sns_topic" "alerts" {
  name = "${var.project_name}-alerts-${var.environment}"
  
  tags = {
    Name = "${var.project_name}-alerts-${var.environment}"
  }
}

# SNS Topic Subscription (Email)
resource "aws_sns_topic_subscription" "alerts_email" {
  topic_arn = aws_sns_topic.alerts.arn
  protocol  = "email"
  endpoint  = "your-email@example.com" # Replace with your email
}

# EventBridge Rule to trigger Step Functions
resource "aws_cloudwatch_event_rule" "workflow_trigger" {
  name                = "${var.project_name}-workflow-trigger-${var.environment}"
  description         = "Trigger Step Functions workflow"
  schedule_expression = "rate(10 minutes)"
  is_enabled          = false # Set to true to enable scheduled execution
  
  tags = {
    Name = "${var.project_name}-workflow-trigger-${var.environment}"
  }
}

# EventBridge Target for Step Functions
resource "aws_cloudwatch_event_target" "workflow_trigger" {
  rule     = aws_cloudwatch_event_rule.workflow_trigger.name
  target_id = "step-functions"
  arn      = aws_sfn_state_machine.microservices_workflow.arn
  role_arn = aws_iam_role.eventbridge_step_functions.arn
  
  input = jsonencode({
    input = {
      source      = "scheduled-event"
      requestId   = "auto-generated"
      timestamp   = "$$.Execution.StartTime"
    }
  })
}

# IAM Role for EventBridge to invoke Step Functions
resource "aws_iam_role" "eventbridge_step_functions" {
  name = "${var.project_name}-eventbridge-sfn-role-${var.environment}"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "events.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      }
    ]
  })
  
  tags = {
    Name = "${var.project_name}-eventbridge-sfn-role-${var.environment}"
  }
}

resource "aws_iam_role_policy" "eventbridge_step_functions" {
  name = "${var.project_name}-eventbridge-sfn-policy-${var.environment}"
  role = aws_iam_role.eventbridge_step_functions.id
  
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = "states:StartExecution"
        Resource = aws_sfn_state_machine.microservices_workflow.arn
      }
    ]
  })
}

# Outputs
output "step_functions_arn" {
  description = "ARN of Step Functions state machine"
  value       = aws_sfn_state_machine.microservices_workflow.arn
}

output "step_functions_console_url" {
  description = "AWS Console URL for Step Functions"
  value       = "https://console.aws.amazon.com/states/home?region=${var.aws_region}#/statemachines/view/${aws_sfn_state_machine.microservices_workflow.arn}"
}

output "sns_topic_arn" {
  description = "ARN of SNS alerts topic"
  value       = aws_sns_topic.alerts.arn
}
