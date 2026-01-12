# Demo Script - Test AI Agents
# This script demonstrates the agentic AI capabilities

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "    AI Agents Demo Script" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8081"

Write-Host "`n1. Checking AI Agents Status..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/health" -Method Get
    Write-Host "✅ Agents Status:" -ForegroundColor Green
    $response | ConvertTo-Json -Depth 2
} catch {
    Write-Host "⚠️  Could not reach agents. Make sure services are running." -ForegroundColor Red
    Write-Host "   Run: .\start-all.ps1" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n2. Creating Normal User (Should be allowed)..." -ForegroundColor Yellow
$normalUser = @{
    username = "john_doe"
    email = "john.doe@company.com"
    firstName = "John"
    lastName = "Doe"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/evaluations/users" -Method Post -Body $normalUser -ContentType "application/json"
    Write-Host "✅ Normal user created successfully" -ForegroundColor Green
    Start-Sleep -Seconds 2
} catch {
    Write-Host "⚠️  Error creating user: $_" -ForegroundColor Red
}

Write-Host "`n3. Creating Suspicious User (Should be quarantined)..." -ForegroundColor Yellow
$suspiciousUser = @{
    username = "test12345"
    email = "test99999@tempmail.com"
    firstName = "Test"
    lastName = "Bot"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "$baseUrl/api/evaluations/users" -Method Post -Body $suspiciousUser -ContentType "application/json"
    Write-Host "✅ Suspicious user created (check logs for quarantine)" -ForegroundColor Green
    Start-Sleep -Seconds 2
} catch {
    Write-Host "⚠️  Error creating user: $_" -ForegroundColor Red
}

Write-Host "`n4. Creating Users with Unusual Patterns..." -ForegroundColor Yellow
$unusualUsers = @(
    @{ username = "aaaaaaa"; email = "spam@guerrillamail.com"; firstName = "A"; lastName = "A" },
    @{ username = "12345678"; email = "bot123@mailinator.com"; firstName = "Bot"; lastName = "User" },
    @{ username = "qwxyz"; email = "fake456@throwaway.email"; firstName = "Fake"; lastName = "Person" }
)

foreach ($user in $unusualUsers) {
    $json = $user | ConvertTo-Json
    try {
        Invoke-RestMethod -Uri "$baseUrl/api/evaluations/users" -Method Post -Body $json -ContentType "application/json" | Out-Null
        Write-Host "  📧 Created: $($user.username)" -ForegroundColor Gray
        Start-Sleep -Milliseconds 500
    } catch {
        Write-Host "  ⚠️  Error: $($user.username)" -ForegroundColor Red
    }
}

Write-Host "`n5. Getting Anomaly Detection Statistics..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
try {
    $stats = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/anomaly-detection/stats" -Method Get
    Write-Host "✅ Anomaly Detection Stats:" -ForegroundColor Green
    $stats | ConvertTo-Json -Depth 2
} catch {
    Write-Host "⚠️  Could not retrieve stats" -ForegroundColor Red
}

Write-Host "`n6. Getting All Agents Status..." -ForegroundColor Yellow
try {
    $allStatus = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/status" -Method Get
    Write-Host "✅ All Agents Status:" -ForegroundColor Green
    $allStatus | ConvertTo-Json -Depth 3
} catch {
    Write-Host "⚠️  Could not retrieve status" -ForegroundColor Red
}

Write-Host "`n7. Testing Self-Healing Agent (Simulated Health Check)..." -ForegroundColor Yellow
$healthCheckData = @{
    serviceName = "evaluation-service"
    serviceUp = $true
    errorRate = 0.12
    avgResponseTime = 450.0
} | ConvertTo-Json

try {
    $healthResponse = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/self-healing/health-check" -Method Post -Body $healthCheckData -ContentType "application/json"
    Write-Host "✅ Health Check Decision:" -ForegroundColor Green
    $healthResponse.decision | ConvertTo-Json -Depth 2
} catch {
    Write-Host "⚠️  Health check failed" -ForegroundColor Red
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "    Demo Complete!" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "`nCheck the service logs to see AI agent decisions in action:" -ForegroundColor Yellow
Write-Host "  - Anomaly detections (🔍)" -ForegroundColor White
Write-Host "  - Quarantine decisions (🚨)" -ForegroundColor White
Write-Host "  - Jira tickets created (🎫)" -ForegroundColor White
Write-Host "  - Self-healing actions (🔧)" -ForegroundColor White
Write-Host "`nView logs:" -ForegroundColor Yellow
Write-Host "  docker-compose logs -f evaluation-service | grep -E '(🤖|🔍|🚨|🎫|🔧)'" -ForegroundColor Gray
