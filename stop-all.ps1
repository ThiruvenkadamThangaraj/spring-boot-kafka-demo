# Stop all running microservices

Write-Host "Stopping all microservices..." -ForegroundColor Yellow
docker-compose down

if ($LASTEXITCODE -eq 0) {
    Write-Host "All services stopped successfully!" -ForegroundColor Green
} else {
    Write-Host "Failed to stop services!" -ForegroundColor Red
}
