# Test CompletableFuture Async APIs
# Tests IO and CPU task separation

param(
    [string]$ServiceUrl = "http://localhost:8081"
)

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  COMPLETABLE FUTURE API TESTS" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Service URL: $ServiceUrl" -ForegroundColor White
Write-Host "========================================`n" -ForegroundColor Cyan

# Test counter
$testsPassed = 0
$testsFailed = 0

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [object]$Body = $null
    )
    
    Write-Host "Testing: $Name" -ForegroundColor Cyan
    Write-Host "  Endpoint: $Method $Url" -ForegroundColor Gray
    
    try {
        $params = @{
            Uri = $Url
            Method = $Method
            ContentType = "application/json"
        }
        
        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json)
            Write-Host "  Body: $($params.Body)" -ForegroundColor Gray
        }
        
        $startTime = Get-Date
        $response = Invoke-RestMethod @params
        $endTime = Get-Date
        $duration = ($endTime - $startTime).TotalMilliseconds
        
        Write-Host "  ✅ SUCCESS" -ForegroundColor Green
        Write-Host "  Duration: ${duration}ms" -ForegroundColor Gray
        Write-Host "  Response: $($response.message)" -ForegroundColor Gray
        
        if ($response.data) {
            $dataType = $response.data.GetType().Name
            if ($dataType -eq "Object[]") {
                Write-Host "  Data: Array with $($response.data.Count) items" -ForegroundColor Gray
            } else {
                Write-Host "  Data: $dataType" -ForegroundColor Gray
            }
        }
        
        Write-Host ""
        $script:testsPassed++
        return $response
    }
    catch {
        Write-Host "  ❌ FAILED: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host ""
        $script:testsFailed++
        return $null
    }
}

# 1. Test Demo Service Health
Write-Host "=== DEMO SERVICE TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Demo Health Check" -Method "GET" -Url "$ServiceUrl/api/demo/health"
Test-Endpoint -Name "Demo Info" -Method "GET" -Url "$ServiceUrl/api/demo/info"

# 2. Test Pure IO Task
Write-Host "=== IO TASK TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Pure IO Task (Database Fetch)" -Method "GET" -Url "$ServiceUrl/api/demo/io-task"

# 3. Test Pure CPU Task
Write-Host "=== CPU TASK TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Pure CPU Task (Data Processing)" -Method "GET" -Url "$ServiceUrl/api/demo/cpu-task"

# 4. Test Complete Workflow (IO → CPU → IO)
Write-Host "=== WORKFLOW TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Complete Workflow (IO → CPU → IO)" -Method "GET" -Url "$ServiceUrl/api/demo/workflow"

# 5. Test Parallel IO Operations
Write-Host "=== PARALLEL EXECUTION TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Parallel IO Operations" -Method "GET" -Url "$ServiceUrl/api/demo/parallel-io"

# 6. Test Fan-Out Pattern
Write-Host "=== FAN-OUT PATTERN TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Fan-Out Pattern (1 IO → Multiple CPU)" -Method "GET" -Url "$ServiceUrl/api/demo/fan-out"

# 7. Test Complex Pipeline
Write-Host "=== COMPLEX PIPELINE TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Complex Pipeline" -Method "GET" -Url "$ServiceUrl/api/demo/pipeline"

# 8. Test Error Handling
Write-Host "=== ERROR HANDLING TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Error Handling" -Method "GET" -Url "$ServiceUrl/api/demo/error-handling"

# 9. Test API Call
Write-Host "=== EXTERNAL API TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "External API Call" -Method "GET" -Url "$ServiceUrl/api/demo/api-call?endpoint=/api/test"

# 10. Test Data Aggregation
Write-Host "=== AGGREGATION TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Data Aggregation (CPU Task)" -Method "GET" -Url "$ServiceUrl/api/demo/aggregate"

# 11. Test Async User Service
Write-Host "=== ASYNC USER SERVICE TESTS ===" -ForegroundColor Yellow
Test-Endpoint -Name "Async User Service Health" -Method "GET" -Url "$ServiceUrl/api/async/users/health"

# 12. Test Get All Users (Async)
$users = Test-Endpoint -Name "Get All Users (Async IO + CPU)" -Method "GET" -Url "$ServiceUrl/api/async/users"

# 13. Test Get User by ID (Async)
if ($users -and $users.data -and $users.data.Count -gt 0) {
    $userId = $users.data[0].id
    Test-Endpoint -Name "Get User by ID (Async)" -Method "GET" -Url "$ServiceUrl/api/async/users/$userId"
    Test-Endpoint -Name "Get User with Processing (Async)" -Method "GET" -Url "$ServiceUrl/api/async/users/$userId/processed"
}

# 14. Test Create User (Async with Parallel Validations)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$newUser = @{
    username = "async_user_$timestamp"
    email = "async_$timestamp@example.com"
    firstName = "Async"
    lastName = "User"
    phoneNumber = "+1234567890"
    department = "IT"
    salary = 75000
}

$createdUser = Test-Endpoint -Name "Create User (Async Multi-Step)" -Method "POST" -Url "$ServiceUrl/api/async/users" -Body $newUser

# 15. Test Update User (Async)
if ($createdUser -and $createdUser.data) {
    $updateData = @{
        firstName = "Updated"
        lastName = "Async"
        phoneNumber = "+9876543210"
        department = "Engineering"
        salary = 85000
    }
    Test-Endpoint -Name "Update User (Async)" -Method "PUT" -Url "$ServiceUrl/api/async/users/$($createdUser.data.id)" -Body $updateData
}

# 16. Test Batch Processing (Async Parallel)
if ($users -and $users.data -and $users.data.Count -ge 3) {
    $userIds = @($users.data[0].id, $users.data[1].id, $users.data[2].id)
    Test-Endpoint -Name "Batch Processing (Async Parallel IO)" -Method "POST" -Url "$ServiceUrl/api/async/users/batch" -Body $userIds
}

# 17. Test Delete User (Async)
if ($createdUser -and $createdUser.data) {
    Test-Endpoint -Name "Delete User (Async)" -Method "DELETE" -Url "$ServiceUrl/api/async/users/$($createdUser.data.id)"
}

# 18. Test Concurrent Requests
Write-Host "=== CONCURRENT REQUEST TESTS ===" -ForegroundColor Yellow
Write-Host "Sending 10 concurrent requests to test thread pool..." -ForegroundColor Cyan

$jobs = @()
1..10 | ForEach-Object {
    $jobs += Start-Job -ScriptBlock {
        param($url, $index)
        try {
            $response = Invoke-RestMethod -Uri "$url/api/demo/workflow" -Method GET
            return @{ Success = $true; Index = $index; Duration = 0 }
        } catch {
            return @{ Success = $false; Index = $index; Error = $_.Exception.Message }
        }
    } -ArgumentList $ServiceUrl, $_
}

Write-Host "Waiting for concurrent requests to complete..." -ForegroundColor Gray
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job

$successCount = ($results | Where-Object { $_.Success }).Count
Write-Host "  ✅ $successCount/10 concurrent requests succeeded" -ForegroundColor Green

if ($successCount -eq 10) {
    $script:testsPassed++
} else {
    $script:testsFailed++
}
Write-Host ""

# Summary
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY" -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Tests Passed: $testsPassed" -ForegroundColor Green
Write-Host "Tests Failed: $testsFailed" -ForegroundColor Red
Write-Host "Total Tests: $($testsPassed + $testsFailed)" -ForegroundColor White
Write-Host "========================================`n" -ForegroundColor Cyan

if ($testsFailed -eq 0) {
    Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "❌ SOME TESTS FAILED" -ForegroundColor Red
}

Write-Host "`nNote: Check service console for thread pool activity logs" -ForegroundColor Magenta
Write-Host "Look for thread names like:" -ForegroundColor Magenta
Write-Host "  - IO-Task-X (for I/O operations)" -ForegroundColor Gray
Write-Host "  - CPU-Task-X (for CPU operations)" -ForegroundColor Gray
Write-Host ""
