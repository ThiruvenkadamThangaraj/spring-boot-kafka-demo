# ========================================
# TERRAFORM AND AWS LEARNING GUIDE
# From Basics to Production-Ready Infrastructure
# ========================================

## 📋 Overview

This guide will help you master:
- ✅ Terraform fundamentals
- ✅ AWS core services (Lambda, Step Functions, EKS)
- ✅ Docker and Kubernetes
- ✅ CI/CD pipelines
- ✅ Real-world microservices deployment

---

## 🎯 Learning Path (3+ Years Professional Level)

### Phase 1: Foundations (Weeks 1-2)

#### **Day 1-3: Terraform Basics**

**Core Concepts:**
```hcl
# 1. Providers - Connect to cloud platforms
provider "aws" {
  region = "us-east-1"
}

# 2. Resources - Infrastructure components
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
}

# 3. Variables - Make code reusable
variable "instance_type" {
  description = "EC2 instance type"
  type        = string
  default     = "t2.micro"
}

# 4. Outputs - Export values
output "instance_ip" {
  value = aws_instance.web.public_ip
}

# 5. Data Sources - Query existing resources
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
}
```

**Hands-on Exercise:**
```bash
# Create a simple EC2 instance
mkdir terraform-basics
cd terraform-basics

# Create main.tf
cat > main.tf << 'EOF'
provider "aws" {
  region = "us-east-1"
}

resource "aws_instance" "my_first_vm" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t2.micro"
  
  tags = {
    Name = "MyFirstTerraformVM"
  }
}

output "public_ip" {
  value = aws_instance.my_first_vm.public_ip
}
EOF

# Initialize, plan, apply
terraform init
terraform plan
terraform apply
terraform destroy  # Clean up
```

#### **Day 4-7: AWS Fundamentals**

**1. IAM (Identity and Access Management)**
```hcl
# Create IAM role for Lambda
resource "aws_iam_role" "lambda_role" {
  name = "lambda_execution_role"
  
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Action = "sts:AssumeRole"
      Effect = "Allow"
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# Attach policy
resource "aws_iam_role_policy_attachment" "lambda_basic" {
  role       = aws_iam_role.lambda_role.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}
```

**2. S3 (Simple Storage Service)**
```hcl
resource "aws_s3_bucket" "my_bucket" {
  bucket = "my-terraform-state-bucket-${random_id.suffix.hex}"
  
  tags = {
    Name        = "TerraformStateBucket"
    Environment = "Dev"
  }
}

# Enable versioning
resource "aws_s3_bucket_versioning" "versioning" {
  bucket = aws_s3_bucket.my_bucket.id
  
  versioning_configuration {
    status = "Enabled"
  }
}

# Enable encryption
resource "aws_s3_bucket_server_side_encryption_configuration" "encryption" {
  bucket = aws_s3_bucket.my_bucket.id
  
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}
```

---

### Phase 2: Networking and Security (Weeks 3-4)

#### **VPC Architecture**

```hcl
# Complete VPC setup
module "vpc" {
  source = "terraform-aws-modules/vpc/aws"
  
  name = "my-vpc"
  cidr = "10.0.0.0/16"
  
  azs             = ["us-east-1a", "us-east-1b", "us-east-1c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]
  
  enable_nat_gateway = true
  enable_vpn_gateway = false
  
  tags = {
    Terraform   = "true"
    Environment = "dev"
  }
}
```

#### **Security Groups**

```hcl
# Web server security group
resource "aws_security_group" "web" {
  name        = "web-sg"
  description = "Security group for web servers"
  vpc_id      = module.vpc.vpc_id
  
  # Inbound HTTP
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Inbound HTTPS
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  
  # Outbound all
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# Database security group
resource "aws_security_group" "database" {
  name        = "database-sg"
  vpc_id      = module.vpc.vpc_id
  
  # Only allow from web servers
  ingress {
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.web.id]
  }
}
```

---

### Phase 3: Serverless with Lambda (Weeks 5-6)

#### **Lambda Function Basics**

```python
# lambda/hello-world/index.py
import json

def handler(event, context):
    """Simple Lambda function"""
    
    print(f"Event: {json.dumps(event)}")
    
    return {
        'statusCode': 200,
        'headers': {
            'Content-Type': 'application/json'
        },
        'body': json.dumps({
            'message': 'Hello from Lambda!',
            'input': event
        })
    }
```

**Terraform Configuration:**
```hcl
# Package Lambda code
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = "${path.module}/lambda/hello-world/index.py"
  output_path = "${path.module}/lambda/hello-world.zip"
}

# Create Lambda function
resource "aws_lambda_function" "hello_world" {
  filename         = data.archive_file.lambda_zip.output_path
  function_name    = "hello-world"
  role             = aws_iam_role.lambda_role.arn
  handler          = "index.handler"
  source_code_hash = data.archive_file.lambda_zip.output_base64sha256
  runtime          = "python3.11"
  
  environment {
    variables = {
      ENVIRONMENT = "dev"
    }
  }
}

# API Gateway to trigger Lambda
resource "aws_apigatewayv2_api" "lambda_api" {
  name          = "lambda-api"
  protocol_type = "HTTP"
}

resource "aws_apigatewayv2_integration" "lambda" {
  api_id           = aws_apigatewayv2_api.lambda_api.id
  integration_type = "AWS_PROXY"
  integration_uri  = aws_lambda_function.hello_world.invoke_arn
}

resource "aws_apigatewayv2_route" "lambda" {
  api_id    = aws_apigatewayv2_api.lambda_api.id
  route_key = "GET /hello"
  target    = "integrations/${aws_apigatewayv2_integration.lambda.id}"
}

output "api_endpoint" {
  value = aws_apigatewayv2_api.lambda_api.api_endpoint
}
```

#### **Advanced Lambda Patterns**

```python
# lambda/event-processor/index.py
import json
import boto3
import os

dynamodb = boto3.resource('dynamodb')
s3 = boto3.client('s3')
sns = boto3.client('sns')

def handler(event, context):
    """Process events and store in DynamoDB"""
    
    table_name = os.environ['DYNAMODB_TABLE']
    table = dynamodb.Table(table_name)
    
    # Process S3 event
    for record in event.get('Records', []):
        if 'eventName' in record and record['eventName'].startswith('ObjectCreated'):
            bucket = record['s3']['bucket']['name']
            key = record['s3']['object']['key']
            
            # Get object
            obj = s3.get_object(Bucket=bucket, Key=key)
            content = obj['Body'].read().decode('utf-8')
            
            # Store in DynamoDB
            table.put_item(Item={
                'id': key,
                'content': content,
                'timestamp': context.request_id
            })
            
            # Send notification
            sns.publish(
                TopicArn=os.environ['SNS_TOPIC_ARN'],
                Subject='File Processed',
                Message=f'Successfully processed {key}'
            )
    
    return {'statusCode': 200, 'body': 'Processed'}
```

---

### Phase 4: Step Functions (Weeks 7-8)

#### **Simple Workflow**

```hcl
resource "aws_sfn_state_machine" "order_processing" {
  name     = "order-processing"
  role_arn = aws_iam_role.step_functions.arn
  
  definition = jsonencode({
    Comment = "Order Processing Workflow"
    StartAt = "ValidateOrder"
    States = {
      ValidateOrder = {
        Type     = "Task"
        Resource = aws_lambda_function.validate_order.arn
        Next     = "CheckInventory"
        Retry = [{
          ErrorEquals     = ["States.ALL"]
          IntervalSeconds = 2
          MaxAttempts     = 3
          BackoffRate     = 2
        }]
      }
      
      CheckInventory = {
        Type     = "Task"
        Resource = aws_lambda_function.check_inventory.arn
        Next     = "IsInStock"
      }
      
      IsInStock = {
        Type = "Choice"
        Choices = [{
          Variable     = "$.inStock"
          BooleanEquals = true
          Next         = "ProcessPayment"
        }]
        Default = "OutOfStock"
      }
      
      ProcessPayment = {
        Type     = "Task"
        Resource = aws_lambda_function.process_payment.arn
        Next     = "SendConfirmation"
      }
      
      SendConfirmation = {
        Type     = "Task"
        Resource = aws_lambda_function.send_email.arn
        End      = true
      }
      
      OutOfStock = {
        Type  = "Fail"
        Error = "OutOfStockError"
        Cause = "Item is out of stock"
      }
    }
  })
}
```

#### **Parallel Processing**

```hcl
# Process multiple tasks in parallel
definition = jsonencode({
  StartAt = "ParallelProcessing"
  States = {
    ParallelProcessing = {
      Type = "Parallel"
      Branches = [
        {
          StartAt = "ProcessImages"
          States = {
            ProcessImages = {
              Type     = "Task"
              Resource = aws_lambda_function.process_images.arn
              End      = true
            }
          }
        },
        {
          StartAt = "GenerateThumbnails"
          States = {
            GenerateThumbnails = {
              Type     = "Task"
              Resource = aws_lambda_function.generate_thumbnails.arn
              End      = true
            }
          }
        },
        {
          StartAt = "ExtractMetadata"
          States = {
            ExtractMetadata = {
              Type     = "Task"
              Resource = aws_lambda_function.extract_metadata.arn
              End      = true
            }
          }
        }
      ]
      Next = "AggregateResults"
    }
    
    AggregateResults = {
      Type     = "Task"
      Resource = aws_lambda_function.aggregate.arn
      End      = true
    }
  }
})
```

---

### Phase 5: EKS and Kubernetes (Weeks 9-12)

#### **EKS Cluster with Terraform**

```hcl
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"
  
  cluster_name    = "my-microservices-cluster"
  cluster_version = "1.28"
  
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
  
  # Managed node groups
  eks_managed_node_groups = {
    general = {
      min_size     = 2
      max_size     = 10
      desired_size = 3
      
      instance_types = ["t3.medium"]
      capacity_type  = "SPOT"  # Cost optimization
      
      labels = {
        role = "general"
      }
    }
    
    cpu_intensive = {
      min_size     = 1
      max_size     = 5
      desired_size = 2
      
      instance_types = ["c5.xlarge"]
      
      labels = {
        role = "cpu-intensive"
      }
      
      taints = [{
        key    = "workload"
        value  = "cpu-intensive"
        effect = "NoSchedule"
      }]
    }
  }
  
  # Cluster add-ons
  cluster_addons = {
    coredns = {
      most_recent = true
    }
    kube-proxy = {
      most_recent = true
    }
    vpc-cni = {
      most_recent = true
    }
  }
  
  tags = {
    Environment = "dev"
    Terraform   = "true"
  }
}
```

#### **Kubernetes Deployment**

```yaml
# k8s/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: evaluation-service
  labels:
    app: evaluation-service
spec:
  replicas: 3
  selector:
    matchLabels:
      app: evaluation-service
  template:
    metadata:
      labels:
        app: evaluation-service
    spec:
      containers:
      - name: evaluation-service
        image: <account-id>.dkr.ecr.us-east-1.amazonaws.com/evaluation-service:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: evaluation-service
spec:
  selector:
    app: evaluation-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8081
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: evaluation-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: evaluation-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**Apply Kubernetes manifests:**
```bash
# Configure kubectl
aws eks update-kubeconfig --region us-east-1 --name my-microservices-cluster

# Apply manifests
kubectl apply -f k8s/deployment.yaml

# Check status
kubectl get pods
kubectl get services
kubectl get hpa

# View logs
kubectl logs -f deployment/evaluation-service
```

---

### Phase 6: CI/CD Pipelines (Weeks 13-14)

#### **GitHub Actions Workflow**

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy to AWS

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_REPOSITORY: evaluation-service

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run tests
        run: mvn clean test
      
      - name: Generate coverage report
        run: mvn jacoco:report
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  build-and-push:
    needs: test
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Login to ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1
      
      - name: Build and push Docker image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
          docker tag $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG $ECR_REGISTRY/$ECR_REPOSITORY:latest
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
          docker push $ECR_REGISTRY/$ECR_REPOSITORY:latest

  deploy-to-ecs:
    needs: build-and-push
    runs-on: ubuntu-latest
    
    steps:
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Force new ECS deployment
        run: |
          aws ecs update-service \
            --cluster microservices-cluster-dev \
            --service evaluation-service-dev \
            --force-new-deployment

  deploy-to-eks:
    needs: build-and-push
    runs-on: ubuntu-latest
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: ${{ env.AWS_REGION }}
      
      - name: Update kubeconfig
        run: |
          aws eks update-kubeconfig \
            --region ${{ env.AWS_REGION }} \
            --name my-microservices-cluster
      
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/evaluation-service \
            evaluation-service=${{ steps.login-ecr.outputs.registry }}/$ECR_REPOSITORY:${{ github.sha }}
          kubectl rollout status deployment/evaluation-service
```

---

## 🎯 Key Skills Summary

### **Terraform Expertise**
✅ Resource management (create, update, destroy)
✅ State management (local, remote S3)
✅ Modules for reusability
✅ Workspaces for multi-environment
✅ Provisioners and lifecycle rules
✅ Import existing infrastructure

### **AWS Lambda**
✅ Function creation and configuration
✅ Event sources (S3, API Gateway, EventBridge)
✅ IAM roles and permissions
✅ Environment variables and secrets
✅ Monitoring with CloudWatch
✅ Performance optimization

### **AWS Step Functions**
✅ State machine design
✅ Task orchestration
✅ Error handling and retries
✅ Parallel execution
✅ Choice states for branching
✅ Integration with Lambda, ECS, SNS

### **EKS (Kubernetes)**
✅ Cluster creation and management
✅ Node groups and auto-scaling
✅ Pod deployments and services
✅ Ingress controllers
✅ Helm charts
✅ Monitoring with Prometheus/Grafana

### **Docker & Containers**
✅ Multi-stage builds
✅ Image optimization
✅ Security scanning
✅ Registry management (ECR)
✅ Container orchestration

### **CI/CD**
✅ GitHub Actions workflows
✅ Automated testing
✅ Blue-green deployments
✅ Canary releases
✅ Rollback strategies

---

## 📚 Practice Projects

1. **Serverless API**: Lambda + API Gateway + DynamoDB
2. **Data Pipeline**: S3 → Lambda → Step Functions → RDS
3. **Microservices on EKS**: Full Spring Boot deployment
4. **Event-Driven Architecture**: SQS + Lambda + SNS
5. **CI/CD Pipeline**: Full automation from commit to production

---

**You now have a complete, production-ready setup! 🚀**
