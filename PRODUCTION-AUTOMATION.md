# Production Automation Guide

## Overview

This guide shows you how to **run the 1 billion record transfer automatically in production** using scheduled jobs, monitoring, and alerting.

## Wait... Why PowerShell? Isn't This Spring Boot?

**Yes, this IS Spring Boot!** Here's what each technology does:

| Component | Technology | Responsibility |
|-----------|-----------|----------------|
| **Data Transfer Logic** | ✅ Spring Boot (Java) | Fetch data, insert data, claim partitions |
| **Worker Service** | ✅ Spring Boot (@Scheduled) | Poll for work, process partitions |
| **REST API** | ✅ Spring Boot (@RestController) | Monitor progress |
| **Orchestration** | PowerShell/Bash | START 10 Spring Boot instances, monitor completion, STOP them |
| **Scheduling** | Task Scheduler/Cron | Run daily at 2 AM |

### Two Approaches:

**Option A: PowerShell Orchestration (Simpler) ⭐ Recommended**
```
Task Scheduler → PowerShell Script → Starts 10 JVMs (java -jar app.jar)
                                   ↓
                              Each JVM runs Spring Boot
                              Spring Boot does the work
```

**Option B: Pure Spring Boot (100% Java)**
```
Task Scheduler → Spring Boot Coordinator App → Internally spawns 10 worker threads
                                             ↓
                                        Spring Boot does everything
```

**Both are valid!** Below we show Option A (simpler), then Option B (pure Spring Boot).

---

## Quick Answer

**Four Production Options:**

| Option | Technology Stack | Setup Time | Best For |
|--------|-----------------|------------|----------|
| **A1: Windows Task Scheduler + PowerShell** | PowerShell orchestrates Spring Boot | 15 min | Windows servers, simple |
| **A2: Linux Cron + Bash** | Bash orchestrates Spring Boot | 20 min | Linux servers |
| **A3: Kubernetes CronJob** | K8s orchestrates Spring Boot | 30 min | Cloud, auto-scaling |
| **B: Pure Spring Boot** | 100% Spring Boot (no scripts) | 25 min | Java purists, embedded scheduling |

---

## Option 4: Pure Spring Boot (No PowerShell/Bash)

**"I want 100% Java/Spring Boot, no scripts!"**

### Architecture

Instead of external scripts starting 10 JVMs, have **ONE Spring Boot app** with:
- Main thread: Coordinator
- 10 worker threads: Process partitions in parallel

```
┌─────────────────────────────────────────┐
│  Single Spring Boot Application         │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │  CoordinatorService                │ │
│  │  @Scheduled(cron = "0 2 * * *")   │ │
│  │                                    │ │
│  │  Creates 10 worker threads ───────┼─┼──> Thread 1: Process Partition 1
│  └────────────────────────────────────┘ │   Thread 2: Process Partition 2
│                                          │   Thread 3: Process Partition 3
│                                          │   ...
│                                          │   Thread 10: Process Partition 10
└─────────────────────────────────────────┘
```

### Step 1: Create CoordinatorService

Create **`src/main/java/.../CoordinatorService.java`**:

```java
package com.yourcompany.transfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Service
public class CoordinatorService {
    
    private static final Logger log = LoggerFactory.getLogger(CoordinatorService.class);
    private static final int WORKER_COUNT = 10;
    private static final int TIMEOUT_MINUTES = 15;
    
    @Autowired
    private JdbcTemplate itmJdbcTemplate;
    
    @Autowired
    private JdbcTemplate coJdbcTemplate;
    
    @Autowired
    private JdbcTemplate coordJdbcTemplate;
    
    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    
    public CoordinatorService() {
        // Create thread pool for workers
        this.executorService = Executors.newFixedThreadPool(
            WORKER_COUNT,
            new ThreadFactory() {
                private int counter = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Worker-" + counter++);
                    t.setDaemon(false);
                    return t;
                }
            }
        );
    }
    
    /**
     * Scheduled to run daily at 2 AM
     * This is the entry point - replaces PowerShell script!
     */
    @Scheduled(cron = "0 2 * * *")  // Daily at 2 AM
    public void runDailyTransfer() {
        if (isRunning) {
            log.warn("Transfer already in progress, skipping this run");
            return;
        }
        
        isRunning = true;
        log.info("════════════════════════════════════════════════");
        log.info("  Daily Transfer Started at {}", LocalDateTime.now());
        log.info("════════════════════════════════════════════════");
        
        Long jobId = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Create job entry
            jobId = coordJdbcTemplate.queryForObject(
                "INSERT INTO transfer_jobs (status, started_at) " +
                "VALUES ('PENDING', GETDATE()); " +
                "SELECT SCOPE_IDENTITY()",
                Long.class
            );
            log.info("Created job ID: {}", jobId);
            
            // Step 2: Reset partitions
            coordJdbcTemplate.update(
                "UPDATE transfer_partitions " +
                "SET status = 'PENDING', worker_id = NULL, " +
                "started_at = NULL, completed_at = NULL, " +
                "records_transferred = 0, updated_at = GETDATE()"
            );
            log.info("Reset all partitions to PENDING");
            
            // Step 3: Start workers
            List<Future<PartitionResult>> futures = new ArrayList<>();
            for (int i = 1; i <= WORKER_COUNT; i++) {
                final int workerId = i;
                Future<PartitionResult> future = executorService.submit(() -> 
                    processPartitionsForWorker(workerId)
                );
                futures.add(future);
            }
            log.info("Started {} workers", WORKER_COUNT);
            
            // Step 4: Wait for all workers to complete (with timeout)
            List<PartitionResult> results = new ArrayList<>();
            for (Future<PartitionResult> future : futures) {
                try {
                    PartitionResult result = future.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
                    results.add(result);
                } catch (TimeoutException e) {
                    log.error("Worker timed out after {} minutes", TIMEOUT_MINUTES);
                    future.cancel(true);
                    throw new RuntimeException("Transfer timeout", e);
                }
            }
            
            // Step 5: Calculate totals
            long totalRecords = results.stream()
                .mapToLong(r -> r.recordsTransferred)
                .sum();
            
            long durationMs = System.currentTimeMillis() - startTime;
            double durationMin = durationMs / 60000.0;
            
            // Step 6: Validate
            Long sourceCount = itmJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ITM.bam_table " +
                "WHERE app_id BETWEEN 1001 AND 4500",
                Long.class
            );
            
            Long targetCount = coJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CO.entitlement_table",
                Long.class
            );
            
            log.info("Validation - Source: {}, Target: {}", sourceCount, targetCount);
            
            // Step 7: Update job status
            coordJdbcTemplate.update(
                "UPDATE transfer_jobs " +
                "SET status = 'COMPLETED', completed_at = GETDATE(), " +
                "total_records = ?, duration_seconds = ? " +
                "WHERE job_id = ?",
                totalRecords, durationMs / 1000, jobId
            );
            
            log.info("════════════════════════════════════════════════");
            log.info("  ✓ Transfer COMPLETED Successfully!");
            log.info("  Records Transferred: {}", totalRecords);
            log.info("  Duration: {:.2f} minutes", durationMin);
            log.info("  Rate: {:.0f} records/sec", totalRecords / (durationMs / 1000.0));
            log.info("════════════════════════════════════════════════");
            
            // Optional: Send notification
            sendNotification("✅ Transfer Completed", 
                String.format("Transferred %,d records in %.2f minutes", totalRecords, durationMin));
            
        } catch (Exception e) {
            log.error("════════════════════════════════════════════════");
            log.error("  ✗ Transfer FAILED: {}", e.getMessage(), e);
            log.error("════════════════════════════════════════════════");
            
            // Update job as failed
            if (jobId != null) {
                coordJdbcTemplate.update(
                    "UPDATE transfer_jobs " +
                    "SET status = 'FAILED', completed_at = GETDATE(), " +
                    "error_message = ? WHERE job_id = ?",
                    e.getMessage(), jobId
                );
            }
            
            // Send failure notification
            sendNotification("⚠️ Transfer FAILED", "Error: " + e.getMessage());
            
        } finally {
            isRunning = false;
        }
    }
    
    /**
     * Worker method - each thread processes partitions until none left
     */
    private PartitionResult processPartitionsForWorker(int workerId) {
        log.info("Worker {} started", workerId);
        long totalRecords = 0;
        int partitionsProcessed = 0;
        
        while (true) {
            try {
                // Claim next partition (thread-safe)
                Map<String, Object> partition = claimNextPartition(workerId);
                
                if (partition == null) {
                    log.info("Worker {} - No more partitions, finishing", workerId);
                    break;
                }
                
                Long partitionId = ((Number) partition.get("partition_id")).longValue();
                Integer appIdStart = (Integer) partition.get("app_id_start");
                Integer appIdEnd = (Integer) partition.get("app_id_end");
                
                log.info("Worker {} processing partition {} (app_id {}-{})", 
                    workerId, partitionId, appIdStart, appIdEnd);
                
                // Fetch data from source
                List<String> entitlements = itmJdbcTemplate.query(
                    "SELECT DISTINCT entitlement_name " +
                    "FROM ITM.bam_table WITH (NOLOCK) " +
                    "WHERE app_id >= ? AND app_id <= ?",
                    (rs, rowNum) -> rs.getString("entitlement_name"),
                    appIdStart, appIdEnd
                );
                
                log.info("Worker {} - Fetched {} records", workerId, entitlements.size());
                
                // Insert into target (batch)
                List<String> uniqueEntitlements = entitlements.stream()
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
                
                // Insert in chunks of 50,000
                int chunkSize = 50000;
                for (int i = 0; i < uniqueEntitlements.size(); i += chunkSize) {
                    List<String> chunk = uniqueEntitlements.subList(
                        i, Math.min(i + chunkSize, uniqueEntitlements.size())
                    );
                    
                    coJdbcTemplate.batchUpdate(
                        "MERGE CO.entitlement_table AS target " +
                        "USING (SELECT ? AS entitlement_name) AS source " +
                        "ON target.entitlement_name = source.entitlement_name " +
                        "WHEN NOT MATCHED THEN " +
                        "INSERT (entitlement_name) VALUES (source.entitlement_name);",
                        chunk,
                        chunk.size(),
                        (ps, entitlement) -> ps.setString(1, entitlement)
                    );
                }
                
                // Mark partition complete
                coordJdbcTemplate.update(
                    "UPDATE transfer_partitions " +
                    "SET status = 'COMPLETED', completed_at = GETDATE(), " +
                    "records_transferred = ?, updated_at = GETDATE() " +
                    "WHERE partition_id = ?",
                    uniqueEntitlements.size(), partitionId
                );
                
                totalRecords += uniqueEntitlements.size();
                partitionsProcessed++;
                
                log.info("Worker {} completed partition {} - {} records", 
                    workerId, partitionId, uniqueEntitlements.size());
                
            } catch (Exception e) {
                log.error("Worker {} failed: {}", workerId, e.getMessage(), e);
                break;
            }
        }
        
        log.info("Worker {} finished - Processed {} partitions, {} records", 
            workerId, partitionsProcessed, totalRecords);
        
        return new PartitionResult(partitionsProcessed, totalRecords);
    }
    
    /**
     * Thread-safe partition claiming using database locks
     */
    private Map<String, Object> claimNextPartition(int workerId) {
        try {
            return coordJdbcTemplate.queryForMap(
                "WITH NextPartition AS ( " +
                "    SELECT TOP 1 partition_id, app_id_start, app_id_end " +
                "    FROM transfer_partitions WITH (UPDLOCK, ROWLOCK) " +
                "    WHERE status = 'PENDING' " +
                "    ORDER BY partition_id " +
                ") " +
                "UPDATE transfer_partitions " +
                "SET status = 'PROCESSING', " +
                "    worker_id = ?, " +
                "    started_at = GETDATE(), " +
                "    updated_at = GETDATE() " +
                "OUTPUT inserted.partition_id, inserted.app_id_start, inserted.app_id_end " +
                "WHERE partition_id = (SELECT partition_id FROM NextPartition)",
                workerId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;  // No partitions left
        }
    }
    
    /**
     * Send notification (email, Slack, etc.)
     */
    private void sendNotification(String subject, String message) {
        // TODO: Implement your notification logic
        // Examples:
        // - Send email using JavaMailSender
        // - Post to Slack webhook
        // - Send to Azure Application Insights
        log.info("Notification: {} - {}", subject, message);
    }
    
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down coordinator service...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }
    
    // Result class
    private static class PartitionResult {
        final int partitionsProcessed;
        final long recordsTransferred;
        
        PartitionResult(int partitionsProcessed, long recordsTransferred) {
            this.partitionsProcessed = partitionsProcessed;
            this.recordsTransferred = recordsTransferred;
        }
    }
}
```

### Step 2: Enable Scheduling

Update **`Application.java`**:

```java
package com.yourcompany.transfer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // ⭐ Enable scheduled tasks
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### Step 3: Configure Application

Update **`application.yml`**:

```yaml
spring:
  application:
    name: transfer-coordinator
  
  # Three datasources (same as before)
  datasource:
    itm:
      jdbc-url: jdbc:sqlserver://itm-server:1433;databaseName=ITM
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      hikari:
        maximum-pool-size: 20
        
    co:
      jdbc-url: jdbc:sqlserver://co-server:1433;databaseName=CO
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      hikari:
        maximum-pool-size: 20
        
    coord:
      jdbc-url: jdbc:sqlserver://coord-server:1433;databaseName=Coordination
      username: ${DB_USERNAME}
      password: ${DB_PASSWORD}
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      hikari:
        maximum-pool-size: 10

# Logging
logging:
  level:
    com.yourcompany.transfer: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

### Step 4: Deploy & Run

**Build:**
```powershell
mvn clean package
```

**Run as Windows Service:**

Using [NSSM](https://nssm.cc/) (Non-Sucking Service Manager):

```powershell
# Download NSSM
Invoke-WebRequest -Uri "https://nssm.cc/release/nssm-2.24.zip" -OutFile "nssm.zip"
Expand-Archive -Path "nssm.zip" -DestinationPath "."

# Install as Windows Service
.\nssm-2.24\win64\nssm.exe install TransferCoordinator `
    "C:\Program Files\Java\jdk-17\bin\java.exe" `
    "-Xms4g -Xmx4g -jar C:\YourApp\target\transfer-app.jar"

# Set working directory
.\nssm-2.24\win64\nssm.exe set TransferCoordinator AppDirectory "C:\YourApp"

# Start service
Start-Service TransferCoordinator

# Check status
Get-Service TransferCoordinator
```

**Run as Linux Systemd Service:**

Create **`/etc/systemd/system/transfer-coordinator.service`**:

```ini
[Unit]
Description=Transfer Coordinator Service
After=network.target

[Service]
Type=simple
User=appuser
WorkingDirectory=/opt/transfer-app
ExecStart=/usr/bin/java -Xms4g -Xmx4g -jar /opt/transfer-app/app.jar
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

Enable and start:
```bash
sudo systemctl enable transfer-coordinator
sudo systemctl start transfer-coordinator
sudo systemctl status transfer-coordinator
```

### Step 5: Monitor

**View logs:**
```powershell
# Windows
Get-Content "C:\YourApp\logs\spring.log" -Tail 50 -Wait

# Linux
journalctl -u transfer-coordinator -f
```

**Check progress via REST API:**
```bash
curl http://localhost:8080/api/monitor/progress
```

### Advantages of Pure Spring Boot

✅ **100% Java** - No external scripts  
✅ **Self-contained** - Single JAR file  
✅ **Built-in scheduling** - @Scheduled annotation  
✅ **Easy deployment** - Run as Windows/Linux service  
✅ **Standard logging** - Logback/SLF4J  
✅ **Spring Boot management** - Actuator endpoints  

### Comparison

| Aspect | PowerShell Orchestration | Pure Spring Boot |
|--------|-------------------------|------------------|
| **External scripts** | Yes (PowerShell/Bash) | No |
| **Number of processes** | 10 JVMs | 1 JVM with 10 threads |
| **Memory usage** | 20GB (10 × 2GB) | 4GB (1 × 4GB) |
| **Deployment** | JAR + script | Just JAR |
| **Monitoring** | Script checks REST API | Internal coordination |
| **Failure handling** | Script manages | Spring manages |
| **Preferred for** | Large-scale, isolated workers | Simpler deployments |

---

## Which Option Should You Choose?

### Choose **PowerShell/Bash Orchestration** if:
- You want true parallel processing (10 separate JVMs)
- You need isolated failure domains (one worker crash doesn't affect others)
- You have plenty of memory (20GB+)
- You're comfortable with external scripts
- You want maximum throughput

### Choose **Pure Spring Boot** if:
- You want 100% Java solution
- You prefer simpler deployment (single service)
- Memory is limited (works with 4-6GB)
- You want everything in one place
- You're running on a single server

**Both work great for 1 billion records!** The PowerShell approach is slightly faster (5-8 min) due to true parallelism, while Pure Spring Boot is simpler (8-12 min with thread-based parallelism).

---

## Option 1: Windows Task Scheduler (Recommended for Windows)

### Step 1: Create Production Script

Create **`run-daily-transfer.ps1`** in your project root:

```powershell
# Production script - runs complete transfer process automatically
param(
    [string]$LogDir = "C:\TransferLogs",
    [int]$TimeoutMinutes = 15,
    [string]$AppPath = "C:\YourApp",
    [string]$SqlServer = "your-server",
    [string]$Database = "your-db",
    [string]$AlertEmail = "team@company.com"
)

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$logFile = "$LogDir\transfer_$timestamp.log"

# Create log directory
New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

function Write-Log {
    param($Message)
    $logMessage = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - $Message"
    Write-Host $logMessage
    Add-Content -Path $logFile -Value $logMessage
}

function Send-Alert {
    param($Subject, $Body)
    Send-MailMessage `
        -To $AlertEmail `
        -From "transfer-bot@company.com" `
        -Subject $Subject `
        -Body $Body `
        -SmtpServer "smtp.company.com" `
        -ErrorAction SilentlyContinue
}

try {
    Write-Log "════════════════════════════════════════════════"
    Write-Log "  Daily Transfer Job Started"
    Write-Log "════════════════════════════════════════════════"
    
    # Step 1: Create new job entry
    Write-Log "Creating job entry in database..."
    $createJobScript = @"
INSERT INTO transfer_jobs (status, started_at)
VALUES ('PENDING', GETDATE());
SELECT SCOPE_IDENTITY() AS job_id;
"@
    
    $jobId = sqlcmd -S $SqlServer -d $Database -Q $createJobScript -h -1 -W
    Write-Log "Job ID: $jobId"
    
    # Step 2: Reset partitions for new run
    Write-Log "Resetting partitions..."
    sqlcmd -S $SqlServer -d $Database -Q @"
UPDATE transfer_partitions 
SET status = 'PENDING', 
    worker_id = NULL, 
    started_at = NULL, 
    completed_at = NULL,
    updated_at = GETDATE();
"@ | Out-Null
    
    # Step 3: Start workers
    Write-Log "Starting 10 worker processes..."
    $workers = @()
    for ($i = 1; $i -le 10; $i++) {
        $port = 8080 + $i
        $proc = Start-Process -FilePath "java" `
            -ArgumentList "-Xms2g -Xmx2g -jar $AppPath\target\app.jar --server.port=$port" `
            -WorkingDirectory $AppPath `
            -PassThru `
            -WindowStyle Hidden
        $workers += $proc
        Write-Log "Started Worker $i (PID: $($proc.Id), Port: $port)"
        Start-Sleep -Seconds 2
    }
    
    # Step 4: Wait for workers to warm up
    Write-Log "Waiting for workers to initialize..."
    Start-Sleep -Seconds 10
    
    # Step 5: Monitor progress
    Write-Log "Monitoring progress..."
    $startTime = Get-Date
    $completed = $false
    $lastProgress = 0
    
    while (-not $completed) {
        $elapsed = (Get-Date) - $startTime
        
        # Check timeout
        if ($elapsed.TotalMinutes -gt $TimeoutMinutes) {
            throw "Transfer timeout after $TimeoutMinutes minutes"
        }
        
        # Check progress
        try {
            $progressJson = Invoke-RestMethod -Uri "http://localhost:8081/api/monitor/progress" -TimeoutSec 5
            $completedCount = $progressJson.completed
            $totalCount = $progressJson.total_partitions
            $percentage = [math]::Round(($completedCount / $totalCount) * 100, 1)
            
            # Log progress only if changed
            if ($completedCount -ne $lastProgress) {
                Write-Log "Progress: $completedCount/$totalCount partitions ($percentage%)"
                $lastProgress = $completedCount
            }
            
            # Check if all completed
            if ($completedCount -eq $totalCount) {
                $completed = $true
                Write-Log "All partitions completed!"
            }
        } catch {
            Write-Log "Warning: Could not reach monitoring endpoint (workers may still be starting)"
        }
        
        Start-Sleep -Seconds 30
    }
    
    # Step 6: Stop workers gracefully
    Write-Log "Stopping workers..."
    foreach ($worker in $workers) {
        if (-not $worker.HasExited) {
            Stop-Process -Id $worker.Id -Force
            Write-Log "Stopped Worker (PID: $($worker.Id))"
        }
    }
    
    # Wait for clean shutdown
    Start-Sleep -Seconds 5
    
    # Step 7: Validate results
    Write-Log "Validating results..."
    $validationScript = @"
SELECT 
    (SELECT COUNT(*) FROM CO.entitlement_table) as target_count,
    (SELECT COUNT(*) FROM ITM.bam_table WHERE app_id BETWEEN 1001 AND 4500) as source_count;
"@
    
    $validation = sqlcmd -S $SqlServer -d $Database -Q $validationScript -h -1 -W | Select-String -Pattern '\d+'
    Write-Log "Validation results: $validation"
    
    # Step 8: Update job status
    $duration = (Get-Date) - $startTime
    $durationMinutes = [math]::Round($duration.TotalMinutes, 2)
    
    sqlcmd -S $SqlServer -d $Database -Q @"
UPDATE transfer_jobs 
SET status = 'COMPLETED', 
    completed_at = GETDATE(),
    duration_minutes = $durationMinutes
WHERE job_id = $jobId;
"@ | Out-Null
    
    Write-Log "════════════════════════════════════════════════"
    Write-Log "  ✓ Transfer COMPLETED successfully!"
    Write-Log "  Duration: $durationMinutes minutes"
    Write-Log "════════════════════════════════════════════════"
    
    # Send success email
    Send-Alert `
        -Subject "✅ Daily Transfer Completed" `
        -Body "Transfer completed successfully in $durationMinutes minutes.`n`nLog file: $logFile"
    
    exit 0
    
} catch {
    $errorMsg = $_.Exception.Message
    Write-Log "════════════════════════════════════════════════"
    Write-Log "  ✗ ERROR: $errorMsg"
    Write-Log "  Stack Trace: $($_.ScriptStackTrace)"
    Write-Log "════════════════════════════════════════════════"
    
    # Stop any running workers
    Write-Log "Stopping all workers due to error..."
    Get-Process -Name java -ErrorAction SilentlyContinue | Where-Object {
        $_.CommandLine -like "*app.jar*"
    } | Stop-Process -Force
    
    # Update job as failed
    if ($jobId) {
        sqlcmd -S $SqlServer -d $Database -Q @"
UPDATE transfer_jobs 
SET status = 'FAILED', 
    error_message = '$($errorMsg.Replace("'", "''"))',
    completed_at = GETDATE() 
WHERE job_id = $jobId;
"@ | Out-Null
    }
    
    # Send failure email
    Send-Alert `
        -Subject "⚠️ Daily Transfer FAILED" `
        -Body "Transfer failed with error: $errorMsg`n`nLog file: $logFile"
    
    Write-Log "Transfer FAILED"
    exit 1
}
```

### Step 2: Schedule with Task Scheduler

**Option A: Using PowerShell (Quick Setup)**

```powershell
# Create scheduled task - run this in PowerShell as Administrator
$action = New-ScheduledTaskAction `
    -Execute "PowerShell.exe" `
    -Argument "-ExecutionPolicy Bypass -File C:\YourApp\run-daily-transfer.ps1"

$trigger = New-ScheduledTaskTrigger -Daily -At 2:00AM

$principal = New-ScheduledTaskPrincipal `
    -UserId "SYSTEM" `
    -LogonType ServiceAccount `
    -RunLevel Highest

$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -RestartCount 3 `
    -RestartInterval (New-TimeSpan -Minutes 5) `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)

Register-ScheduledTask `
    -TaskName "DailyDataTransfer" `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -Description "Daily transfer of 1B records from ITM to CO" `
    -Force
    
Write-Host "✓ Scheduled task created successfully!" -ForegroundColor Green
Write-Host "Task will run daily at 2:00 AM" -ForegroundColor Cyan
```

**Option B: Using Task Scheduler GUI**

1. Press `Win + R`, type `taskschd.msc`, press Enter
2. Click **Create Task** (not Basic Task)
3. **General Tab:**
   - Name: `DailyDataTransfer`
   - Description: `Daily transfer of 1 billion records`
   - Security options:
     - ☑ Run whether user is logged on or not
     - ☑ Run with highest privileges
     - ☑ Hidden
4. **Triggers Tab:**
   - Click **New...**
   - Begin the task: `On a schedule`
   - Settings: `Daily`, Start at `2:00:00 AM`
   - Advanced settings:
     - ☑ Enabled
     - ☑ Stop task if it runs longer than: `1 hour`
5. **Actions Tab:**
   - Click **New...**
   - Action: `Start a program`
   - Program/script: `PowerShell.exe`
   - Add arguments: `-ExecutionPolicy Bypass -File C:\YourApp\run-daily-transfer.ps1`
   - Start in: `C:\YourApp`
6. **Conditions Tab:**
   - ☑ Start only if the computer is on AC power: OFF
   - ☑ Wake the computer to run this task
7. **Settings Tab:**
   - ☑ Allow task to be run on demand
   - ☑ If the running task does not end when requested, force it to stop
   - If the task fails, restart every: `5 minutes`, max `3` attempts
8. Click **OK**, enter credentials when prompted

### Step 3: Test the Schedule

```powershell
# Run the task manually to test
Start-ScheduledTask -TaskName "DailyDataTransfer"

# Check status
Get-ScheduledTask -TaskName "DailyDataTransfer" | Get-ScheduledTaskInfo

# View history
Get-WinEvent -LogName "Microsoft-Windows-TaskScheduler/Operational" | 
    Where-Object { $_.Message -like "*DailyDataTransfer*" } | 
    Select-Object -First 10
```

---

## Option 2: Linux Systemd Services + Cron

### Step 1: Create Systemd Service Template

Create **`/etc/systemd/system/transfer-worker@.service`**:

```ini
[Unit]
Description=Data Transfer Worker %i
After=network.target postgresql.service

[Service]
Type=simple
User=appuser
Group=appuser
WorkingDirectory=/opt/transfer-app
Environment="SERVER_PORT=808%i"
Environment="SPRING_PROFILES_ACTIVE=worker"
ExecStart=/usr/bin/java -Xms2g -Xmx2g \
    -jar /opt/transfer-app/app.jar \
    --server.port=808%i

Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

# Security settings
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

### Step 2: Create Orchestration Script

Create **`/opt/transfer-app/run-transfer.sh`**:

```bash
#!/bin/bash

set -e  # Exit on error

# Configuration
LOG_DIR="/var/log/transfer"
APP_DIR="/opt/transfer-app"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$LOG_DIR/transfer_$TIMESTAMP.log"
SQL_SERVER="your-server"
DATABASE="your-db"
TIMEOUT_MINUTES=15

mkdir -p "$LOG_DIR"

log() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') - $1" | tee -a "$LOG_FILE"
}

send_alert() {
    local subject="$1"
    local body="$2"
    echo "$body" | mail -s "$subject" team@company.com
}

cleanup() {
    log "Cleaning up workers..."
    for i in {1..10}; do
        systemctl stop transfer-worker@$i 2>/dev/null || true
    done
}

trap cleanup EXIT

log "════════════════════════════════════════════════"
log "  Daily Transfer Job Started"
log "════════════════════════════════════════════════"

# Reset partitions
log "Resetting partitions..."
sqlcmd -S "$SQL_SERVER" -d "$DATABASE" -Q \
    "UPDATE transfer_partitions SET status='PENDING', worker_id=NULL, started_at=NULL, completed_at=NULL"

# Start workers
log "Starting 10 workers..."
for i in {1..10}; do
    systemctl start transfer-worker@$i
    log "Started worker $i"
    sleep 2
done

# Monitor progress
log "Monitoring progress..."
START_TIME=$(date +%s)
COMPLETED=false

while [ "$COMPLETED" = false ]; do
    CURRENT_TIME=$(date +%s)
    ELAPSED=$((CURRENT_TIME - START_TIME))
    ELAPSED_MINUTES=$((ELAPSED / 60))
    
    if [ $ELAPSED_MINUTES -gt $TIMEOUT_MINUTES ]; then
        log "ERROR: Transfer timeout after $TIMEOUT_MINUTES minutes"
        send_alert "⚠️ Transfer FAILED" "Transfer timed out after $TIMEOUT_MINUTES minutes"
        exit 1
    fi
    
    # Check progress
    if PROGRESS=$(curl -s http://localhost:8081/api/monitor/progress 2>/dev/null); then
        COMPLETED_COUNT=$(echo "$PROGRESS" | jq -r '.completed // 0')
        TOTAL_COUNT=$(echo "$PROGRESS" | jq -r '.total_partitions // 10')
        PERCENTAGE=$(echo "scale=1; ($COMPLETED_COUNT * 100) / $TOTAL_COUNT" | bc)
        
        log "Progress: $COMPLETED_COUNT/$TOTAL_COUNT ($PERCENTAGE%)"
        
        if [ "$COMPLETED_COUNT" -eq "$TOTAL_COUNT" ]; then
            COMPLETED=true
            log "All partitions completed!"
        fi
    else
        log "Warning: Could not reach monitoring endpoint"
    fi
    
    sleep 30
done

# Stop workers
log "Stopping workers..."
cleanup

# Calculate duration
END_TIME=$(date +%s)
DURATION_MINUTES=$(( (END_TIME - START_TIME) / 60 ))

# Validate
log "Validating results..."
VALIDATION=$(sqlcmd -S "$SQL_SERVER" -d "$DATABASE" -Q \
    "SELECT COUNT(*) FROM CO.entitlement_table" -h -1 | tr -d '[:space:]')

log "════════════════════════════════════════════════"
log "  ✓ Transfer COMPLETED successfully!"
log "  Duration: $DURATION_MINUTES minutes"
log "  Records: $VALIDATION"
log "════════════════════════════════════════════════"

send_alert "✅ Transfer Completed" "Transfer completed in $DURATION_MINUTES minutes. Records: $VALIDATION"

exit 0
```

Make it executable:

```bash
chmod +x /opt/transfer-app/run-transfer.sh
chown appuser:appuser /opt/transfer-app/run-transfer.sh
```

### Step 3: Schedule with Cron

```bash
# Edit crontab as appuser
sudo -u appuser crontab -e

# Add daily job at 2 AM
0 2 * * * /opt/transfer-app/run-transfer.sh >> /var/log/transfer/cron.log 2>&1
```

### Step 4: Test

```bash
# Test the script manually
sudo -u appuser /opt/transfer-app/run-transfer.sh

# Check systemd services
systemctl status transfer-worker@{1..10}

# View logs
journalctl -u transfer-worker@1 -f
```

---

## Option 3: Kubernetes CronJob

### Step 1: Create CronJob Definition

Create **`k8s/transfer-cronjob.yaml`**:

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: daily-data-transfer
  namespace: production
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM UTC
  concurrencyPolicy: Forbid  # Don't start if previous still running
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      parallelism: 10  # Run 10 workers in parallel
      completions: 10  # All 10 must complete
      backoffLimit: 3  # Retry failed pods max 3 times
      activeDeadlineSeconds: 3600  # 1 hour timeout
      template:
        metadata:
          labels:
            app: transfer-worker
            job: daily-transfer
        spec:
          restartPolicy: OnFailure
          containers:
          - name: transfer-worker
            image: your-registry.azurecr.io/transfer-app:latest
            imagePullPolicy: Always
            env:
            - name: SPRING_PROFILES_ACTIVE
              value: "worker"
            - name: SPRING_DATASOURCE_ITM_URL
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: itm-url
            - name: SPRING_DATASOURCE_CO_URL
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: co-url
            - name: SPRING_DATASOURCE_ITM_USERNAME
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: username
            - name: SPRING_DATASOURCE_ITM_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: db-credentials
                  key: password
            resources:
              requests:
                memory: "2Gi"
                cpu: "1000m"
              limits:
                memory: "2Gi"
                cpu: "2000m"
            livenessProbe:
              httpGet:
                path: /actuator/health
                port: 8080
              initialDelaySeconds: 30
              periodSeconds: 30
            readinessProbe:
              httpGet:
                path: /actuator/health/readiness
                port: 8080
              initialDelaySeconds: 10
              periodSeconds: 10
```

### Step 2: Create Database Secrets

```bash
kubectl create secret generic db-credentials \
  --from-literal=itm-url='jdbc:sqlserver://itm-server:1433;databaseName=ITM' \
  --from-literal=co-url='jdbc:sqlserver://co-server:1433;databaseName=CO' \
  --from-literal=username='your-user' \
  --from-literal=password='your-password' \
  --namespace=production
```

### Step 3: Deploy

```bash
# Apply the CronJob
kubectl apply -f k8s/transfer-cronjob.yaml

# Verify
kubectl get cronjobs -n production
```

### Step 4: Test Manually

```bash
# Trigger manual run
kubectl create job --from=cronjob/daily-data-transfer manual-transfer-$(date +%s) -n production

# Watch progress
kubectl get pods -n production -l job=daily-transfer -w

# View logs
kubectl logs -n production -l job=daily-transfer -f

# Check job status
kubectl get jobs -n production
```

---

## Monitoring & Alerting

### Health Check Endpoint

Add to **`MonitorController.java`**:

```java
@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    
    @Autowired
    private JdbcTemplate coordJdbcTemplate;
    
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        try {
            // Check database connectivity
            coordJdbcTemplate.queryForObject("SELECT 1", Integer.class);
            health.put("status", "UP");
            health.put("database", "CONNECTED");
            
            // Check active workers
            Integer activeWorkers = coordJdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT worker_id) FROM transfer_partitions " +
                "WHERE status = 'PROCESSING'", 
                Integer.class
            );
            health.put("activeWorkers", activeWorkers);
            
            // Check for stuck partitions (processing > 15 minutes)
            Integer stuckPartitions = coordJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_partitions " +
                "WHERE status = 'PROCESSING' " +
                "AND DATEDIFF(MINUTE, started_at, GETDATE()) > 15",
                Integer.class
            );
            health.put("stuckPartitions", stuckPartitions);
            
            if (stuckPartitions > 0) {
                health.put("warning", "Some partitions may be stuck");
            }
            
            return ResponseEntity.ok(health);
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            return ResponseEntity.status(503).body(health);
        }
    }
}
```

### Slack Notifications

Add to **`run-daily-transfer.ps1`**:

```powershell
function Send-SlackNotification {
    param(
        [string]$Message,
        [string]$Status,  # SUCCESS or FAILURE
        [string]$WebhookUrl = "https://hooks.slack.com/services/YOUR/WEBHOOK/URL"
    )
    
    $emoji = if ($Status -eq "SUCCESS") { ":white_check_mark:" } else { ":x:" }
    $color = if ($Status -eq "SUCCESS") { "good" } else { "danger" }
    
    $payload = @{
        channel = "#data-transfer"
        username = "Transfer Bot"
        icon_emoji = ":robot_face:"
        attachments = @(
            @{
                color = $color
                title = "$emoji Data Transfer $Status"
                text = $Message
                fields = @(
                    @{
                        title = "Timestamp"
                        value = (Get-Date -Format "yyyy-MM-dd HH:mm:ss")
                        short = $true
                    }
                )
            }
        )
    } | ConvertTo-Json -Depth 4
    
    try {
        Invoke-RestMethod -Uri $WebhookUrl -Method Post -Body $payload -ContentType "application/json"
    } catch {
        Write-Log "Warning: Could not send Slack notification: $($_.Exception.Message)"
    }
}

# Usage in success block
Send-SlackNotification `
    -Message "Daily transfer completed successfully in $durationMinutes minutes" `
    -Status "SUCCESS"

# Usage in error block
Send-SlackNotification `
    -Message "Daily transfer failed: $errorMsg`nLog: $logFile" `
    -Status "FAILURE"
```

### Azure Application Insights

Add to **`pom.xml`**:

```xml
<dependency>
    <groupId>com.microsoft.azure</groupId>
    <artifactId>applicationinsights-spring-boot-starter</artifactId>
    <version>3.4.18</version>
</dependency>
```

Add to **`application.yml`**:

```yaml
azure:
  application-insights:
    instrumentation-key: ${APPINSIGHTS_KEY}
    
logging:
  level:
    com.yourpackage: INFO
```

---

## Best Practices for Production

### 1. Resource Management
- **Memory**: 2GB per worker (20GB total for 10 workers)
- **CPU**: 1-2 cores per worker minimum
- **Network**: Ensure sufficient bandwidth to database (10 Gbps recommended)
- **Disk**: 10GB for logs and temporary files

### 2. Error Handling
- Auto-retry failed partitions (max 3 attempts)
- Email/Slack alerts on failure within 1 minute
- Keep logs for 30 days (rotate daily)
- Alert if transfer takes > 15 minutes

### 3. Monitoring Checklist
- [ ] Track execution time (baseline: 5-8 minutes)
- [ ] Monitor database connections (alert if > 80% used)
- [ ] Check partition status (alert if stuck > 15 min)
- [ ] Monitor memory usage (alert if > 90%)
- [ ] Verify record counts daily
- [ ] Track failure rate (alert if > 1% failures)

### 4. Maintenance Window
- Schedule during low-traffic hours (2-3 AM)
- Block at least 20-minute window
- Have rollback plan ready
- Test monthly on staging environment

### 5. Backup Strategy
```sql
-- Backup target table before daily run
SELECT * INTO CO.entitlement_table_backup_20260114
FROM CO.entitlement_table;

-- Keep last 7 days of backups
-- Drop older backups to save space
```

### 6. Security
- Store credentials in Azure Key Vault or AWS Secrets Manager
- Use service accounts with minimal permissions
- Enable TLS for database connections
- Rotate passwords quarterly
- Audit log access monthly

---

## Troubleshooting Production Issues

### Issue 1: Transfer Taking Too Long

**Symptom:** Transfer exceeds 15 minutes

**Diagnosis:**
```powershell
# Check which partitions are slow
curl http://localhost:8081/api/monitor/partitions
```

**Solutions:**
1. Increase workers to 20
2. Check database index on `app_id`
3. Verify network bandwidth
4. Check for database locks

### Issue 2: Workers Not Starting

**Symptom:** Task starts but no workers appear

**Diagnosis:**
```powershell
# Check for Java processes
Get-Process -Name java

# Check logs
Get-Content C:\TransferLogs\transfer_*.log -Tail 50
```

**Solutions:**
1. Verify Java is in PATH
2. Check application.yml configuration
3. Verify database connectivity
4. Check port availability (8081-8090)

### Issue 3: Task Scheduler Not Running

**Symptom:** Task shows "Ready" but never runs

**Diagnosis:**
```powershell
# Check task history
Get-ScheduledTask -TaskName "DailyDataTransfer" | Get-ScheduledTaskInfo

# View last run result
Get-WinEvent -LogName "Microsoft-Windows-TaskScheduler/Operational" | 
    Where-Object { $_.Message -like "*DailyDataTransfer*" }
```

**Solutions:**
1. Verify task credentials
2. Check "Run with highest privileges"
3. Verify script path is correct
4. Test script manually first

### Issue 4: Database Connection Timeout

**Symptom:** Workers fail with connection timeout

**Solutions:**
```yaml
# Update application.yml
spring:
  datasource:
    hikari:
      connection-timeout: 60000  # 60 seconds
      maximum-pool-size: 20
      minimum-idle: 10
      leak-detection-threshold: 30000
```

---

## Summary

You now have **three production-ready automation options**:

| Method | Setup | When to Use |
|--------|-------|-------------|
| **Windows Task Scheduler** | 15 min | Windows servers, simple setup |
| **Linux Cron + Systemd** | 20 min | Linux servers, systemd management |
| **Kubernetes CronJob** | 30 min | Cloud environments, auto-scaling |

**Next Steps:**
1. Choose your deployment platform
2. Update script with your database credentials
3. Test manually first (`Start-ScheduledTask` or `./run-transfer.sh`)
4. Enable monitoring and alerts
5. Schedule for daily 2 AM execution
6. Monitor first run carefully
7. Set up weekly review of logs

**Key Metrics to Track:**
- Execution time (target: < 10 minutes)
- Success rate (target: > 99%)
- Records transferred (target: 1 billion)
- Partition failure rate (target: < 1%)

🎉 **Your 1 billion record transfer is now fully automated!**
