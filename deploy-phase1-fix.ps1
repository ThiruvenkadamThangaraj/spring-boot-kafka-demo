# Phase 1 Fix - Quick Deploy Script
# 
# This script will:
# 1. Backup current configuration
# 2. Build the project with Phase 1 fixes
# 3. Verify the build
# 4. Show next steps

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "PHASE 1 FIX: DEPLOYMENT SCRIPT" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

$projectRoot = "c:\Users\thiru_qoss8b1\Downloads\demo\demo"
cd $projectRoot

# Step 1: Backup
Write-Host "[1/5] Creating backup..." -ForegroundColor Yellow
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupDir = "backup-$timestamp"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

if (Test-Path "src\main\resources\application.properties") {
    Copy-Item "src\main\resources\application.properties" "$backupDir\application.properties.backup"
    Write-Host "✓ Backed up application.properties" -ForegroundColor Green
}

# Step 2: Check if new files exist
Write-Host ""
Write-Host "[2/5] Verifying Phase 1 files..." -ForegroundColor Yellow
$files = @(
    "src\main\java\com\example\config\HttpClientConfig.java",
    "src\main\java\com\example\config\TimeoutAwareErrorHandler.java",
    "src\main\java\com\example\controller\HealthController.java",
    "src\main\java\com\example\exception\ServiceUnavailableException.java",
    "src\main\java\com\example\service\ExampleDownstreamService.java"
)

$allExist = $true
foreach ($file in $files) {
    if (Test-Path $file) {
        Write-Host "✓ $file" -ForegroundColor Green
    } else {
        Write-Host "✗ $file NOT FOUND" -ForegroundColor Red
        $allExist = $false
    }
}

if (-not $allExist) {
    Write-Host ""
    Write-Host "ERROR: Some Phase 1 files are missing!" -ForegroundColor Red
    Write-Host "Please ensure all files were created successfully." -ForegroundColor Red
    exit 1
}

# Step 3: Build project
Write-Host ""
Write-Host "[3/5] Building project..." -ForegroundColor Yellow
Write-Host "Running: mvn clean package -DskipTests" -ForegroundColor Cyan

$buildOutput = mvn clean package -DskipTests 2>&1
$buildSuccess = $LASTEXITCODE -eq 0

if ($buildSuccess) {
    Write-Host "✓ BUILD SUCCESS" -ForegroundColor Green
} else {
    Write-Host "✗ BUILD FAILED" -ForegroundColor Red
    Write-Host ""
    Write-Host "Build output:" -ForegroundColor Yellow
    Write-Host $buildOutput
    Write-Host ""
    Write-Host "Common issues:" -ForegroundColor Yellow
    Write-Host "- Missing import statements" -ForegroundColor Gray
    Write-Host "- Package name mismatch (check package com.example.*)" -ForegroundColor Gray
    Write-Host "- Missing dependencies in pom.xml" -ForegroundColor Gray
    exit 1
}

# Step 4: Verify JAR
Write-Host ""
Write-Host "[4/5] Verifying JAR files..." -ForegroundColor Yellow
$jars = Get-ChildItem -Path ".\*-service\target" -Filter "*.jar" -Recurse -ErrorAction SilentlyContinue | 
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*.original" }

if ($jars.Count -gt 0) {
    Write-Host "✓ Found $($jars.Count) JAR file(s):" -ForegroundColor Green
    foreach ($jar in $jars) {
        $sizeMB = [math]::Round($jar.Length / 1MB, 2)
        Write-Host "  - $($jar.Name) ($sizeMB MB)" -ForegroundColor Cyan
    }
} else {
    Write-Host "⚠ No JAR files found" -ForegroundColor Yellow
}

# Step 5: Show next steps
Write-Host ""
Write-Host "[5/5] Phase 1 Fix Deployment Complete!" -ForegroundColor Green
Write-Host ""
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "NEXT STEPS:" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Update application.properties with Phase 1 configuration:" -ForegroundColor Yellow
Write-Host "   Add these properties:" -ForegroundColor Gray
Write-Host ""
Write-Host "   # Phase 1 Fix: Timeouts" -ForegroundColor DarkGray
Write-Host "   http.client.connect-timeout=2000" -ForegroundColor DarkGray
Write-Host "   http.client.read-timeout=5000" -ForegroundColor DarkGray
Write-Host "   http.client.connection-request-timeout=3000" -ForegroundColor DarkGray
Write-Host ""
Write-Host "   # HikariCP timeouts" -ForegroundColor DarkGray
Write-Host "   spring.datasource.hikari.connection-timeout=3000" -ForegroundColor DarkGray
Write-Host "   spring.datasource.hikari.leak-detection-threshold=60000" -ForegroundColor DarkGray
Write-Host ""

Write-Host "2. Deploy the application:" -ForegroundColor Yellow
Write-Host "   Option A (Test locally):" -ForegroundColor Gray
Write-Host "   java -jar evaluation-service\target\evaluation-service-*.jar" -ForegroundColor DarkGray
Write-Host ""
Write-Host "   Option B (Windows Service):" -ForegroundColor Gray
Write-Host "   Stop-Service YourService" -ForegroundColor DarkGray
Write-Host "   Copy-Item target\*.jar C:\YourServicePath\" -ForegroundColor DarkGray
Write-Host "   Start-Service YourService" -ForegroundColor DarkGray
Write-Host ""

Write-Host "3. Verify Phase 1 fix is working:" -ForegroundColor Yellow
Write-Host "   curl http://localhost:8080/api/health/status" -ForegroundColor DarkGray
Write-Host ""
Write-Host "   Expected response:" -ForegroundColor Gray
Write-Host '   {"status":"HEALTHY","downstreamConcurrency":{"available":10}}' -ForegroundColor DarkGray
Write-Host ""

Write-Host "4. Monitor the system:" -ForegroundColor Yellow
Write-Host "   Watch for these improvements:" -ForegroundColor Gray
Write-Host "   - Thread count should stay below 80 (was 180-200)" -ForegroundColor DarkGray
Write-Host "   - API latency should be < 5 seconds (was 30+ seconds)" -ForegroundColor DarkGray
Write-Host "   - Downstream calls limited to 10 concurrent (was unlimited)" -ForegroundColor DarkGray
Write-Host ""

Write-Host "5. Review documentation:" -ForegroundColor Yellow
Write-Host "   See: PHASE1-IMMEDIATE-FIX-GUIDE.md for detailed instructions" -ForegroundColor DarkGray
Write-Host ""

Write-Host "=====================================" -ForegroundColor Cyan
Write-Host "ROLLBACK PLAN (if needed):" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "If issues occur, rollback with:" -ForegroundColor Gray
Write-Host "Copy-Item $backupDir\application.properties.backup src\main\resources\application.properties" -ForegroundColor DarkGray
Write-Host "mvn clean package -DskipTests" -ForegroundColor DarkGray
Write-Host ""

Write-Host "Backup location: $backupDir" -ForegroundColor Cyan
Write-Host ""
Write-Host "Phase 1 deployment ready! 🚀" -ForegroundColor Green
