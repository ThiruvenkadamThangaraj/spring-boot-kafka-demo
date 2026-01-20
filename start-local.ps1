# Start All Services Locally (Without Docker)
# This script starts all microservices using Maven Spring Boot plugin

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Starting All Services Locally          " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Continue"

# Check if Maven build is up to date
Write-Host "[Step 1/3] Checking Maven Build..." -ForegroundColor Yellow
if (-not (Test-Path ".\evaluation-service\target\evaluation-service-0.0.1-SNAPSHOT.jar")) {
    Write-Host "  Building all services..." -ForegroundColor Yellow
    mvn clean install -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Host ""
        Write-Host "Maven build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Build complete" -ForegroundColor Green
} else {
    Write-Host "  Build artifacts found (skipping build)" -ForegroundColor Green
}
Write-Host ""

# Check if Kafka is needed and running
Write-Host "[Step 2/3] Checking Kafka..." -ForegroundColor Yellow
$KAFKA_HOME = "C:\kafka"
$kafkaRunning = $false

if (Test-Path $KAFKA_HOME) {
    $kafkaProcess = Get-Process -Name "java" -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "*kafka*" }
    if ($kafkaProcess) {
        Write-Host "  Kafka is running" -ForegroundColor Green
        $kafkaRunning = $true
    } else {
        Write-Host "  Kafka is not running" -ForegroundColor Yellow
        Write-Host "  Audit service requires Kafka to be running" -ForegroundColor Yellow
        Write-Host ""
        $response = Read-Host "  Start Kafka now? (y/n)"
        if ($response -eq 'y' -or $response -eq 'Y') {
            Write-Host "  Starting Zookeeper..." -ForegroundColor Yellow
            $zkCommand = "cd '$KAFKA_HOME'; .\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties"
            Start-Process powershell -ArgumentList "-NoExit", "-Command", $zkCommand -WindowStyle Normal
            Start-Sleep -Seconds 5
            
            Write-Host "  Starting Kafka..." -ForegroundColor Yellow
            $kafkaCommand = "cd '$KAFKA_HOME'; .\bin\windows\kafka-server-start.bat .\config\server.properties"
            Start-Process powershell -ArgumentList "-NoExit", "-Command", $kafkaCommand -WindowStyle Normal
            Start-Sleep -Seconds 10
            
            Write-Host "  Kafka started" -ForegroundColor Green
            $kafkaRunning = $true
        } else {
            Write-Host "  Continuing without Kafka (audit service will not work properly)" -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "  Kafka not found at $KAFKA_HOME" -ForegroundColor Yellow
    Write-Host "  Audit service requires Kafka - install from: https://kafka.apache.org/downloads" -ForegroundColor Yellow
}
Write-Host ""

# Start all microservices
Write-Host "[Step 3/3] Starting Microservices..." -ForegroundColor Yellow
Write-Host ""

$services = @(
    @{Name="Evaluation"; Path="evaluation-service"; Port=8081},
    @{Name="Sampling"; Path="sampling-service"; Port=8082},
    @{Name="Evidence"; Path="evidence-service"; Port=8083},
    @{Name="Remediation"; Path="remediation-service"; Port=8084},
    @{Name="Jira"; Path="jira-service"; Port=8085},
    @{Name="Audit"; Path="audit-service"; Port=8086}
)

$startedServices = @()

foreach ($service in $services) {
    Write-Host "  Starting $($service.Name) Service (Port $($service.Port))..." -ForegroundColor Cyan
    
    # Skip audit service if Kafka is not running
    if ($service.Name -eq "Audit" -and -not $kafkaRunning) {
        Write-Host "    Skipped (Kafka not running)" -ForegroundColor Yellow
        Write-Host ""
        continue
    }
    
    try {
        $processTitle = "$($service.Name) Service - Port $($service.Port)"
        $command = "cd '$($service.Path)'; `$host.UI.RawUI.WindowTitle = '$processTitle'; mvn spring-boot:run"
        Start-Process powershell -ArgumentList "-NoExit", "-Command", $command -WindowStyle Normal
        $startedServices += $service
        Write-Host "    Started" -ForegroundColor Green
    } catch {
        Write-Host "    Failed to start: $_" -ForegroundColor Red
    }
    
    Write-Host ""
    Start-Sleep -Seconds 2
}

# Wait for services to start
Write-Host "Waiting for services to start (30 seconds)..." -ForegroundColor Yellow
Start-Sleep -Seconds 30
Write-Host ""

# Check health of all services
Write-Host "Checking Service Health..." -ForegroundColor Cyan
Write-Host ""

$healthyServices = 0
$totalServices = 0

foreach ($service in $startedServices) {
    $totalServices++
    $healthUrl = "http://localhost:$($service.Port)/api/$($service.Path.Replace('-service',''))/health"
    
    # Special case for audit service
    if ($service.Name -eq "Audit") {
        $healthUrl = "http://localhost:$($service.Port)/api/audit/health"
    }
    
    Write-Host "  $($service.Name) Service (Port $($service.Port))..." -NoNewline
    try {
        $response = Invoke-WebRequest -Uri $healthUrl -Method GET -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host " Healthy" -ForegroundColor Green
            $healthyServices++
        } else {
            Write-Host " Unhealthy (Status: $($response.StatusCode))" -ForegroundColor Yellow
        }
    } catch {
        Write-Host " Not ready yet (still starting up)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "==========================================" -ForegroundColor Green
Write-Host "  Services Started Successfully!          " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Started $totalServices services ($healthyServices healthy)" -ForegroundColor Cyan
Write-Host ""

# Display service URLs
Write-Host "Service URLs:" -ForegroundColor Cyan
foreach ($service in $startedServices) {
    if ($service.Name -eq "Audit") {
        $label = "$($service.Name) Service:"
        Write-Host "  $label".PadRight(25) -NoNewline -ForegroundColor White
        Write-Host "http://localhost:$($service.Port)/api/audit" -ForegroundColor Yellow
    } else {
        $label = "$($service.Name) Service:"
        Write-Host "  $label".PadRight(25) -NoNewline -ForegroundColor White
        Write-Host "http://localhost:$($service.Port)/swagger-ui.html" -ForegroundColor Yellow
    }
}

Write-Host ""

# Display Kafka info if running
if ($kafkaRunning) {
    Write-Host "Kafka:" -ForegroundColor Cyan
    Write-Host "  Bootstrap Server:".PadRight(25) -NoNewline -ForegroundColor White
    Write-Host "localhost:9092" -ForegroundColor Yellow
    Write-Host "  Audit Events Topic:".PadRight(25) -NoNewline -ForegroundColor White
    Write-Host "audit-events" -ForegroundColor Yellow
    Write-Host ""
}

Write-Host "Useful Commands:" -ForegroundColor Cyan
Write-Host "  Test Audit Service:  .\test-audit-service.ps1" -ForegroundColor White
Write-Host "  Create Audit Topic:  .\create-audit-topic.ps1" -ForegroundColor White
Write-Host "  Stop All Services:   Close all PowerShell windows" -ForegroundColor White
Write-Host ""
Write-Host "Note: Each service is running in its own PowerShell window" -ForegroundColor Yellow
Write-Host "      Close the windows or press Ctrl+C in each to stop services" -ForegroundColor Yellow
Write-Host ""
