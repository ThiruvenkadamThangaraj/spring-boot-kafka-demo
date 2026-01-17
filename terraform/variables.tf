# ========================================
# Terraform Variables
# Define all configurable parameters
# ========================================

variable "aws_region" {
  description = "AWS region where resources will be created"
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name (dev, staging, prod)"
  type        = string
  default     = "dev"
  
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "project_name" {
  description = "Project name used for resource naming"
  type        = string
  default     = "microservices"
}

# ========================================
# Network Configuration
# ========================================

variable "vpc_cidr" {
  description = "CIDR block for VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "az_count" {
  description = "Number of Availability Zones to use"
  type        = number
  default     = 2
  
  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "AZ count must be between 2 and 3."
  }
}

# ========================================
# ECS Configuration
# ========================================

variable "ecs_task_cpu" {
  description = "CPU units for ECS task (256, 512, 1024, 2048, 4096)"
  type        = string
  default     = "512"
}

variable "ecs_task_memory" {
  description = "Memory for ECS task in MB (512, 1024, 2048, 4096, 8192)"
  type        = string
  default     = "1024"
}

variable "service_desired_count" {
  description = "Desired number of ECS tasks per service"
  type        = number
  default     = 2
}

variable "microservices" {
  description = "List of microservices to deploy"
  type = map(object({
    name          = string
    port          = number
    health_check  = string
    cpu           = string
    memory        = string
    desired_count = number
  }))
  
  default = {
    evaluation = {
      name          = "evaluation-service"
      port          = 8081
      health_check  = "/actuator/health"
      cpu           = "512"
      memory        = "1024"
      desired_count = 2
    }
    sampling = {
      name          = "sampling-service"
      port          = 8082
      health_check  = "/actuator/health"
      cpu           = "512"
      memory        = "1024"
      desired_count = 2
    }
    evidence = {
      name          = "evidence-service"
      port          = 8083
      health_check  = "/actuator/health"
      cpu           = "512"
      memory        = "1024"
      desired_count = 2
    }
    remediation = {
      name          = "remediation-service"
      port          = 8084
      health_check  = "/actuator/health"
      cpu           = "512"
      memory        = "1024"
      desired_count = 2
    }
    jira = {
      name          = "jira-service"
      port          = 8085
      health_check  = "/actuator/health"
      cpu           = "512"
      memory        = "1024"
      desired_count = 2
    }
  }
}

# ========================================
# RDS Configuration
# ========================================

variable "db_instance_class" {
  description = "RDS instance class"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage for RDS in GB"
  type        = number
  default     = 20
}

variable "db_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "15.4"
}

variable "db_name" {
  description = "Name of the database"
  type        = string
  default     = "microservices_db"
}

variable "db_username" {
  description = "Master username for database"
  type        = string
  default     = "dbadmin"
  sensitive   = true
}

variable "db_password" {
  description = "Master password for database"
  type        = string
  sensitive   = true
  # Set via environment variable: TF_VAR_db_password
}

# ========================================
# ECR Configuration
# ========================================

variable "ecr_repositories" {
  description = "List of ECR repository names"
  type        = list(string)
  default = [
    "evaluation-service",
    "sampling-service",
    "evidence-service",
    "remediation-service",
    "jira-service"
  ]
}

# ========================================
# Lambda Configuration
# ========================================

variable "lambda_runtime" {
  description = "Lambda runtime"
  type        = string
  default     = "python3.11"
}

variable "lambda_memory_size" {
  description = "Lambda memory size in MB"
  type        = number
  default     = 256
}

variable "lambda_timeout" {
  description = "Lambda timeout in seconds"
  type        = number
  default     = 60
}

# ========================================
# Tags
# ========================================

variable "common_tags" {
  description = "Common tags to apply to all resources"
  type        = map(string)
  default = {
    Terraform   = "true"
    Application = "Spring-Boot-Microservices"
  }
}
