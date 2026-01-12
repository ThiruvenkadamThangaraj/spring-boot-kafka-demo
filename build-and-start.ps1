
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BUILD AND START ALL SERVICES" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

# Step 1: Stop all running services
Write-Host "Step 1: Stopping all running services..." -ForegroundColor Cyan

# Kill all CMD windows running Kafka/Zookeeper
Write-Host "  Stopping Kafka and Zookeeper CMD windows..." -ForegroundColor Yellow
Get-Process cmd -ErrorAction SilentlyContinue | ForEach-Object {
    try {
        $cmdLine = (Get-WmiObject Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
        if ($cmdLine -like "*zookeeper*" -or $cmdLine -like "*kafka*") {
            Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
        }
    } catch {}
}

# Kill all PowerShell windows running microservices
Get-Process powershell -ErrorAction SilentlyContinue | ForEach-Object {
    if ($_.Id -ne $PID) {  # Don't kill current script
        try {
            $cmdLine = (Get-WmiObject Win32_Process -Filter "ProcessId = $($_.Id)" -ErrorAction SilentlyContinue).CommandLine
            if ($cmdLine -like "*spring-boot:run*" -or $cmdLine -like "*-service*") {
                Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue
            }
        } catch {}
    }
}

# Stop all Java processes (microservices and Kafka)
Start-Sleep -Seconds 2
$javaPids = Get-Process java -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Id
if ($javaPids) {
    Write-Host "  Stopping Java processes..." -ForegroundColor Yellow
    Stop-Process -Id $javaPids -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 3
    Write-Host "  ✓ All Java processes stopped" -ForegroundColor Green
} else {
    Write-Host "  No running Java processes found" -ForegroundColor Gray
}

# Force clear ports if still occupied
$ports = @(8081, 8082, 8083, 8084, 8085, 9092, 2181)
$portNames = @("Evaluation", "Sampling", "Evidence", "Remediation", "Jira", "Kafka", "Zookeeper")
Write-Host "`n  Verifying ports are free..." -ForegroundColor Cyan
Start-Sleep -Seconds 2
for($i=0; $i -lt $ports.Count; $i++) {
    $listening = netstat -ano | findstr ":$($ports[$i])\s.*LISTENING"
    if ($listening) {
        Write-Host "  ⚠ Port $($ports[$i]) ($($portNames[$i])) still in use, forcing release..." -ForegroundColor Yellow
        if ($listening -match "LISTENING\s+(\d+)") {
            $pid = $Matches[1]
            Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
        }
    }
}
Start-Sleep -Seconds 2
Write-Host "  ✓ All ports cleared`n" -ForegroundColor Green

# Step 2: Build the project
Write-Host "Step 2: Building project..." -ForegroundColor Cyan
Write-Host "  Running: mvn clean install -DskipTests`n" -ForegroundColor Gray

$buildOutput = mvn clean install -DskipTests 2>&1
$buildExitCode = $LASTEXITCODE

if ($buildExitCode -eq 0) {
    Write-Host "`n✓ BUILD SUCCESS!`n" -ForegroundColor Green
} else {
    Write-Host "`n✗ BUILD FAILED!`n" -ForegroundColor Red
    Write-Host "Build output:" -ForegroundColor Yellow
    Write-Host $buildOutput
    Write-Host "`nExiting without starting services.`n" -ForegroundColor Red
    exit 1
}

# Step 3: Start Kafka
Write-Host "Step 3: Starting Kafka..." -ForegroundColor Cyan

Write-Host "  Starting Zookeeper..." -ForegroundColor Gray
$zkCommand = 'cd /d C:\kafka & title Zookeeper & bin\windows\zookeeper-server-start.bat config\zookeeper.properties'
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $zkCommand -WindowStyle Normal
Write-Host "  ⏳ Waiting for Zookeeper to initialize..." -ForegroundColor Gray
Start-Sleep -Seconds 12

Write-Host "  Starting Kafka Server..." -ForegroundColor Gray
$kafkaCommand = 'cd /d C:\kafka & title Kafka-Server & bin\windows\kafka-server-start.bat config\server.properties'
Start-Process -FilePath "cmd.exe" -ArgumentList "/k", $kafkaCommand -WindowStyle Normal
Write-Host "  ⏳ Waiting for Kafka to start (this takes ~30 seconds)..." -ForegroundColor Gray

# Wait and verify Kafka is actually running
$retryCount = 0
$maxRetries = 12
$kafkaCheck = $null

while (-not $kafkaCheck -and $retryCount -lt $maxRetries) {
    Start-Sleep -Seconds 5
    $kafkaCheck = netstat -ano | findstr ":9092.*LISTENING"
    $retryCount++
    
    if (-not $kafkaCheck -and $retryCount -lt $maxRetries) {
        $elapsed = $retryCount * 5
        Write-Host "  Kafka starting... $elapsed seconds elapsed" -ForegroundColor Gray
    }
}

if ($kafkaCheck) {
    Write-Host "  Kafka is ready on port 9092!" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "  KAFKA FAILED TO START!" -ForegroundColor Red
    Write-Host "  Check the Kafka-Server CMD window for errors." -ForegroundColor Yellow
    Write-Host "  Common issues:" -ForegroundColor Yellow
    Write-Host "    - Zookeeper not running (check Zookeeper window)" -ForegroundColor Gray
    Write-Host "    - Port 9092 still in use by another process" -ForegroundColor Gray
    Write-Host "    - Corrupted Kafka logs (delete C:\kafka\kafka-logs-*)" -ForegroundColor Gray
    Write-Host ""
    Write-Host "  Press Ctrl+C to cancel or wait to continue without Kafka..." -ForegroundColor Red
    Write-Host ""
    Start-Sleep -Seconds 10
}

# Step 4: Start all microservices
Write-Host "Step 4: Starting microservices..." -ForegroundColor Cyan

$services = @(
    @{Name="Evaluation Service"; Port=8081; Path="evaluation-service"},
    @{Name="Sampling Service"; Port=8082; Path="sampling-service"},
    @{Name="Evidence Service"; Port=8083; Path="evidence-service"},
    @{Name="Remediation Service"; Port=8084; Path="remediation-service"},
    @{Name="Jira Service"; Port=8085; Path="jira-service"}
)

foreach ($service in $services) {
    Write-Host "  Starting $($service.Name) on port $($service.Port)..." -ForegroundColor Gray
    Start-Process powershell -ArgumentList @(
        "-NoExit",
        "-Command",
        "cd '$PSScriptRoot\$($service.Path)'; `$host.ui.RawUI.WindowTitle='$($service.Name)'; Write-Host '================================================' -ForegroundColor Cyan; Write-Host 'Starting $($service.Name)' -ForegroundColor Yellow; Write-Host 'Port: $($service.Port)' -ForegroundColor Cyan; Write-Host 'Swagger: http://localhost:$($service.Port)/swagger-ui.html' -ForegroundColor Cyan; Write-Host '================================================' -ForegroundColor Cyan; Write-Host ''; mvn spring-boot:run"
    )
    Start-Sleep -Seconds 2
}

Write-Host "`n  ⏳ Waiting 40 seconds for all services to start...`n" -ForegroundColor Gray
Start-Sleep -Seconds 40

# Step 5: Final status check
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "FINAL STATUS CHECK" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

$kafka = netstat -ano | findstr ":9092.*LISTENING"
if ($kafka) {
    Write-Host "✓ Kafka: RUNNING" -ForegroundColor Green
} else {
    Write-Host "✗ Kafka: NOT RUNNING" -ForegroundColor Red
}

Write-Host ""
$running = 0
foreach ($service in $services) {
    $listening = netstat -ano | findstr ":$($service.Port).*LISTENING"
    if ($listening) {
        Write-Host "✓ $($service.Name): RUNNING on port $($service.Port)" -ForegroundColor Green
        $running++
    } else {
        Write-Host "⚠ $($service.Name): NOT YET READY (may still be starting)" -ForegroundColor Yellow
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
if ($running -eq 5 -and $kafka) {
    Write-Host "🎉 ALL SYSTEMS OPERATIONAL! 🎉" -ForegroundColor Green
    Write-Host "`nService URLs:" -ForegroundColor Cyan
    Write-Host "  Evaluation:   http://localhost:8081/swagger-ui.html" -ForegroundColor White
    Write-Host "  Sampling:     http://localhost:8082/swagger-ui.html" -ForegroundColor White
    Write-Host "  Evidence:     http://localhost:8083/swagger-ui.html" -ForegroundColor White
    Write-Host "  Remediation:  http://localhost:8084/swagger-ui.html" -ForegroundColor White
    Write-Host "  Jira:         http://localhost:8085/swagger-ui.html" -ForegroundColor White
    Write-Host ""
    Write-Host "Test User Creation:" -ForegroundColor Cyan
    Write-Host '  Invoke-RestMethod -Uri "http://localhost:8081/api/users" -Method Post -ContentType "application/json" -Body ''{"username":"test","email":"test@example.com","firstName":"Test","lastName":"User","phoneNumber":"+1234567890","department":"IT","salary":75000}''' -ForegroundColor Gray
} else {
    Write-Host "  $running/5 services running" -ForegroundColor Yellow
    Write-Host "  Wait a bit longer or check service windows for errors." -ForegroundColor Yellow
}
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
