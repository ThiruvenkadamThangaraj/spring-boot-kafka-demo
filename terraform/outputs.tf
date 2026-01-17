# ========================================
# Terraform Outputs
# Export important values for reference
# ========================================

output "region" {
  description = "AWS region"
  value       = var.aws_region
}

output "environment" {
  description = "Environment name"
  value       = var.environment
}

output "account_id" {
  description = "AWS Account ID"
  value       = data.aws_caller_identity.current.account_id
}

# Network Outputs
output "vpc_cidr" {
  description = "VPC CIDR block"
  value       = aws_vpc.main.cidr_block
}

output "availability_zones" {
  description = "Availability zones used"
  value       = aws_subnet.public[*].availability_zone
}

# Load Balancer Outputs
output "load_balancer_url" {
  description = "Load Balancer URL"
  value       = "http://${aws_lb.main.dns_name}"
}

output "load_balancer_arn" {
  description = "Load Balancer ARN"
  value       = aws_lb.main.arn
}

# Database Outputs
output "database_endpoint" {
  description = "RDS database endpoint"
  value       = aws_db_instance.main.endpoint
}

output "database_name" {
  description = "Database name"
  value       = var.db_name
}

# Container Registry Outputs
output "ecr_login_command" {
  description = "Command to login to ECR"
  value       = "aws ecr get-login-password --region ${var.aws_region} | docker login --username AWS --password-stdin ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com"
}

# Service URLs
output "microservice_endpoints" {
  description = "Endpoints for each microservice"
  value = {
    for k, v in var.microservices :
    k => "http://${aws_lb.main.dns_name}/${v.name}"
  }
}

# Lambda Outputs
output "lambda_functions" {
  description = "Lambda function ARNs"
  value = {
    data_processor = aws_lambda_function.data_processor.arn
    health_checker = aws_lambda_function.health_checker.arn
  }
}

# Step Functions Outputs
output "workflow_arn" {
  description = "Step Functions workflow ARN"
  value       = aws_sfn_state_machine.microservices_workflow.arn
}

output "workflow_url" {
  description = "Step Functions workflow console URL"
  value       = "https://${var.aws_region}.console.aws.amazon.com/states/home?region=${var.aws_region}#/statemachines/view/${aws_sfn_state_machine.microservices_workflow.arn}"
}

# Deployment Instructions
output "deployment_instructions" {
  description = "Next steps for deployment"
  value = <<-EOT
    ========================================
    AWS Infrastructure Created Successfully!
    ========================================
    
    Next Steps:
    
    1. Login to ECR:
       ${self.ecr_login_command}
    
    2. Build and push Docker images:
       cd evaluation-service && docker build -t ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/microservices-evaluation-service-${var.environment}:latest .
       docker push ${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/microservices-evaluation-service-${var.environment}:latest
    
    3. Access your services:
       Load Balancer: http://${aws_lb.main.dns_name}
       
    4. Monitor your infrastructure:
       ECS Cluster: https://console.aws.amazon.com/ecs/home?region=${var.aws_region}#/clusters/${aws_ecs_cluster.main.name}
       RDS: https://console.aws.amazon.com/rds/home?region=${var.aws_region}
       Lambda: https://console.aws.amazon.com/lambda/home?region=${var.aws_region}
       Step Functions: ${self.workflow_url}
    
    5. View logs:
       CloudWatch: https://console.aws.amazon.com/cloudwatch/home?region=${var.aws_region}
    
    ========================================
  EOT
}
