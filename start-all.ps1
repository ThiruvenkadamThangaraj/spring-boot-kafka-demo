# Build and Run All Microservices with Docker
# This script builds all services and starts them using Docker Compose

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Building All Microservices..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Step 1: Build all services with Maven
Write-Host "`n[1/3] Building with Maven..." -ForegroundColor Yellow
mvn clean install -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nMaven build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`nMaven build successful!" -ForegroundColor Green

# Step 2: Build Docker images
Write-Host "`n[2/3] Building Docker images..." -ForegroundColor Yellow
docker-compose build

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nDocker build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "`nDocker images built successfully!" -ForegroundColor Green

# Step 3: Start all services
Write-Host "`n[3/3] Starting all microservices..." -ForegroundColor Yellow
docker-compose up -d

if ($LASTEXITCODE -ne 0) {
    Write-Host "`nFailed to start services!" -ForegroundColor Red
    exit 1
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "All services started successfully!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nService URLs:" -ForegroundColor Cyan
Write-Host "  Evaluation Service:   http://localhost:8081/swagger-ui.html" -ForegroundColor White
Write-Host "  Sampling Service:     http://localhost:8082/swagger-ui.html" -ForegroundColor White
Write-Host "  Evidence Service:     http://localhost:8083/swagger-ui.html" -ForegroundColor White
Write-Host "  Remediation Service:  http://localhost:8084/swagger-ui.html" -ForegroundColor White
Write-Host "  Jira Service:         http://localhost:8085/swagger-ui.html" -ForegroundColor White

Write-Host "`nUseful Commands:" -ForegroundColor Cyan
Write-Host "  View logs:       docker-compose logs -f" -ForegroundColor White
Write-Host "  Stop services:   docker-compose down" -ForegroundColor White
Write-Host "  Restart:         docker-compose restart" -ForegroundColor White
Write-Host "  Check status:    docker-compose ps" -ForegroundColor White
