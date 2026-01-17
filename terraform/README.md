# ========================================
# AWS Infrastructure Deployment with Terraform
# Complete Learning Guide and Reference
# ========================================

## 📚 Table of Contents

1. [Prerequisites](#prerequisites)
2. [What You'll Learn](#what-youll-learn)
3. [AWS Services Overview](#aws-services-overview)
4. [Terraform Basics](#terraform-basics)
5. [Project Structure](#project-structure)
6. [Quick Start](#quick-start)
7. [Detailed Walkthrough](#detailed-walkthrough)
8. [Best Practices](#best-practices)
9. [Troubleshooting](#troubleshooting)
10. [Learning Resources](#learning-resources)

---

## 🎯 Prerequisites

### Required Software
```bash
# 1. Install Terraform (v1.0+)
# Windows: Download from https://www.terraform.io/downloads
# Or use Chocolatey:
choco install terraform

# 2. Install AWS CLI
# Download from: https://aws.amazon.com/cli/
# Or use Chocolatey:
choco install awscli

# 3. Docker Desktop
# Download from: https://www.docker.com/products/docker-desktop

# 4. kubectl (for EKS - covered later)
choco install kubernetes-cli
```

### AWS Account Setup
```bash
# Configure AWS credentials
aws configure
# Enter:
# - AWS Access Key ID
# - AWS Secret Access Key
# - Default region (e.g., us-east-1)
# - Output format (json)

# Verify configuration
aws sts get-caller-identity
```

---

## 🎓 What You'll Learn

### 1. **Terraform (Infrastructure as Code)**
- ✅ Resource definitions and dependencies
- ✅ Variables, outputs, and modules
- ✅ State management
- ✅ Remote backends (S3 + DynamoDB)
- ✅ Workspaces for multiple environments

### 2. **AWS Services**
- ✅ **Networking**: VPC, Subnets, NAT Gateway, Security Groups
- ✅ **Compute**: ECS Fargate (serverless containers)
- ✅ **Database**: RDS PostgreSQL (managed database)
- ✅ **Container Registry**: ECR
- ✅ **Load Balancing**: Application Load Balancer (ALB)
- ✅ **Serverless**: Lambda functions
- ✅ **Orchestration**: Step Functions
- ✅ **Monitoring**: CloudWatch, CloudWatch Logs
- ✅ **Secrets**: AWS Secrets Manager
- ✅ **Notifications**: SNS

### 3. **Docker & Containerization**
- ✅ Dockerfile creation for Spring Boot
- ✅ Multi-stage builds for optimization
- ✅ Image tagging and versioning
- ✅ ECR push/pull operations

### 4. **CI/CD Concepts**
- ✅ Build pipelines
- ✅ Automated testing
- ✅ Blue-green deployments
- ✅ Rolling updates

---

## 🏗️ AWS Services Overview

### Architecture Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                      Internet Gateway                        │
└────────────────────────┬────────────────────────────────────┘
                         │
            ┌────────────▼────────────┐
            │  Application Load       │
            │  Balancer (ALB)         │
            │  - Public Subnets       │
            └────────────┬────────────┘
                         │
        ┌────────────────┼────────────────┐
        │                │                │
┌───────▼──────┐  ┌─────▼──────┐  ┌─────▼──────┐
│ ECS Fargate  │  │ ECS Fargate│  │ ECS Fargate│
│ evaluation   │  │ sampling   │  │ evidence   │
│ - Private    │  │ - Private  │  │ - Private  │
└──────┬───────┘  └──────┬─────┘  └──────┬─────┘
       │                 │                │
       └─────────────────┼────────────────┘
                         │
                 ┌───────▼────────┐
                 │  RDS PostgreSQL│
                 │  - Private     │
                 │  - Multi-AZ    │
                 └────────────────┘

┌─────────────────────────────────────┐
│  Lambda Functions                   │
│  - Data Processor                   │
│  - Health Checker                   │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│  Step Functions                     │
│  - Workflow Orchestration           │
│  - Error Handling                   │
└─────────────────────────────────────┘
```

### Service Breakdown

#### 1. **VPC (Virtual Private Cloud)**
- **Purpose**: Isolated network for your resources
- **Components**: 
  - Public Subnets (for ALB)
  - Private Subnets (for ECS, RDS, Lambda)
  - NAT Gateway (outbound internet for private resources)
- **Why**: Security isolation, control network traffic

#### 2. **ECS Fargate**
- **Purpose**: Run Docker containers without managing servers
- **Key Concepts**:
  - Cluster: Logical grouping of services
  - Task Definition: Blueprint for containers
  - Service: Maintains desired count of tasks
- **Benefits**: Serverless, auto-scaling, pay per use

#### 3. **RDS PostgreSQL**
- **Purpose**: Managed relational database
- **Features**: Automated backups, multi-AZ, read replicas
- **Why**: No database administration overhead

#### 4. **Lambda**
- **Purpose**: Run code without servers
- **Use Cases**: 
  - Event processing
  - Scheduled tasks
  - API backends
- **Benefits**: Pay per invocation, auto-scaling

#### 5. **Step Functions**
- **Purpose**: Orchestrate microservices workflows
- **Features**: Visual workflows, error handling, retries
- **Use Case**: Complex multi-service transactions

---

## 📁 Project Structure

```
terraform/
├── main.tf              # VPC, networking, security groups
├── variables.tf         # Input variables
├── terraform.tfvars     # Variable values
├── ecs.tf              # ECS cluster, services, ALB
├── rds.tf              # PostgreSQL database
├── lambda.tf           # Lambda functions
├── step-functions.tf   # Step Functions workflows
├── ecr.tf              # Container registry
├── outputs.tf          # Output values
└── README.md           # This file

lambda/
├── data-processor/
│   ├── index.py
│   └── requirements.txt
└── health-checker/
    ├── index.py
    └── requirements.txt
```

---

## 🚀 Quick Start

### Step 1: Initialize Terraform
```bash
cd terraform

# Initialize Terraform (downloads providers)
terraform init

# Output:
# Initializing provider plugins...
# - Finding hashicorp/aws versions...
# Terraform has been successfully initialized!
```

### Step 2: Validate Configuration
```bash
# Check syntax
terraform validate

# Output: Success! The configuration is valid.

# Preview what will be created
terraform plan

# This shows:
# - Resources to be created
# - Estimated costs
# - Dependencies
```

### Step 3: Set Required Variables
```bash
# Set database password (required)
$env:TF_VAR_db_password="YourSecurePassword123!"

# Or create a secrets file (terraform.tfvars.secret)
# DO NOT commit this file!
```

### Step 4: Deploy Infrastructure
```bash
# Apply configuration (creates resources)
terraform apply

# Review the plan, type 'yes' to proceed
# This will take 10-15 minutes

# Output will show:
# - Created resources
# - Load balancer URL
# - Database endpoint
# - ECR repository URLs
```

### Step 5: Build and Deploy Services
```bash
# 1. Login to ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin <account-id>.dkr.ecr.us-east-1.amazonaws.com

# 2. Build Docker image
cd ../evaluation-service
docker build -t microservices-evaluation-service-dev:latest .

# 3. Tag image
docker tag microservices-evaluation-service-dev:latest <account-id>.dkr.ecr.us-east-1.amazonaws.com/microservices-evaluation-service-dev:latest

# 4. Push to ECR
docker push <account-id>.dkr.ecr.us-east-1.amazonaws.com/microservices-evaluation-service-dev:latest

# 5. ECS will automatically pull and deploy
```

---

## 📖 Detailed Walkthrough

### Understanding main.tf

```hcl
# Provider configuration - connects to AWS
provider "aws" {
  region = var.aws_region  # Use variable for flexibility
  
  # Apply tags to all resources automatically
  default_tags {
    tags = {
      Project     = "Spring-Boot-Microservices"
      Environment = var.environment
      ManagedBy   = "Terraform"
    }
  }
}

# Data source - fetch available AZs
data "aws_availability_zones" "available" {
  state = "available"
}

# VPC - isolated network
resource "aws_vpc" "main" {
  cidr_block = var.vpc_cidr  # e.g., 10.0.0.0/16
  
  # Enable DNS for RDS endpoints
  enable_dns_hostnames = true
  enable_dns_support   = true
}

# Public subnet - for ALB
resource "aws_subnet" "public" {
  count             = var.az_count  # Create in multiple AZs
  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone = data.aws_availability_zones.available.names[count.index]
  
  map_public_ip_on_launch = true  # Auto-assign public IPs
}
```

### Understanding ECS Configuration

```hcl
# ECS Task Definition - defines container configuration
resource "aws_ecs_task_definition" "services" {
  for_each = var.microservices  # Loop through all services
  
  family = "${each.value.name}-${var.environment}"
  
  # Fargate launch type
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"  # Required for Fargate
  
  # Resource allocation
  cpu    = each.value.cpu     # e.g., "512" = 0.5 vCPU
  memory = each.value.memory  # e.g., "1024" = 1GB RAM
  
  # Container definition
  container_definitions = jsonencode([{
    name  = each.value.name
    image = "${data.aws_caller_identity.current.account_id}.dkr.ecr.${var.aws_region}.amazonaws.com/${each.value.name}:latest"
    
    # Port mapping
    portMappings = [{
      containerPort = each.value.port
      protocol      = "tcp"
    }]
    
    # Environment variables
    environment = [
      { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
      { name = "SERVER_PORT", value = tostring(each.value.port) }
    ]
    
    # Secrets from Secrets Manager
    secrets = [{
      name      = "SPRING_DATASOURCE_PASSWORD"
      valueFrom = aws_secretsmanager_secret.db_password.arn
    }]
    
    # Logging to CloudWatch
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"  = aws_cloudwatch_log_group.ecs.name
        "awslogs-region" = var.aws_region
      }
    }
  }])
}
```

### Understanding Lambda

```hcl
resource "aws_lambda_function" "data_processor" {
  filename      = "../lambda/data-processor.zip"
  function_name = "${var.project_name}-data-processor"
  role          = aws_iam_role.lambda_execution.arn
  handler       = "index.handler"  # Python: filename.function
  runtime       = "python3.11"
  
  # VPC configuration (to access RDS)
  vpc_config {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.lambda.id]
  }
  
  # Environment variables
  environment {
    variables = {
      DB_SECRET_ARN = aws_secretsmanager_secret.db_password.arn
      REGION        = var.aws_region
    }
  }
}
```

---

## 🎨 Best Practices

### 1. **Security**
```hcl
# ✅ Use Secrets Manager for sensitive data
resource "aws_secretsmanager_secret" "db_password" {
  name = "${var.project_name}/${var.environment}/db-password"
}

# ✅ Restrict security group rules
resource "aws_security_group_rule" "rds" {
  type                     = "ingress"
  from_port                = 5432
  to_port                  = 5432
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.ecs_tasks.id  # Only ECS
  security_group_id        = aws_security_group.rds.id
}

# ❌ Avoid hardcoding passwords
# db_password = "password123"  # DON'T DO THIS
```

### 2. **State Management**
```hcl
# ✅ Use remote state (S3 + DynamoDB for locking)
terraform {
  backend "s3" {
    bucket         = "my-terraform-state"
    key            = "microservices/terraform.tfstate"
    region         = "us-east-1"
    encrypt        = true
    dynamodb_table = "terraform-state-lock"
  }
}
```

### 3. **Resource Naming**
```hcl
# ✅ Use consistent naming convention
name = "${var.project_name}-${resource_type}-${var.environment}"

# Examples:
# - microservices-vpc-dev
# - microservices-alb-prod
# - microservices-db-staging
```

### 4. **Cost Optimization**
```hcl
# ✅ Use auto-scaling
resource "aws_appautoscaling_target" "ecs" {
  min_capacity = var.environment == "prod" ? 2 : 1
  max_capacity = var.environment == "prod" ? 10 : 3
}

# ✅ Right-size resources
variable "db_instance_class" {
  default = var.environment == "prod" ? "db.t3.medium" : "db.t3.micro"
}

# ✅ Use ECR lifecycle policies
resource "aws_ecr_lifecycle_policy" "services" {
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep last 10 images"
      selection = {
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
```

---

## 🔧 Common Commands

```bash
# Initialize and download providers
terraform init

# Validate syntax
terraform validate

# Format code
terraform fmt -recursive

# Preview changes
terraform plan

# Apply changes
terraform apply

# Apply without confirmation
terraform apply -auto-approve

# Destroy all resources
terraform destroy

# Show current state
terraform show

# List resources
terraform state list

# Get specific output
terraform output alb_dns_name

# Refresh state from AWS
terraform refresh

# Import existing resource
terraform import aws_vpc.main vpc-12345678

# Create workspace
terraform workspace new staging

# Switch workspace
terraform workspace select prod

# List workspaces
terraform workspace list
```

---

## 🐛 Troubleshooting

### Issue 1: "Error creating VPC"
```bash
# Check AWS credentials
aws sts get-caller-identity

# Verify IAM permissions
# Ensure your user has these permissions:
# - ec2:*
# - ecs:*
# - rds:*
# - iam:*
```

### Issue 2: "Error: timeout while waiting for state"
```bash
# Increase timeout in resource configuration
resource "aws_db_instance" "main" {
  # ...
  
  timeouts {
    create = "60m"
    update = "60m"
    delete = "60m"
  }
}
```

### Issue 3: "Insufficient capacity"
```bash
# Try different instance type or AZ
variable "db_instance_class" {
  default = "db.t3.small"  # Instead of db.t3.micro
}
```

### Issue 4: State Lock Error
```bash
# Force unlock (use with caution)
terraform force-unlock <lock-id>
```

---

## 📚 Learning Path

### Week 1: Terraform Fundamentals
1. Complete Terraform tutorial: https://learn.hashicorp.com/terraform
2. Practice with simple resources (S3, EC2)
3. Understand state management

### Week 2: AWS Networking
1. Learn VPC concepts
2. Practice with subnets, route tables
3. Implement security groups

### Week 3: Container Services
1. Dockerize Spring Boot apps
2. Push to ECR
3. Deploy on ECS Fargate

### Week 4: Serverless
1. Create Lambda functions
2. Set up Step Functions workflows
3. Integrate with microservices

### Week 5: CI/CD
1. Set up GitHub Actions / GitLab CI
2. Automated testing
3. Blue-green deployments

---

## 🔗 Learning Resources

### Official Documentation
- [Terraform AWS Provider](https://registry.terraform.io/providers/hashicorp/aws/latest/docs)
- [AWS ECS Documentation](https://docs.aws.amazon.com/ecs/)
- [AWS Lambda Documentation](https://docs.aws.amazon.com/lambda/)
- [AWS Step Functions](https://docs.aws.amazon.com/step-functions/)

### Tutorials
- [Terraform Learning Path](https://learn.hashicorp.com/terraform)
- [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
- [ECS Workshop](https://ecsworkshop.com/)
- [Serverless Workshop](https://serverlessworkshops.io/)

### Books
- "Terraform: Up & Running" by Yevgeniy Brikman
- "AWS Certified Solutions Architect Study Guide"
- "Docker Deep Dive" by Nigel Poulton

---

## 💡 Next Steps

1. **Deploy this infrastructure**: Follow the Quick Start guide
2. **Experiment**: Modify variables, add services
3. **Monitor**: Use CloudWatch dashboards
4. **Optimize**: Review costs, right-size resources
5. **Automate**: Set up CI/CD pipeline
6. **Scale**: Add more microservices
7. **Advanced**: Implement EKS, API Gateway, DynamoDB

---

## 📞 Getting Help

- **Terraform Registry**: https://registry.terraform.io/
- **AWS Forums**: https://forums.aws.amazon.com/
- **Stack Overflow**: Tag questions with `terraform` and `amazon-web-services`
- **AWS Support**: For production issues

---

**Happy Learning! 🚀**
