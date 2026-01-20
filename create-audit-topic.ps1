#!/usr/bin/env pwsh

# Create Kafka Topic for Audit Events
# This script creates the audit-events topic with optimal configuration for high throughput

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host "  Creating Kafka Audit Topic             " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$KAFKA_HOME = "C:\kafka"
$KAFKA_BIN = "$KAFKA_HOME\bin\windows"
$BOOTSTRAP_SERVER = "localhost:9092"
$TOPIC_NAME = "audit-events"

# Check if Kafka is running
Write-Host "Checking Kafka availability..." -ForegroundColor Yellow
try {
    $kafkaCheck = & "$KAFKA_BIN\kafka-topics.bat" --list --bootstrap-server $BOOTSTRAP_SERVER 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "❌ Kafka is not running or not accessible at $BOOTSTRAP_SERVER" -ForegroundColor Red
        Write-Host "Please start Kafka first using start-all.ps1" -ForegroundColor Yellow
        exit 1
    }
    Write-Host "✓ Kafka is running" -ForegroundColor Green
} catch {
    Write-Host "❌ Error checking Kafka: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Check if topic already exists
Write-Host "Checking if topic '$TOPIC_NAME' already exists..." -ForegroundColor Yellow
$existingTopics = & "$KAFKA_BIN\kafka-topics.bat" --list --bootstrap-server $BOOTSTRAP_SERVER 2>&1

if ($existingTopics -match $TOPIC_NAME) {
    Write-Host "⚠ Topic '$TOPIC_NAME' already exists" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Do you want to delete and recreate it? (y/n)"
    if ($response -eq 'y' -or $response -eq 'Y') {
        Write-Host "Deleting existing topic..." -ForegroundColor Yellow
        & "$KAFKA_BIN\kafka-topics.bat" --delete --topic $TOPIC_NAME --bootstrap-server $BOOTSTRAP_SERVER
        Start-Sleep -Seconds 2
        Write-Host "✓ Topic deleted" -ForegroundColor Green
    } else {
        Write-Host "Keeping existing topic" -ForegroundColor Yellow
        exit 0
    }
}

Write-Host ""

# Create the topic with optimal configuration
Write-Host "Creating topic '$TOPIC_NAME' with high-throughput configuration..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  - Partitions: 12 (for parallel processing)" -ForegroundColor White
Write-Host "  - Replication Factor: 1 (single broker)" -ForegroundColor White
Write-Host "  - Retention: 7 days (604800000 ms)" -ForegroundColor White
Write-Host "  - Segment Size: 1GB (for efficient disk I/O)" -ForegroundColor White
Write-Host "  - Compression: Snappy (fast, good compression ratio)" -ForegroundColor White
Write-Host ""

& "$KAFKA_BIN\kafka-topics.bat" `
    --create `
    --topic $TOPIC_NAME `
    --bootstrap-server $BOOTSTRAP_SERVER `
    --partitions 12 `
    --replication-factor 1 `
    --config retention.ms=604800000 `
    --config segment.bytes=1073741824 `
    --config compression.type=snappy `
    --config min.insync.replicas=1

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✓ Topic '$TOPIC_NAME' created successfully!" -ForegroundColor Green
    Write-Host ""
    
    # Describe the topic
    Write-Host "Topic Details:" -ForegroundColor Cyan
    & "$KAFKA_BIN\kafka-topics.bat" --describe --topic $TOPIC_NAME --bootstrap-server $BOOTSTRAP_SERVER
    
    Write-Host ""
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host "  Audit Topic Setup Complete!            " -ForegroundColor Green
    Write-Host "==========================================" -ForegroundColor Green
    Write-Host ""
    Write-Host "Next Steps:" -ForegroundColor Cyan
    Write-Host "  1. Start audit-service: cd audit-service && mvn spring-boot:run" -ForegroundColor White
    Write-Host "  2. Make API calls to any microservice" -ForegroundColor White
    Write-Host "  3. Check audit logs: http://localhost:8086/api/audit" -ForegroundColor White
    Write-Host ""
} else {
    Write-Host ""
    Write-Host "❌ Failed to create topic '$TOPIC_NAME'" -ForegroundColor Red
    Write-Host "Please check Kafka logs for errors" -ForegroundColor Yellow
    exit 1
}
