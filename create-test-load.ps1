# Quick Load Test - Create smaller batches for testing
# Use this before running the full million users

param(
    [int]$UserCount = 10000
)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  QUICK LOAD TEST - $UserCount USERS" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

$services = @(
    "http://localhost:8081/api/users",
    "http://localhost:8082/api/users",
    "http://localhost:8083/api/users",
    "http://localhost:8084/api/users",
    "http://localhost:8085/api/users"
)

$departments = @("IT", "HR", "Finance", "Sales")
$startTime = Get-Date
$successCount = 0
$timestamp = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "Creating $UserCount users across 5 services..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop at any time`n" -ForegroundColor Yellow

# Create users sequentially but fast
for ($i = 1; $i -le $UserCount; $i++) {
    try {
        $serviceUrl = $services[$i % 5]
        $dept = $departments[$i % 4]
        $uniqueUsername = "user${timestamp}_$i"
        
        $body = @{
            username = $uniqueUsername
            email = "${uniqueUsername}@loadtest.com"
            firstName = "Test"
            lastName = "User$i"
            phoneNumber = "+1555$('{0:D7}' -f $i)"
            department = $dept
            salary = 60000 + ($i % 50000)
        } | ConvertTo-Json
        
        $null = Invoke-RestMethod -Uri $serviceUrl -Method Post `
            -ContentType "application/json" -Body $body `
            -TimeoutSec 5 -ErrorAction Stop
        
        $successCount++
        
        # Small delay to avoid overwhelming the services
        Start-Sleep -Milliseconds 50
        
        # Update progress every 100 users
        if ($i % 100 -eq 0) {
            $elapsed = (Get-Date) - $startTime
            $rate = if ($elapsed.TotalSeconds -gt 0) { [math]::Round($i / $elapsed.TotalSeconds, 0) } else { 0 }
            Write-Host "`rCreated: $i / $UserCount | Rate: $rate users/sec" -NoNewline -ForegroundColor Cyan
        }
    }
    catch {
        Write-Host "`nError at user $i : $($_.Exception.Message)" -ForegroundColor Red
    }
}

$endTime = Get-Date
$totalTime = $endTime - $startTime

Write-Host "`n`n========================================" -ForegroundColor Cyan
Write-Host "  TEST COMPLETE" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Users Created: $successCount / $UserCount" -ForegroundColor Green
Write-Host "Total Time: $($totalTime.ToString('mm\:ss'))" -ForegroundColor White
Write-Host "Average Rate: $([math]::Round($successCount / $totalTime.TotalSeconds, 0)) users/second" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan
