#!/usr/bin/env pwsh

# Test Audit Service Integration
# This script tests the complete audit logging flow across all microservices

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Audit Service Integration Test         " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$ErrorActionPreference = "Continue"

# Service URLs
$AUDIT_SERVICE = "http://localhost:8086"
$EVALUATION_SERVICE = "http://localhost:8081"
$SAMPLING_SERVICE = "http://localhost:8082"
$EVIDENCE_SERVICE = "http://localhost:8083"
$REMEDIATION_SERVICE = "http://localhost:8084"
$JIRA_SERVICE = "http://localhost:8085"

# Generate a unique correlation ID for distributed tracing
$CORRELATION_ID = [guid]::NewGuid().ToString()

Write-Host "Test Configuration:" -ForegroundColor Yellow
Write-Host "  Correlation ID: $CORRELATION_ID" -ForegroundColor White
Write-Host ""

# Function to check service health
function Test-ServiceHealth {
    param (
        [string]$ServiceName,
        [string]$HealthUrl
    )
    
    Write-Host "Checking $ServiceName..." -NoNewline
    try {
        $response = Invoke-WebRequest -Uri $HealthUrl -Method GET -TimeoutSec 5 -ErrorAction Stop
        if ($response.StatusCode -eq 200) {
            Write-Host " ✓" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host " ❌ (Not running)" -ForegroundColor Red
        return $false
    }
}

# Function to make API call with correlation ID
function Invoke-AuditedApiCall {
    param (
        [string]$ServiceName,
        [string]$Url,
        [string]$Method = "GET",
        [hashtable]$Body = @{},
        [string]$CorrelationId
    )
    
    Write-Host "  → $Method $Url" -ForegroundColor Cyan
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-Correlation-ID" = $CorrelationId
        "X-User-Name" = "test-user"
    }
    
    try {
        if ($Method -eq "GET") {
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -TimeoutSec 10
        } else {
            $jsonBody = $Body | ConvertTo-Json
            $response = Invoke-RestMethod -Uri $Url -Method $Method -Headers $headers -Body $jsonBody -TimeoutSec 10
        }
        Write-Host "    Status: 200 OK" -ForegroundColor Green
        return $true
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        Write-Host "    Status: $statusCode" -ForegroundColor Yellow
        return $false
    }
}

# Step 1: Check all services are running
Write-Host "Step 1: Checking Service Health" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""

$servicesRunning = @{
    "Audit Service" = Test-ServiceHealth "Audit Service" "$AUDIT_SERVICE/api/audit/health"
    "Evaluation Service" = Test-ServiceHealth "Evaluation Service" "$EVALUATION_SERVICE/api/evaluations/health"
    "Sampling Service" = Test-ServiceHealth "Sampling Service" "$SAMPLING_SERVICE/api/sampling/health"
    "Evidence Service" = Test-ServiceHealth "Evidence Service" "$EVIDENCE_SERVICE/api/evidence/health"
    "Remediation Service" = Test-ServiceHealth "Remediation Service" "$REMEDIATION_SERVICE/api/remediation/health"
    "Jira Service" = Test-ServiceHealth "Jira Service" "$JIRA_SERVICE/api/jira/health"
}

$allRunning = $servicesRunning.Values | Where-Object { $_ -eq $false } | Measure-Object | Select-Object -ExpandProperty Count
if ($allRunning -gt 0) {
    Write-Host ""
    Write-Host "⚠ Warning: Not all services are running!" -ForegroundColor Yellow
    Write-Host "Please start all services before running this test." -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Continue anyway? (y/n)"
    if ($response -ne 'y' -and $response -ne 'Y') {
        exit 0
    }
}

Write-Host ""

# Step 2: Make test API calls to each service
Write-Host "Step 2: Making Test API Calls" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "Testing Evaluation Service..." -ForegroundColor Yellow
if ($servicesRunning["Evaluation Service"]) {
    Invoke-AuditedApiCall -ServiceName "Evaluation" -Url "$EVALUATION_SERVICE/api/evaluations" -Method "GET" -CorrelationId $CORRELATION_ID
    Invoke-AuditedApiCall -ServiceName "Evaluation" -Url "$EVALUATION_SERVICE/api/evaluations/1" -Method "GET" -CorrelationId $CORRELATION_ID
}
Write-Host ""

Write-Host "Testing Sampling Service..." -ForegroundColor Yellow
if ($servicesRunning["Sampling Service"]) {
    Invoke-AuditedApiCall -ServiceName "Sampling" -Url "$SAMPLING_SERVICE/api/sampling" -Method "GET" -CorrelationId $CORRELATION_ID
}
Write-Host ""

Write-Host "Testing Evidence Service..." -ForegroundColor Yellow
if ($servicesRunning["Evidence Service"]) {
    Invoke-AuditedApiCall -ServiceName "Evidence" -Url "$EVIDENCE_SERVICE/api/evidence" -Method "GET" -CorrelationId $CORRELATION_ID
}
Write-Host ""

Write-Host "Testing Remediation Service..." -ForegroundColor Yellow
if ($servicesRunning["Remediation Service"]) {
    Invoke-AuditedApiCall -ServiceName "Remediation" -Url "$REMEDIATION_SERVICE/api/remediation" -Method "GET" -CorrelationId $CORRELATION_ID
}
Write-Host ""

Write-Host "Testing Jira Service..." -ForegroundColor Yellow
if ($servicesRunning["Jira Service"]) {
    Invoke-AuditedApiCall -ServiceName "Jira" -Url "$JIRA_SERVICE/api/jira" -Method "GET" -CorrelationId $CORRELATION_ID
}
Write-Host ""

# Step 3: Wait for Kafka processing
Write-Host "Step 3: Waiting for Kafka Processing" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Waiting 5 seconds for audit events to be processed..." -ForegroundColor Yellow
Start-Sleep -Seconds 5
Write-Host "✓ Done" -ForegroundColor Green
Write-Host ""

# Step 4: Query audit logs
Write-Host "Step 4: Querying Audit Logs" -ForegroundColor Cyan
Write-Host "=================================" -ForegroundColor Cyan
Write-Host ""

if ($servicesRunning["Audit Service"]) {
    
    # Query all audit logs
    Write-Host "1. Fetching all audit logs (last 10)..." -ForegroundColor Yellow
    try {
        $allLogs = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit?page=0&size=10" -Method GET
        $logCount = $allLogs.totalElements
        Write-Host "   Total audit logs: $logCount" -ForegroundColor Green
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch audit logs" -ForegroundColor Red
        Write-Host ""
    }
    
    # Query by correlation ID (distributed tracing)
    Write-Host "2. Fetching logs by Correlation ID (distributed trace)..." -ForegroundColor Yellow
    try {
        $traceLogs = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit/trace/$CORRELATION_ID" -Method GET
        $traceCount = $traceLogs.Count
        Write-Host "   Found $traceCount requests in this trace:" -ForegroundColor Green
        
        if ($traceCount -gt 0) {
            foreach ($log in $traceLogs) {
                $service = $log.serviceName
                $method = $log.httpMethod
                $endpoint = $log.endpoint
                $status = $log.httpStatusCode
                $duration = $log.executionTimeMs
                Write-Host "   - $service | $method $endpoint | Status: $status | Duration: ${duration}ms" -ForegroundColor White
            }
        }
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch trace logs" -ForegroundColor Red
        Write-Host ""
    }
    
    # Query by service name
    Write-Host "3. Fetching logs by service name (evaluation-service)..." -ForegroundColor Yellow
    try {
        $serviceLogs = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit/service/evaluation-service?page=0&size=5" -Method GET
        $serviceLogCount = $serviceLogs.totalElements
        Write-Host "   Total logs for evaluation-service: $serviceLogCount" -ForegroundColor Green
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch service logs" -ForegroundColor Red
        Write-Host ""
    }
    
    # Query failed requests
    Write-Host "4. Fetching failed requests..." -ForegroundColor Yellow
    try {
        $failedLogs = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit/failures?page=0&size=5" -Method GET
        $failedCount = $failedLogs.totalElements
        Write-Host "   Total failed requests: $failedCount" -ForegroundColor Green
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch failed request logs" -ForegroundColor Red
        Write-Host ""
    }
    
    # Query slow requests
    Write-Host "5. Fetching slow requests (>1000ms)..." -ForegroundColor Yellow
    try {
        $slowLogs = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit/slow?threshold=1000&page=0&size=5" -Method GET
        $slowCount = $slowLogs.totalElements
        Write-Host "   Total slow requests: $slowCount" -ForegroundColor Green
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch slow request logs" -ForegroundColor Red
        Write-Host ""
    }
    
    # Get statistics
    Write-Host "6. Fetching service statistics..." -ForegroundColor Yellow
    try {
        $stats = Invoke-RestMethod -Uri "$AUDIT_SERVICE/api/audit/statistics" -Method GET
        Write-Host "   Service Statistics:" -ForegroundColor Green
        foreach ($stat in $stats) {
            $service = $stat.serviceName
            $count = $stat.totalRequests
            $avgDuration = [math]::Round($stat.avgExecutionTimeMs, 2)
            $successRate = [math]::Round($stat.successRate, 2)
            Write-Host "   - $service | Requests: $count | Avg Duration: ${avgDuration}ms | Success Rate: ${successRate}%" -ForegroundColor White
        }
        Write-Host ""
    } catch {
        Write-Host "   ❌ Failed to fetch statistics" -ForegroundColor Red
        Write-Host ""
    }
    
} else {
    Write-Host "⚠ Audit service is not running. Skipping audit log queries." -ForegroundColor Yellow
    Write-Host ""
}

# Summary
Write-Host "==========================================" -ForegroundColor Green
Write-Host "  Test Complete!                          " -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Access audit logs via:" -ForegroundColor Cyan
Write-Host "  - All logs: $AUDIT_SERVICE/api/audit" -ForegroundColor White
Write-Host "  - By service: $AUDIT_SERVICE/api/audit/service/{serviceName}" -ForegroundColor White
Write-Host "  - By user: $AUDIT_SERVICE/api/audit/user/{username}" -ForegroundColor White
Write-Host "  - Distributed trace: $AUDIT_SERVICE/api/audit/trace/{correlationId}" -ForegroundColor White
Write-Host "  - Failures: $AUDIT_SERVICE/api/audit/failures" -ForegroundColor White
Write-Host "  - Slow requests: $AUDIT_SERVICE/api/audit/slow?threshold=1000" -ForegroundColor White
Write-Host "  - Statistics: $AUDIT_SERVICE/api/audit/statistics" -ForegroundColor White
Write-Host ""
Write-Host "Correlation ID for this test: $CORRELATION_ID" -ForegroundColor Yellow
Write-Host ""
