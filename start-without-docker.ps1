# Start all microservices without Docker
# This script opens separate windows for each service

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Starting All Microservices..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$services = @(
    @{Name="Evaluation"; Port=8081; Path="evaluation-service"},
    @{Name="Sampling"; Port=8082; Path="sampling-service"},
    @{Name="Evidence"; Port=8083; Path="evidence-service"},
    @{Name="Remediation"; Port=8084; Path="remediation-service"},
    @{Name="Jira"; Port=8085; Path="jira-service"}
)

Write-Host "`nStarting 5 microservices in separate windows..." -ForegroundColor Yellow

foreach ($service in $services) {
    $servicePath = Join-Path $PSScriptRoot $service.Path
    Write-Host "Starting $($service.Name) Service on port $($service.Port)..." -ForegroundColor White
    
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$servicePath'; Write-Host 'Starting $($service.Name) Service...' -ForegroundColor Green; mvn spring-boot:run"
    Start-Sleep -Seconds 2
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "All services are starting!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

Write-Host "`nService URLs (wait 30-60 seconds for all to start):" -ForegroundColor Cyan
Write-Host "  Evaluation Service:   http://localhost:8081/swagger-ui.html" -ForegroundColor White
Write-Host "  Sampling Service:     http://localhost:8082/swagger-ui.html" -ForegroundColor White
Write-Host "  Evidence Service:     http://localhost:8083/swagger-ui.html" -ForegroundColor White
Write-Host "  Remediation Service:  http://localhost:8084/swagger-ui.html" -ForegroundColor White
Write-Host "  Jira Service:         http://localhost:8085/swagger-ui.html" -ForegroundColor White

Write-Host "`nTo stop all services: Close all the PowerShell windows" -ForegroundColor Yellow
Write-Host "Press any key to exit this window..." -ForegroundColor Gray
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")
