# ========================================
# Build Script for Lambda Functions
# Creates deployment packages (ZIP files)
# ========================================

Write-Host "Building Lambda Functions..." -ForegroundColor Cyan

# Create Lambda deployment packages
$lambdaFunctions = @("data-processor", "health-checker")

foreach ($function in $lambdaFunctions) {
    Write-Host "`nBuilding $function..." -ForegroundColor Yellow
    
    $functionPath = "lambda\$function"
    
    if (-not (Test-Path $functionPath)) {
        Write-Host "Error: $functionPath not found!" -ForegroundColor Red
        continue
    }
    
    # Create temp directory for dependencies
    $tempDir = "$functionPath\package"
    if (Test-Path $tempDir) {
        Remove-Item $tempDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $tempDir | Out-Null
    
    # Install Python dependencies
    if (Test-Path "$functionPath\requirements.txt") {
        Write-Host "Installing dependencies for $function..." -ForegroundColor Gray
        pip install -r "$functionPath\requirements.txt" -t $tempDir --quiet
    }
    
    # Copy function code
    Copy-Item "$functionPath\index.py" -Destination $tempDir
    
    # Create ZIP archive
    $zipFile = "$functionPath.zip"
    if (Test-Path $zipFile) {
        Remove-Item $zipFile -Force
    }
    
    Write-Host "Creating ZIP archive..." -ForegroundColor Gray
    Compress-Archive -Path "$tempDir\*" -DestinationPath $zipFile -CompressionLevel Optimal
    
    # Cleanup
    Remove-Item $tempDir -Recurse -Force
    
    $zipSize = (Get-Item $zipFile).Length / 1KB
    Write-Host "✓ Created $zipFile (${zipSize:N2} KB)" -ForegroundColor Green
}

Write-Host "`n✓ Lambda functions built successfully!" -ForegroundColor Green
Write-Host "`nNext steps:" -ForegroundColor Cyan
Write-Host "1. cd terraform" -ForegroundColor Gray
Write-Host "2. terraform init" -ForegroundColor Gray
Write-Host "3. terraform plan" -ForegroundColor Gray
Write-Host "4. terraform apply" -ForegroundColor Gray
