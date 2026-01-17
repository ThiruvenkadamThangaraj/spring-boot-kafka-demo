# ========================================
# Terraform Environment Configuration
# Development environment variables
# ========================================

aws_region  = "us-east-1"
environment = "dev"
project_name = "microservices"

# Network Configuration
vpc_cidr = "10.0.0.0/16"
az_count = 2

# ECS Configuration
ecs_task_cpu    = "512"
ecs_task_memory = "1024"
service_desired_count = 2

# RDS Configuration
db_instance_class     = "db.t3.micro"
db_allocated_storage  = 20
db_engine_version     = "15.4"
db_name              = "microservices_db"
db_username          = "dbadmin"
# db_password should be set via environment variable: TF_VAR_db_password

# Lambda Configuration
lambda_runtime     = "python3.11"
lambda_memory_size = 256
lambda_timeout     = 60

# Common Tags
common_tags = {
  Terraform   = "true"
  Application = "Spring-Boot-Microservices"
  CostCenter  = "Engineering"
  Team        = "Platform"
}
