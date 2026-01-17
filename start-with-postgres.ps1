# ========================================
# Start Services with PostgreSQL
# Alternative to start-without-docker.ps1 with PostgreSQL support
# ========================================

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting Microservices with PostgreSQL" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# Check if PostgreSQL is running
Write-Host "`nChecking PostgreSQL connection..." -ForegroundColor Yellow

try {
    $pgTest = docker ps --filter "name=postgres-microservices" --format "{{.Names}}"
    
    if ($pgTest -eq "postgres-microservices") {
        Write-Host "✓ PostgreSQL container is running" -ForegroundColor Green
    } else {
        Write-Host "PostgreSQL container not found. Starting it now..." -ForegroundColor Yellow
        docker-compose -f docker-compose-postgres.yml up -d postgres
        
        Write-Host "Waiting for PostgreSQL to be ready..." -ForegroundColor Gray
        Start-Sleep -Seconds 10
        
        # Test connection
        $retries = 0
        $maxRetries = 30
        $connected = $false
        
        while (-not $connected -and $retries -lt $maxRetries) {
            try {
                docker exec postgres-microservices pg_isready -U dbadmin -d microservices_db 2>$null | Out-Null
                if ($LASTEXITCODE -eq 0) {
                    $connected = $true
                    Write-Host "✓ PostgreSQL is ready!" -ForegroundColor Green
                }
            } catch {
                $retries++
                Write-Host "." -NoNewline
                Start-Sleep -Seconds 1
            }
        }
        
        if (-not $connected) {
            Write-Host "`n✗ Failed to connect to PostgreSQL after $maxRetries attempts" -ForegroundColor Red
            Write-Host "Please check Docker logs: docker logs postgres-microservices" -ForegroundColor Yellow
            exit 1
        }
    }
} catch {
    Write-Host "✗ Docker is not running or not installed" -ForegroundColor Red
    Write-Host "Please ensure:" -ForegroundColor Yellow
    Write-Host "1. Docker Desktop is installed and running" -ForegroundColor Gray
    Write-Host "2. Run: docker-compose -f docker-compose-postgres.yml up -d postgres" -ForegroundColor Gray
    exit 1
}

# Set environment variables for PostgreSQL
$env:DB_URL = "jdbc:postgresql://localhost:5432/microservices_db"
$env:DB_USERNAME = "dbadmin"
$env:DB_PASSWORD = "dbpassword"
$env:SPRING_PROFILES_ACTIVE = "dev"

Write-Host "`nDatabase Configuration:" -ForegroundColor Cyan
Write-Host "URL: $env:DB_URL" -ForegroundColor Gray
Write-Host "Username: $env:DB_USERNAME" -ForegroundColor Gray
Write-Host "Profile: $env:SPRING_PROFILES_ACTIVE" -ForegroundColor Gray

# Build all services
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Building all services..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

mvn clean install -DskipTests

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Build failed!" -ForegroundColor Red
    exit 1
}

Write-Host "✓ Build completed successfully!" -ForegroundColor Green

# Define services
$services = @(
    @{ Name = "evaluation-service"; Port = 8081; Path = "evaluation-service" }
    @{ Name = "sampling-service"; Port = 8082; Path = "sampling-service" }
    @{ Name = "evidence-service"; Port = 8083; Path = "evidence-service" }
    @{ Name = "remediation-service"; Port = 8084; Path = "remediation-service" }
    @{ Name = "jira-service"; Port = 8085; Path = "jira-service" }
)

# Start services in background
$jobs = @()

foreach ($service in $services) {
    Write-Host "`nStarting $($service.Name) on port $($service.Port)..." -ForegroundColor Yellow
    
    $scriptBlock = {
        param($servicePath, $port, $dbUrl, $dbUser, $dbPass, $profile)
        
        $env:SERVER_PORT = $port
        $env:DB_URL = $dbUrl
        $env:DB_USERNAME = $dbUser
        $env:DB_PASSWORD = $dbPass
        $env:SPRING_PROFILES_ACTIVE = $profile
        
        Set-Location $servicePath
        mvn spring-boot:run
    }
    
    $job = Start-Job -ScriptBlock $scriptBlock -ArgumentList $service.Path, $service.Port, $env:DB_URL, $env:DB_USERNAME, $env:DB_PASSWORD, $env:SPRING_PROFILES_ACTIVE
    $jobs += @{ Job = $job; Name = $service.Name; Port = $service.Port }
    
    Start-Sleep -Seconds 5
}

Write-Host "`n========================================" -ForegroundColor Green
Write-Host "All services started!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green

Write-Host "`nService Endpoints:" -ForegroundColor Cyan
foreach ($service in $services) {
    Write-Host "- $($service.Name): http://localhost:$($service.Port)" -ForegroundColor Gray
    Write-Host "  Health: http://localhost:$($service.Port)/actuator/health" -ForegroundColor DarkGray
    Write-Host "  Swagger: http://localhost:$($service.Port)/swagger-ui.html" -ForegroundColor DarkGray
}

Write-Host "`nDatabase Access:" -ForegroundColor Cyan
Write-Host "- pgAdmin: http://localhost:5050" -ForegroundColor Gray
Write-Host "  Email: admin@example.com" -ForegroundColor DarkGray
Write-Host "  Password: admin" -ForegroundColor DarkGray

Write-Host "`nPress Ctrl+C to stop all services..." -ForegroundColor Yellow

# Monitor jobs
try {
    while ($true) {
        Start-Sleep -Seconds 5
        
        foreach ($item in $jobs) {
            if ($item.Job.State -eq "Failed") {
                Write-Host "`n✗ $($item.Name) failed!" -ForegroundColor Red
                Receive-Job -Job $item.Job
            }
        }
    }
} finally {
    Write-Host "`n`nStopping all services..." -ForegroundColor Yellow
    
    foreach ($item in $jobs) {
        Stop-Job -Job $item.Job
        Remove-Job -Job $item.Job
        Write-Host "✓ Stopped $($item.Name)" -ForegroundColor Gray
    }
    
    Write-Host "`n✓ All services stopped!" -ForegroundColor Green
    Write-Host "Note: PostgreSQL container is still running. To stop it:" -ForegroundColor Yellow
    Write-Host "docker-compose -f docker-compose-postgres.yml down" -ForegroundColor Gray
}
