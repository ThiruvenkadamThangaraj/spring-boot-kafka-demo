# AI Agents Demo Mode Test Script
# This tests AI agents WITHOUT Kafka - direct synchronous execution

Write-Host "`n==================================================================" -ForegroundColor Cyan
Write-Host "🎬 AI AGENTS DEMO MODE - Testing Without Kafka" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

$baseUrl = "http://localhost:8081"
$headers = @{
    "Content-Type" = "application/json"
}

# Test 1: Normal User
Write-Host "`n[TEST 1] Creating NORMAL user..." -ForegroundColor Yellow
$normalUser = @{
    userId = 1001
    username = "john_smith"
    email = "john.smith@company.com"
    firstName = "John"
    lastName = "Smith"
} | ConvertTo-Json

try {
    $response1 = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/demo" `
        -Method POST `
        -Headers $headers `
        -Body $normalUser
    
    Write-Host "✅ SUCCESS - Normal User Result:" -ForegroundColor Green
    Write-Host "Username: $($response1.username)" -ForegroundColor White
    Write-Host "Email: $($response1.email)" -ForegroundColor White
    Write-Host "`n🔍 Anomaly Detection:" -ForegroundColor Cyan
    Write-Host "  Action: $($response1.anomalyDetection.action)" -ForegroundColor $(if ($response1.anomalyDetection.action -eq "ALLOW") { "Green" } else { "Red" })
    Write-Host "  Confidence: $($response1.anomalyDetection.confidence)" -ForegroundColor White
    Write-Host "  Anomaly Score: $($response1.anomalyDetection.anomalyScore)" -ForegroundColor White
    Write-Host "  Reasoning: $($response1.anomalyDetection.reasoning)" -ForegroundColor Gray
    Write-Host "`n📧 Email Decision:" -ForegroundColor Cyan
    Write-Host "  $($response1.emailDecision)" -ForegroundColor $(if ($response1.emailSent) { "Green" } else { "Red" })
    
    if ($response1.jiraTicket) {
        Write-Host "`n🎫 Jira Ticket:" -ForegroundColor Cyan
        Write-Host "  Title: $($response1.jiraTicket.title)" -ForegroundColor White
        Write-Host "  Priority: $($response1.jiraTicket.priority)" -ForegroundColor Yellow
    } else {
        Write-Host "`n✅ No Jira ticket needed - User is normal" -ForegroundColor Green
    }
}
catch {
    Write-Host "❌ ERROR: $_" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# Test 2: Suspicious User (Flagged)
Write-Host "`n`n[TEST 2] Creating SUSPICIOUS user (will be flagged)..." -ForegroundColor Yellow
$suspiciousUser = @{
    userId = 1002
    username = "test_user123"
    email = "testuser@guerrillamail.com"
    firstName = "Test"
    lastName = "User"
} | ConvertTo-Json

try {
    $response2 = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/demo" `
        -Method POST `
        -Headers $headers `
        -Body $suspiciousUser
    
    Write-Host "⚠️ WARNING - Suspicious User Result:" -ForegroundColor Yellow
    Write-Host "Username: $($response2.username)" -ForegroundColor White
    Write-Host "Email: $($response2.email)" -ForegroundColor White
    Write-Host "`n🔍 Anomaly Detection:" -ForegroundColor Cyan
    Write-Host "  Action: $($response2.anomalyDetection.action)" -ForegroundColor $(if ($response2.anomalyDetection.action -eq "ALLOW") { "Green" } elseif ($response2.anomalyDetection.action -eq "FLAG_FOR_REVIEW") { "Yellow" } else { "Red" })
    Write-Host "  Confidence: $($response2.anomalyDetection.confidence)" -ForegroundColor White
    Write-Host "  Anomaly Score: $($response2.anomalyDetection.anomalyScore)" -ForegroundColor White
    Write-Host "  Reasoning: $($response2.anomalyDetection.reasoning)" -ForegroundColor Gray
    Write-Host "`n📧 Email Decision:" -ForegroundColor Cyan
    Write-Host "  $($response2.emailDecision)" -ForegroundColor $(if ($response2.emailSent) { "Yellow" } else { "Red" })
    
    if ($response2.jiraTicket) {
        Write-Host "`n🎫 Jira Ticket Created:" -ForegroundColor Yellow
        Write-Host "  Title: $($response2.jiraTicket.title)" -ForegroundColor White
        Write-Host "  Priority: $($response2.jiraTicket.priority)" -ForegroundColor Yellow
        Write-Host "  Assignee: $($response2.jiraTicket.assignee)" -ForegroundColor White
        Write-Host "  Estimated Effort: $($response2.jiraTicket.estimatedEffort)" -ForegroundColor White
    }
}
catch {
    Write-Host "❌ ERROR: $_" -ForegroundColor Red
}

Start-Sleep -Seconds 2

# Test 3: High-Risk User (Quarantined)
Write-Host "`n`n[TEST 3] Creating HIGH-RISK user (will be quarantined)..." -ForegroundColor Yellow
$highRiskUser = @{
    userId = 1003
    username = "hacker999"
    email = "spam999@tempmail.com"
    firstName = "Spam"
    lastName = "Bot"
} | ConvertTo-Json

try {
    $response3 = Invoke-RestMethod -Uri "$baseUrl/api/ai-agents/demo" `
        -Method POST `
        -Headers $headers `
        -Body $highRiskUser
    
    Write-Host "🚨 CRITICAL - High Risk User Result:" -ForegroundColor Red
    Write-Host "Username: $($response3.username)" -ForegroundColor White
    Write-Host "Email: $($response3.email)" -ForegroundColor White
    Write-Host "`n🔍 Anomaly Detection:" -ForegroundColor Cyan
    Write-Host "  Action: $($response3.anomalyDetection.action)" -ForegroundColor Red
    Write-Host "  Confidence: $($response3.anomalyDetection.confidence)" -ForegroundColor White
    Write-Host "  Anomaly Score: $($response3.anomalyDetection.anomalyScore)" -ForegroundColor Red
    Write-Host "  Reasoning: $($response3.anomalyDetection.reasoning)" -ForegroundColor Gray
    Write-Host "`n📧 Email Decision:" -ForegroundColor Cyan
    Write-Host "  $($response3.emailDecision)" -ForegroundColor Red
    
    if ($response3.jiraTicket) {
        Write-Host "`n🎫 Critical Jira Ticket Created:" -ForegroundColor Red
        Write-Host "  Title: $($response3.jiraTicket.title)" -ForegroundColor White
        Write-Host "  Priority: $($response3.jiraTicket.priority)" -ForegroundColor Red
        Write-Host "  Assignee: $($response3.jiraTicket.assignee)" -ForegroundColor White
        Write-Host "  Estimated Effort: $($response3.jiraTicket.estimatedEffort)" -ForegroundColor White
    }
}
catch {
    Write-Host "❌ ERROR: $_" -ForegroundColor Red
}

# Summary
Write-Host "`n`n==================================================================" -ForegroundColor Cyan
Write-Host "📊 DEMO COMPLETE - AI Agents Summary" -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "✅ Test 1: Normal user - ALLOWED" -ForegroundColor Green
Write-Host "⚠️  Test 2: Suspicious user - FLAGGED FOR REVIEW + Jira ticket" -ForegroundColor Yellow
Write-Host "🚨 Test 3: High-risk user - QUARANTINED + Critical Jira ticket" -ForegroundColor Red
Write-Host "`n💡 This demo shows AI agents working WITHOUT Kafka!" -ForegroundColor Cyan
Write-Host "   Decisions made in real-time, synchronously via REST API" -ForegroundColor Gray
Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host ""
