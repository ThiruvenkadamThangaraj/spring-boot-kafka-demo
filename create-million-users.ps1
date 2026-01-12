# Create 1 Million Users - Load Test Script
# Distributes load across all 5 microservices

param(
    [int]$TotalUsers = 1000000,
    [int]$BatchSize = 1000,
    [int]$ParallelBatches = 10
)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  CREATING $TotalUsers USERS" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Batch Size: $BatchSize users per batch" -ForegroundColor White
Write-Host "Parallel Batches: $ParallelBatches" -ForegroundColor White
Write-Host "Services: 5 (ports 8081-8085)" -ForegroundColor White
Write-Host "========================================`n" -ForegroundColor Cyan

# Service URLs
$services = @(
    "http://localhost:8081/api/users",
    "http://localhost:8082/api/users",
    "http://localhost:8083/api/users",
    "http://localhost:8084/api/users",
    "http://localhost:8085/api/users"
)

$departments = @("IT", "HR", "Finance", "Sales", "Marketing", "Engineering", "Operations", "Support")
$startTime = Get-Date
$successCount = 0
$errorCount = 0
$totalBatches = [math]::Ceiling($TotalUsers / $BatchSize)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"

Write-Host "Starting load test at $(Get-Date -Format 'HH:mm:ss')`n" -ForegroundColor Green

# Function to create a batch of users
$createBatchScript = {
    param($StartIndex, $EndIndex, $Services, $Departments, $Timestamp)
    
    $results = @{
        Success = 0
        Errors = 0
    }
    
    for ($i = $StartIndex; $i -le $EndIndex; $i++) {
        try {
            # Round-robin across services
            $serviceUrl = $Services[$i % $Services.Count]
            $dept = $Departments[$i % $Departments.Count]
            $uniqueUsername = "user${Timestamp}_$i"
            
            $body = @{
                username = $uniqueUsername
                email = "${uniqueUsername}@loadtest.com"
                firstName = "User"
                lastName = "$i"
                phoneNumber = "+1234567$('{0:D4}' -f ($i % 10000))"
                department = $dept
                salary = 50000 + ($i % 100000)
            } | ConvertTo-Json
            
            $response = Invoke-RestMethod -Uri $serviceUrl -Method Post `
                -ContentType "application/json" -Body $body `
                -TimeoutSec 5 -ErrorAction Stop
            
            $results.Success++
        }
        catch {
            $results.Errors++
        }
    }
    
    return $results
}

# Create users in parallel batches
$batchIndex = 0
$jobs = @()

for ($i = 0; $i -lt $TotalUsers; $i += $BatchSize) {
    $startIndex = $i
    $endIndex = [Math]::Min($i + $BatchSize - 1, $TotalUsers - 1)
    $batchIndex++
    
    # Start a new batch job
    $job = Start-Job -ScriptBlock $createBatchScript -ArgumentList $startIndex, $endIndex, $services, $departments, $timestamp
    $jobs += $job
    
    # Wait if we have too many parallel jobs
    if ($jobs.Count -ge $ParallelBatches) {
        $completed = $jobs | Wait-Job -Any
        $result = $completed | Receive-Job
        $successCount += $result.Success
        $errorCount += $result.Errors
        
        $jobs = $jobs | Where-Object { $_.Id -ne $completed.Id }
        Remove-Job $completed
        
        # Progress update
        $elapsed = (Get-Date) - $startTime
        $usersCreated = $successCount + $errorCount
        $rate = if ($elapsed.TotalSeconds -gt 0) { [math]::Round($usersCreated / $elapsed.TotalSeconds, 0) } else { 0 }
        $percentComplete = [math]::Round(($usersCreated / $TotalUsers) * 100, 2)
        
        Write-Host "`rProgress: $percentComplete% | Created: $usersCreated / $TotalUsers | Rate: $rate users/sec | Success: $successCount | Errors: $errorCount" -NoNewline -ForegroundColor Cyan
    }
}

# Wait for remaining jobs
Write-Host "`n`nWaiting for remaining batches to complete..." -ForegroundColor Yellow
$jobs | Wait-Job | ForEach-Object {
    $result = $_ | Receive-Job
    $successCount += $result.Success
    $errorCount += $result.Errors
    Remove-Job $_
}

$endTime = Get-Date
$totalTime = $endTime - $startTime

Write-Host "`n`n========================================" -ForegroundColor Cyan
Write-Host "  LOAD TEST COMPLETE" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Total Users Created: $successCount" -ForegroundColor Green
Write-Host "Failed Requests: $errorCount" -ForegroundColor Red
Write-Host "Total Time: $($totalTime.ToString('hh\:mm\:ss'))" -ForegroundColor White
Write-Host "Average Rate: $([math]::Round($successCount / $totalTime.TotalSeconds, 0)) users/second" -ForegroundColor Yellow
Write-Host "========================================`n" -ForegroundColor Cyan

Write-Host "Check service console windows for Kafka events and email notifications!`n" -ForegroundColor Magenta
