# ========================================
# Docker Build and Push Script for AWS ECR
# Builds all microservices and pushes to ECR
# ========================================

param(
    [string]$Region = "us-east-1",
    [string]$Environment = "dev",
    [string]$Service = "all"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Docker Build and Push to AWS ECR" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Get AWS Account ID
Write-Host "`nFetching AWS Account ID..." -ForegroundColor Yellow
$accountId = (aws sts get-caller-identity --query Account --output text)

if (-not $accountId) {
    Write-Host "Error: Unable to get AWS Account ID. Please check AWS credentials." -ForegroundColor Red
    exit 1
}

Write-Host "Account ID: $accountId" -ForegroundColor Green

# ECR Registry URL
$ecrRegistry = "${accountId}.dkr.ecr.${Region}.amazonaws.com"

# Login to ECR
Write-Host "`nLogging in to ECR..." -ForegroundColor Yellow
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $ecrRegistry

if ($LASTEXITCODE -ne 0) {
    Write-Host "Error: ECR login failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Logged in to ECR successfully" -ForegroundColor Green

# Define microservices
$services = @(
    @{ Name = "evaluation-service"; Port = 8081 }
    @{ Name = "sampling-service"; Port = 8082 }
    @{ Name = "evidence-service"; Port = 8083 }
    @{ Name = "remediation-service"; Port = 8084 }
    @{ Name = "jira-service"; Port = 8085 }
)

# Filter services if specific service requested
if ($Service -ne "all") {
    $services = $services | Where-Object { $_.Name -eq $Service }
    if ($services.Count -eq 0) {
        Write-Host "Error: Service '$Service' not found!" -ForegroundColor Red
        exit 1
    }
}

# Build and push each service
foreach ($svc in $services) {
    $serviceName = $svc.Name
    $serviceDir = $serviceName
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "Building $serviceName..." -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
    # Check if service directory exists
    if (-not (Test-Path $serviceDir)) {
        Write-Host "Warning: $serviceDir not found, skipping..." -ForegroundColor Yellow
        continue
    }
    
    # Check if Dockerfile exists
    if (-not (Test-Path "$serviceDir\Dockerfile")) {
        Write-Host "Warning: Dockerfile not found in $serviceDir, skipping..." -ForegroundColor Yellow
        continue
    }
    
    # Build JAR first
    Write-Host "`nBuilding JAR for $serviceName..." -ForegroundColor Yellow
    Push-Location $serviceDir
    mvn clean package -DskipTests
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Maven build failed for $serviceName!" -ForegroundColor Red
        Pop-Location
        continue
    }
    Pop-Location
    
    Write-Host "✓ JAR built successfully" -ForegroundColor Green
    
    # Build Docker image
    $imageName = "microservices-${serviceName}-${Environment}"
    $ecrImageName = "${ecrRegistry}/${imageName}"
    
    Write-Host "`nBuilding Docker image: $imageName" -ForegroundColor Yellow
    docker build -t $imageName "$serviceDir"
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error: Docker build failed for $serviceName!" -ForegroundColor Red
        continue
    }
    
    Write-Host "✓ Docker image built successfully" -ForegroundColor Green
    
    # Tag for ECR
    Write-Host "`nTagging image for ECR..." -ForegroundColor Yellow
    docker tag "${imageName}:latest" "${ecrImageName}:latest"
    docker tag "${imageName}:latest" "${ecrImageName}:$(Get-Date -Format 'yyyyMMdd-HHmmss')"
    
    # Create ECR repository if it doesn't exist
    Write-Host "`nEnsuring ECR repository exists..." -ForegroundColor Yellow
    aws ecr describe-repositories --repository-names $imageName --region $Region 2>$null
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Creating ECR repository: $imageName" -ForegroundColor Gray
        aws ecr create-repository --repository-name $imageName --region $Region | Out-Null
    }
    
    # Push to ECR
    Write-Host "`nPushing to ECR..." -ForegroundColor Yellow
    docker push "${ecrImageName}:latest"
    
    if ($LASTEXITCODE -eq 0) {
        Write-Host "✓ Successfully pushed $serviceName to ECR" -ForegroundColor Green
    } else {
        Write-Host "Error: Failed to push $serviceName to ECR!" -ForegroundColor Red
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Build and Push Complete!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "1. cd terraform" -ForegroundColor Gray
Write-Host "2. terraform apply" -ForegroundColor Gray
Write-Host "3. ECS will automatically pull images from ECR" -ForegroundColor Gray
