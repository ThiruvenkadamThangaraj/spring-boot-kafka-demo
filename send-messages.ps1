# PowerShell script to send messages to Kafka with retry logic
$url = "http://localhost:8080/api/kafka/publish"
$totalMessages = 1000
$successCount = 0
$failureCount = 0
$maxRetries = 3
$retryDelayMs = 100
$failedMessages = @()

Write-Host "Starting to send $totalMessages messages to Kafka..." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

$startTime = Get-Date

for ($i = 1; $i -le $totalMessages; $i++) {
    $key = "key-$i"
    $body = @{
        msg = "Message number $i"
        timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        index = $i
    } | ConvertTo-Json
    
    $sent = $false
    $retryCount = 0
    
    while (-not $sent -and $retryCount -le $maxRetries) {
        try {
            $fullUrl = "$url" + "?key=$key"
            $response = Invoke-RestMethod -Uri $fullUrl -Method Post -Body $body -ContentType "application/json" -TimeoutSec 10 -ErrorAction Stop
            $successCount++
            $sent = $true
            
            # Show progress every 1000 messages
            if ($i % 1000 -eq 0) {
                Write-Host "Sent $i messages... (Failed: $failureCount)" -ForegroundColor Yellow
            }
        }
        catch {
            $retryCount++
            if ($retryCount -le $maxRetries) {
                Start-Sleep -Milliseconds $retryDelayMs
            }
            else {
                $failureCount++
                $failedMessages += $i
                Write-Host "Failed to send message $i after $maxRetries retries: $_" -ForegroundColor Red
            }
        }
    }
}

$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Completed!" -ForegroundColor Green
Write-Host "Total messages sent: $successCount" -ForegroundColor Green
Write-Host "Failed messages: $failureCount" -ForegroundColor Red
Write-Host "Duration: $duration seconds" -ForegroundColor Cyan
Write-Host "Messages per second: $([math]::Round($successCount / $duration, 2))" -ForegroundColor Cyan

if ($failedMessages.Count -gt 0) {
    Write-Host "`nFailed message IDs:" -ForegroundColor Red
    Write-Host ($failedMessages -join ", ") -ForegroundColor Red
    
    # Save failed messages to file for retry
    $failedMessages | Out-File -FilePath "failed-messages.txt"
    Write-Host "`nFailed message IDs saved to: failed-messages.txt" -ForegroundColor Yellow
}

Write-Host "========================================" -ForegroundColor Cyan
