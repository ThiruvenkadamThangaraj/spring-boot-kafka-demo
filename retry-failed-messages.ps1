# PowerShell script to retry failed messages
$url = "http://localhost:8080/api/kafka/publish"
$failedFile = "failed-messages.txt"

if (-not (Test-Path $failedFile)) {
    Write-Host "No failed messages file found: $failedFile" -ForegroundColor Yellow
    exit
}

$failedIds = Get-Content $failedFile
$totalFailed = $failedIds.Count
$successCount = 0
$stillFailedCount = 0
$stillFailed = @()

Write-Host "Retrying $totalFailed failed messages..." -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan

foreach ($id in $failedIds) {
    $key = "key-$id"
    $body = @{
        msg = "Message number $id (RETRY)"
        timestamp = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
        index = $id
    } | ConvertTo-Json
    
    $maxRetries = 5
    $sent = $false
    
    for ($retry = 1; $retry -le $maxRetries; $retry++) {
        try {
            $fullUrl = "$url" + "?key=$key"
            $response = Invoke-RestMethod -Uri $fullUrl -Method Post -Body $body -ContentType "application/json" -TimeoutSec 30 -ErrorAction Stop
            $successCount++
            $sent = $true
            Write-Host "OK Successfully sent message $id" -ForegroundColor Green
            break
        }
        catch {
            if ($retry -lt $maxRetries) {
                Write-Host "  Retry $retry failed for message $id, retrying..." -ForegroundColor Yellow
                Start-Sleep -Milliseconds (200 * $retry)
            }
        }
    }
    
    if (-not $sent) {
        $stillFailedCount++
        $stillFailed += $id
        Write-Host "X Message $id still failed after $maxRetries retries" -ForegroundColor Red
    }
}

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "Retry completed!" -ForegroundColor Green
Write-Host "Successfully sent: $successCount" -ForegroundColor Green
Write-Host "Still failed: $stillFailedCount" -ForegroundColor Red

if ($stillFailed.Count -gt 0) {
    Write-Host "`nStill failed message IDs:" -ForegroundColor Red
    Write-Host ($stillFailed -join ", ") -ForegroundColor Red
    $stillFailed | Out-File -FilePath "still-failed-messages.txt"
    Write-Host "Saved to: still-failed-messages.txt" -ForegroundColor Yellow
}
else {
    Write-Host "`nAll messages successfully sent! Cleaning up..." -ForegroundColor Green
    Remove-Item $failedFile -ErrorAction SilentlyContinue
}

Write-Host "========================================" -ForegroundColor Cyan
