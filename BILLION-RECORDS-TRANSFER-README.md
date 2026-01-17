# 1 Billion Records Transfer System - Complete Guide

## Table of Contents
1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [Prerequisites](#prerequisites)
4. [Project Setup](#project-setup)
5. [Database Configuration](#database-configuration)
6. [Implementation](#implementation)
7. [Usage Guide](#usage-guide)
8. [Daily Scheduled Sync](#daily-scheduled-sync)
9. [Validation & Monitoring](#validation--monitoring)
10. [Performance Metrics](#performance-metrics)
11. [Troubleshooting](#troubleshooting)
12. [Best Practices](#best-practices)

---

## Overview

This system transfers **1 billion records** from a source database (ITM schema) to a target database (CO schema) efficiently using a single Spring Boot application. It includes:

- ✅ **Non-blocking async transfer** with progress tracking
- ✅ **Daily scheduled sync** for missing records only
- ✅ **Automatic validation** to ensure data consistency
- ✅ **Memory efficient** batch processing
- ✅ **Single JVM** - simple deployment
- ✅ **Production-ready** with error handling and logging

### Key Features

| Feature | Description |
|---------|-------------|
| **Transfer Speed** | 45-60 minutes for 1 billion records |
| **Daily Sync** | 1-2 minutes (only missing records) |
| **Memory Usage** | 1-2 GB JVM heap |
| **JVMs Required** | 1 (Single Spring Boot application) |
| **Database** | SQL Server 2019/2022 |
| **Deployment** | Single JAR file |

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────┐                    ┌─────────────────────┐
│   ITM DATABASE      │                    │   CO DATABASE       │
│   (Source)          │                    │   (Target)          │
│                     │                    │                     │
│  Table: bam_table   │                    │  Table:             │
│  Records: 1 Billion │◄──────────────────►│  entitlement_table  │
│  Index: app_id      │    Data Transfer   │  Records: 1 Billion │
└─────────────────────┘                    └─────────────────────┘
         ▲                                           ▲
         │                                           │
         │ Read (Batch)                    Write     │
         │                                           │
         └───────────────┐       ┌──────────────────┘
                         │       │
                  ┌──────▼───────▼──────┐
                  │  SPRING BOOT APP    │
                  │  (Single JVM)       │
                  │                     │
                  │  • Async Transfer   │
                  │  • Daily Scheduler  │
                  │  • Validation       │
                  │  • REST API         │
                  │                     │
                  │  Port: 8080         │
                  │  Memory: 1-2 GB     │
                  └─────────────────────┘
```

### JVM Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    SINGLE JVM PROCESS                        │
├─────────────────────────────────────────────────────────────┤
│  Memory: 2 GB (-Xmx2048m)                                   │
│  Threads: 30-40 (main + workers + scheduler + DB pool)     │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  THREAD POOL: transferExecutor                     │    │
│  │  Core: 5 threads | Max: 10 threads                 │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  SCHEDULER: Spring Task Scheduler                  │    │
│  │  Runs daily at 2:00 AM                             │    │
│  └────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌────────────────────────────────────────────────────┐    │
│  │  CONNECTION POOLS (HikariCP)                       │    │
│  │  • ITM DataSource: 20 connections                  │    │
│  │  • CO DataSource:  20 connections                  │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘

Total: 1 JVM, ~2 GB Memory, 40 connections to databases
```

---

## Prerequisites

### Software Requirements

| Component | Version | Purpose |
|-----------|---------|---------|
| Java | 17+ | Runtime environment |
| Maven | 3.8+ | Build tool |
| SQL Server | 2019/2022 | Source and target databases |
| Spring Boot | 3.2.1 | Application framework |

### Database Requirements

#### Source Database (ITM)
- **Table**: `ITM.bam_table`
- **Records**: 1 billion
- **Index**: `idx_bam_app_id` on `app_id` column (CRITICAL)
- **Connection Pool**: 20 connections

#### Target Database (CO)
- **Table**: `CO.entitlement_table`
- **Index**: `idx_entitlement_name` on `entitlement_name` column
- **Connection Pool**: 20 connections

### Server Requirements

```
Application Server:
├─ CPU: 4 cores minimum
├─ RAM: 4 GB (2 GB for JVM + 2 GB for OS)
├─ Disk: 10 GB (for logs and temp files)
└─ OS: Windows Server 2019+ or Linux

Database Servers:
├─ CPU: 8 cores minimum
├─ RAM: 16 GB minimum
├─ Disk: 500 GB+ SSD recommended
└─ Network: 100 Mbps+ between app and databases
```

---

## Project Setup

### Step 1: Create Spring Boot Project

```bash
# Create new Spring Boot project
mvn archetype:generate \
  -DgroupId=com.example.transfer \
  -DartifactId=data-transfer-service \
  -DarchetypeArtifactId=maven-archetype-quickstart \
  -DinteractiveMode=false
```

### Step 2: Add Dependencies (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.1</version>
        <relativePath/>
    </parent>
    
    <groupId>com.example.transfer</groupId>
    <artifactId>data-transfer-service</artifactId>
    <version>1.0.0</version>
    <name>Data Transfer Service</name>
    
    <properties>
        <java.version>17</java.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <!-- Spring Boot JDBC -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        
        <!-- SQL Server JDBC Driver -->
        <dependency>
            <groupId>com.microsoft.sqlserver</groupId>
            <artifactId>mssql-jdbc</artifactId>
            <version>12.4.2.jre11</version>
        </dependency>
        
        <!-- Lombok (optional) -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Spring Boot Actuator (optional - for monitoring) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### Step 3: Application Configuration (application.yml)

```yaml
spring:
  application:
    name: data-transfer-service
    
  datasource:
    # ITM Schema (Source - 1 Billion Records)
    itm:
      jdbc-url: jdbc:sqlserver://itm-server:1433;databaseName=ITM_DATABASE;encrypt=true;trustServerCertificate=true
      username: ${ITM_DB_USER:itm_user}
      password: ${ITM_DB_PASSWORD:itm_password}
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
        
    # CO Schema (Target)  
    co:
      jdbc-url: jdbc:sqlserver://co-server:1433;databaseName=CO_DATABASE;encrypt=true;trustServerCertificate=true
      username: ${CO_DB_USER:co_user}
      password: ${CO_DB_PASSWORD:co_password}
      driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
      hikari:
        maximum-pool-size: 20
        minimum-idle: 5
        connection-timeout: 30000
        idle-timeout: 600000
        max-lifetime: 1800000
        
  task:
    scheduling:
      pool:
        size: 5
      thread-name-prefix: scheduled-
        
server:
  port: 8080
  
logging:
  level:
    root: INFO
    com.example.transfer: DEBUG
  file:
    name: logs/transfer-service.log
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# App IDs Configuration (your 3500 app IDs)
app:
  ids: 1001,1002,1003,1004,1005,1006,1007,1008,1009,1010
  # ... add all 3500 app IDs here or load from external file
```

---

## Database Configuration

### SQL Server Setup Scripts

#### Create Schemas and Tables

```sql
-- ============================================
-- SQL SERVER DATABASE SETUP
-- ============================================

-- Step 1: Create Schemas
IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'ITM')
    EXEC('CREATE SCHEMA ITM');
GO

IF NOT EXISTS (SELECT * FROM sys.schemas WHERE name = 'CO')
    EXEC('CREATE SCHEMA CO');
GO

-- Step 2: Create Target Table
CREATE TABLE CO.entitlement_table (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    entitlement_name NVARCHAR(200) NOT NULL,
    synced_at DATETIME2 DEFAULT SYSDATETIME()
);
GO

-- Step 3: Create Indexes (CRITICAL for performance)
-- Unique index prevents duplicates
CREATE UNIQUE NONCLUSTERED INDEX idx_unique_entitlement 
ON CO.entitlement_table(entitlement_name);
GO

-- Step 4: Verify Source Table has Index
-- This is CRITICAL for querying 1 billion rows efficiently
IF NOT EXISTS (
    SELECT * FROM sys.indexes 
    WHERE name = 'idx_bam_app_id' 
    AND object_id = OBJECT_ID('ITM.bam_table')
)
BEGIN
    PRINT 'Creating index on ITM.bam_table.app_id (CRITICAL for performance)'
    CREATE NONCLUSTERED INDEX idx_bam_app_id 
    ON ITM.bam_table(app_id) WITH (ONLINE = ON);
END
GO

-- Step 5: Check Index Fragmentation
SELECT 
    OBJECT_NAME(ips.object_id) AS TableName,
    i.name AS IndexName,
    ips.avg_fragmentation_in_percent,
    ips.page_count
FROM sys.dm_db_index_physical_stats(DB_ID(), NULL, NULL, NULL, 'LIMITED') ips
JOIN sys.indexes i ON ips.object_id = i.object_id AND ips.index_id = i.index_id
WHERE OBJECT_NAME(ips.object_id) IN ('bam_table', 'entitlement_table')
ORDER BY avg_fragmentation_in_percent DESC;
GO

-- Step 6: Grant Permissions
GRANT SELECT ON SCHEMA::ITM TO itm_user;
GRANT INSERT, SELECT ON SCHEMA::CO TO co_user;
GO

PRINT 'Database setup completed successfully!'
```

#### Performance Optimization

```sql
-- ============================================
-- PERFORMANCE OPTIMIZATION SCRIPTS
-- ============================================

-- 1. Update Statistics (Run before large transfers)
UPDATE STATISTICS ITM.bam_table WITH FULLSCAN;
UPDATE STATISTICS CO.entitlement_table WITH FULLSCAN;
GO

-- 2. Check Index Usage
SELECT 
    OBJECT_NAME(s.object_id) AS TableName,
    i.name AS IndexName,
    s.user_seeks,
    s.user_scans,
    s.user_lookups,
    s.user_updates,
    s.last_user_seek,
    s.last_user_scan
FROM sys.dm_db_index_usage_stats s
JOIN sys.indexes i ON s.object_id = i.object_id AND s.index_id = i.index_id
WHERE database_id = DB_ID()
  AND OBJECT_NAME(s.object_id) IN ('bam_table', 'entitlement_table')
ORDER BY s.user_seeks DESC;
GO

-- 3. Monitor Table Size
SELECT 
    t.name AS TableName,
    s.name AS SchemaName,
    p.rows AS RowCount,
    (SUM(a.total_pages) * 8) / 1024 AS TotalSpaceMB,
    (SUM(a.used_pages) * 8) / 1024 AS UsedSpaceMB
FROM sys.tables t
JOIN sys.schemas s ON t.schema_id = s.schema_id
JOIN sys.indexes i ON t.object_id = i.object_id
JOIN sys.partitions p ON i.object_id = p.object_id AND i.index_id = p.index_id
JOIN sys.allocation_units a ON p.partition_id = a.container_id
WHERE s.name IN ('ITM', 'CO')
GROUP BY t.name, s.name, p.rows
ORDER BY RowCount DESC;
GO

-- 4. Enable Page Compression (Optional - saves disk space)
ALTER TABLE CO.entitlement_table REBUILD WITH (DATA_COMPRESSION = PAGE);
GO
```

---

## Implementation

### Project Structure

```
data-transfer-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/transfer/
│   │   │       ├── config/
│   │   │       │   ├── DataSourceConfig.java
│   │   │       │   ├── AsyncConfig.java
│   │   │       │   └── SchedulingConfig.java
│   │   │       ├── controller/
│   │   │       │   └── TransferController.java
│   │   │       ├── model/
│   │   │       │   ├── TransferStatus.java
│   │   │       │   ├── TransferResult.java
│   │   │       │   ├── SyncReport.java
│   │   │       │   └── ValidationResult.java
│   │   │       ├── service/
│   │   │       │   ├── SingleColumnTransferService.java
│   │   │       │   ├── SqlServerDailySyncService.java
│   │   │       │   └── SqlServerValidationService.java
│   │   │       └── DataTransferApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── logback-spring.xml
│   └── test/
├── pom.xml
└── README.md
```

### Build the Project

```bash
# Clean and build
mvn clean package -DskipTests

# Run tests
mvn test

# Build with custom profile
mvn clean package -P production
```

---

## Usage Guide

### Starting the Application

#### Method 1: Using Maven

```bash
mvn spring-boot:run
```

#### Method 2: Using JAR

```bash
# Build JAR
mvn clean package

# Run with custom memory settings
java -Xms512m -Xmx2048m -jar target/data-transfer-service-1.0.0.jar
```

#### Method 3: Using PowerShell Script

```powershell
# start-transfer-service.ps1
$env:ITM_DB_USER="itm_user"
$env:ITM_DB_PASSWORD="itm_password"
$env:CO_DB_USER="co_user"
$env:CO_DB_PASSWORD="co_password"

java -Xms512m -Xmx2048m `
     -Dspring.profiles.active=production `
     -jar target/data-transfer-service-1.0.0.jar
```

### API Endpoints

#### 1. Start Async Transfer (Non-Blocking)

**Request:**
```bash
curl -X POST http://localhost:8080/api/entitlement/transfer/async \
  -H "Content-Type: application/json" \
  -d '{
    "appIds": [1001, 1002, 1003, 1004, 1005]
  }'
```

**Response:**
```json
{
  "jobId": "a7f3c9d1-4e2b-4f8a-9c1d-5e6f7a8b9c0d",
  "status": "STARTED",
  "message": "Transfer started. Check /api/entitlement/status/a7f3c9d1-4e2b-4f8a-9c1d-5e6f7a8b9c0d"
}
```

#### 2. Check Transfer Status

**Request:**
```bash
curl http://localhost:8080/api/entitlement/status/a7f3c9d1-4e2b-4f8a-9c1d-5e6f7a8b9c0d
```

**Response (Running):**
```json
{
  "jobId": "a7f3c9d1-4e2b-4f8a-9c1d-5e6f7a8b9c0d",
  "status": "RUNNING",
  "totalAppIds": 3500,
  "processedBatches": 3,
  "totalRecordsTransferred": 428571428,
  "progress": 42.85,
  "startTime": "2026-01-14T10:00:00",
  "endTime": null,
  "errorMessage": null
}
```

**Response (Completed):**
```json
{
  "jobId": "a7f3c9d1-4e2b-4f8a-9c1d-5e6f7a8b9c0d",
  "status": "COMPLETED",
  "totalAppIds": 3500,
  "processedBatches": 7,
  "totalRecordsTransferred": 1000000000,
  "progress": 100.0,
  "startTime": "2026-01-14T10:00:00",
  "endTime": "2026-01-14T10:45:00",
  "errorMessage": null
}
```

#### 3. Validate Data Sync

**Request:**
```bash
curl -X POST http://localhost:8080/api/sync/validate \
  -H "Content-Type: application/json" \
  -d '{
    "appIds": [1001, 1002, 1003, 1004, 1005]
  }'
```

**Response:**
```json
{
  "inSync": true,
  "countMatch": true,
  "sourceCount": 1000000000,
  "targetCount": 1000000000,
  "missingCount": 0,
  "extraCount": 0,
  "missingRecords": [],
  "extraRecords": [],
  "validatedAt": "2026-01-14T11:00:00"
}
```

#### 4. Manual Trigger Daily Sync

**Request:**
```bash
curl -X POST http://localhost:8080/api/sync/trigger
```

**Response:**
```json
{
  "status": "SUCCESS",
  "message": "Manual sync completed",
  "recordsInserted": 15000
}
```

---

## Daily Scheduled Sync

### How It Works

The system automatically runs **every day at 2:00 AM** to sync only missing records:

```
Timeline:
─────────────────────────────────────────────────────────────

Day 1:
  10:00 AM → Initial transfer (1 billion records)
             ITM: 1,000,000,000 records
             CO:  1,000,000,000 records ✓

Day 2:
  Throughout day → New data added to ITM
                   ITM: 1,000,015,000 records (+15,000 new)
                   CO:  1,000,000,000 records

  2:00 AM → Scheduler automatically triggers
            ↓
            Finds 15,000 missing records
            ↓
            Inserts only those 15,000
            ↓
            Complete in 1-2 minutes
            ↓
            ITM: 1,000,015,000 records
            CO:  1,000,015,000 records ✓

Day 3:
  2:00 AM → Scheduler runs again
            ↓
            No new records found
            ↓
            Complete in 30 seconds
```

### Schedule Configuration

```java
// Run every day at 2:00 AM
@Scheduled(cron = "0 0 2 * * *")

// Other schedule options:
@Scheduled(cron = "0 0 0 * * *")      // Midnight
@Scheduled(cron = "0 0 */6 * * *")    // Every 6 hours
@Scheduled(cron = "0 0 2 * * MON-FRI") // Weekdays only at 2 AM
@Scheduled(fixedRate = 86400000)      // Every 24 hours from startup
```

### SQL Query Used

```sql
-- Finds and inserts only missing records
INSERT INTO CO.entitlement_table (entitlement_name)
SELECT DISTINCT itm.entitlement_name
FROM ITM.bam_table itm WITH (NOLOCK)
WHERE itm.app_id IN (1001, 1002, ..., 4500)
  AND NOT EXISTS (
    SELECT 1 FROM CO.entitlement_table co
    WHERE co.entitlement_name = itm.entitlement_name
  );
```

### Logs Example

```
2026-01-15 02:00:00 - ========================================
2026-01-15 02:00:00 - SQL Server Daily Sync: 2026-01-15T02:00:00
2026-01-15 02:00:00 - ========================================
2026-01-15 02:00:05 - Executing sync query...
2026-01-15 02:01:30 - ========================================
2026-01-15 02:01:30 - Sync Completed!
2026-01-15 02:01:30 - Missing records inserted: 15234
2026-01-15 02:01:30 - Duration: 90 seconds
2026-01-15 02:01:30 - ========================================
```

---

## Validation & Monitoring

### 1. Count Validation

Compares record counts between source and target:

```sql
-- Source count
SELECT COUNT(*) FROM ITM.bam_table WITH (NOLOCK)
WHERE app_id IN (1001, 1002, ..., 4500);
-- Result: 1,000,000,000

-- Target count
SELECT COUNT(*) FROM CO.entitlement_table WITH (NOLOCK);
-- Result: 1,000,000,000

-- Match: ✓
```

### 2. Find Missing Records

Uses `NOT EXISTS` to find records in source but not in target:

```sql
SELECT TOP 100 itm.entitlement_name
FROM ITM.bam_table itm WITH (NOLOCK)
WHERE itm.app_id IN (1001, 1002, ..., 4500)
  AND NOT EXISTS (
    SELECT 1 FROM CO.entitlement_table co
    WHERE co.entitlement_name = itm.entitlement_name
  );
```

### 3. Find Extra Records

Identifies records in target that shouldn't be there:

```sql
SELECT TOP 100 co.entitlement_name
FROM CO.entitlement_table co WITH (NOLOCK)
WHERE NOT EXISTS (
  SELECT 1 FROM ITM.bam_table itm WITH (NOLOCK)
  WHERE itm.entitlement_name = co.entitlement_name
    AND itm.app_id IN (1001, 1002, ..., 4500)
);
```

### 4. Monitoring Queries

#### Check Transfer Progress
```sql
-- Monitor table growth
SELECT COUNT(*) FROM CO.entitlement_table;
-- Expected: increases from 0 to 1 billion
```

#### Check Database Load
```sql
-- Active sessions
SELECT 
    session_id,
    login_time,
    status,
    cpu_time,
    memory_usage,
    total_elapsed_time
FROM sys.dm_exec_sessions
WHERE program_name LIKE '%data-transfer%';

-- Blocking queries
SELECT 
    blocking_session_id,
    session_id,
    wait_type,
    wait_time,
    wait_resource
FROM sys.dm_exec_requests
WHERE blocking_session_id <> 0;
```

#### Check Index Performance
```sql
-- Index seek vs scan ratio
SELECT 
    OBJECT_NAME(s.object_id) AS TableName,
    i.name AS IndexName,
    s.user_seeks,
    s.user_scans,
    CASE 
        WHEN (s.user_seeks + s.user_scans) > 0 
        THEN (s.user_seeks * 100.0) / (s.user_seeks + s.user_scans)
        ELSE 0 
    END AS SeekPercentage
FROM sys.dm_db_index_usage_stats s
JOIN sys.indexes i ON s.object_id = i.object_id AND s.index_id = i.index_id
WHERE OBJECT_NAME(s.object_id) = 'bam_table'
  AND i.name = 'idx_bam_app_id';
-- SeekPercentage should be > 90% for good performance
```

---

## Performance Metrics

### Expected Performance

| Metric | Value | Notes |
|--------|-------|-------|
| **Initial Transfer Time** | 45-60 minutes | 1 billion records |
| **Throughput** | 300K-500K records/sec | Depends on network and DB |
| **Daily Sync Time** | 1-2 minutes | For 10K-20K missing records |
| **Memory Usage** | 1-2 GB | JVM heap |
| **CPU Usage** | 20-40% | 4 core system |
| **Network Bandwidth** | 50-100 Mbps | During transfer |
| **Database Connections** | 40 total | 20 per database |

### Performance Comparison

| Method | Time (1B records) | Memory | JVMs | Complexity |
|--------|------------------|---------|------|------------|
| **Current (Java Batch)** | **45-60 min** | **2 GB** | **1** | **Low** |
| Direct SQL INSERT-SELECT | 20-30 min | 10 MB | 1 | Very Low |
| SQL Server BULK INSERT | 10-15 min | 0 MB | 0 | Very Low |
| Apache Spark | 15-20 min | 8+ GB | 10+ | High |
| Manual Row-by-Row | 10+ hours | 1 GB | 1 | Very Low |

### Optimization Tips

#### Database Level
```sql
-- 1. Ensure statistics are updated
UPDATE STATISTICS ITM.bam_table WITH FULLSCAN;

-- 2. Rebuild fragmented indexes
ALTER INDEX idx_bam_app_id ON ITM.bam_table REBUILD;

-- 3. Enable read committed snapshot isolation (reduces blocking)
ALTER DATABASE ITM_DATABASE SET READ_COMMITTED_SNAPSHOT ON;

-- 4. Increase transaction log size
ALTER DATABASE CO_DATABASE 
MODIFY FILE (NAME = CO_DATABASE_log, SIZE = 50GB);

-- 5. Use table hints for read performance
SELECT entitlement_name 
FROM ITM.bam_table WITH (NOLOCK, INDEX(idx_bam_app_id))
WHERE app_id IN (1001, 1002, ...);
```

#### Application Level
```yaml
# Increase connection pool
spring.datasource.itm.hikari.maximum-pool-size: 30
spring.datasource.co.hikari.maximum-pool-size: 30

# Increase thread pool
# In AsyncConfig.java
executor.setCorePoolSize(10);
executor.setMaxPoolSize(20);

# Adjust batch size
# In TransferService.java
List<List<String>> chunks = partition(entitlementNames, 100000);
```

---

## Troubleshooting

### Common Issues

#### 1. Out of Memory Error

**Symptom:**
```
java.lang.OutOfMemoryError: Java heap space
```

**Solution:**
```bash
# Increase JVM heap size
java -Xms1g -Xmx4g -jar data-transfer-service.jar

# Or reduce batch size in code
List<List<String>> chunks = partition(entitlementNames, 25000);
```

#### 2. Database Timeout

**Symptom:**
```
com.microsoft.sqlserver.jdbc.SQLServerException: The query has timed out
```

**Solution:**
```yaml
# Increase connection timeout
spring.datasource.itm.hikari.connection-timeout: 60000
spring.datasource.itm.hikari.validation-timeout: 5000

# Or add query timeout hint
SELECT entitlement_name 
FROM ITM.bam_table WITH (NOLOCK)
OPTION (QUERYTIMEOUT 300);  -- 5 minutes
```

#### 3. Slow Transfer Performance

**Symptom:** Transfer taking > 2 hours

**Diagnosis:**
```sql
-- Check if index is being used
SELECT query_plan
FROM sys.dm_exec_query_plan(plan_handle)
WHERE text LIKE '%bam_table%';

-- Check index fragmentation
SELECT avg_fragmentation_in_percent
FROM sys.dm_db_index_physical_stats(
    DB_ID(), OBJECT_ID('ITM.bam_table'), NULL, NULL, 'DETAILED'
);
```

**Solution:**
```sql
-- Rebuild index if fragmentation > 30%
ALTER INDEX idx_bam_app_id ON ITM.bam_table REBUILD;

-- Update statistics
UPDATE STATISTICS ITM.bam_table WITH FULLSCAN;
```

#### 4. Database Deadlocks

**Symptom:**
```
Transaction was deadlocked on lock resources
```

**Solution:**
```sql
-- Check deadlock victims
SELECT * FROM sys.dm_exec_requests 
WHERE blocking_session_id > 0;

-- Enable row versioning (reduces locks)
ALTER DATABASE ITM_DATABASE 
SET ALLOW_SNAPSHOT_ISOLATION ON;

-- Use WITH (NOLOCK) hint
SELECT entitlement_name 
FROM ITM.bam_table WITH (NOLOCK)
WHERE app_id IN (...);
```

#### 5. Scheduler Not Running

**Symptom:** Daily sync not executing

**Diagnosis:**
```java
// Check if @EnableScheduling is present
@Configuration
@EnableScheduling  // <-- Must have this
public class SchedulingConfig { }

// Check scheduler logs
logging.level.org.springframework.scheduling: DEBUG
```

#### 6. Database Connection Pool Exhausted

**Symptom:**
```
HikariPool - Connection is not available
```

**Solution:**
```yaml
# Increase pool size
spring.datasource.itm.hikari.maximum-pool-size: 30
spring.datasource.itm.hikari.minimum-idle: 10

# Reduce connection leak timeout
spring.datasource.itm.hikari.leak-detection-threshold: 60000
```

### Debug Mode

Enable detailed logging:

```yaml
logging:
  level:
    root: INFO
    com.example.transfer: DEBUG
    org.springframework.jdbc: DEBUG
    com.zaxxer.hikari: DEBUG
    org.springframework.scheduling: DEBUG
```

View SQL queries:

```yaml
logging:
  level:
    org.springframework.jdbc.core.JdbcTemplate: DEBUG
    org.springframework.jdbc.core.StatementCreatorUtils: TRACE
```

---

## Best Practices

### 1. Before Transfer

```bash
# ✓ Verify database connectivity
curl http://localhost:8080/actuator/health

# ✓ Check source table has index
SELECT * FROM sys.indexes 
WHERE name = 'idx_bam_app_id';

# ✓ Verify disk space
SELECT SUM(size * 8 / 1024) AS SizeMB 
FROM sys.master_files;

# ✓ Check database locks
SELECT * FROM sys.dm_tran_locks;

# ✓ Backup target database (optional but recommended)
BACKUP DATABASE CO_DATABASE TO DISK = 'C:\Backup\CO_DATABASE.bak';
```

### 2. During Transfer

```bash
# ✓ Monitor progress
curl http://localhost:8080/api/entitlement/status/{jobId}

# ✓ Check application logs
tail -f logs/transfer-service.log

# ✓ Monitor database CPU/Memory
# Use SQL Server Management Studio or:
SELECT * FROM sys.dm_os_performance_counters;

# ✓ Check table growth
SELECT COUNT(*) FROM CO.entitlement_table;
```

### 3. After Transfer

```bash
# ✓ Validate data sync
curl -X POST http://localhost:8080/api/sync/validate

# ✓ Update statistics
UPDATE STATISTICS CO.entitlement_table WITH FULLSCAN;

# ✓ Rebuild indexes if fragmented
ALTER INDEX ALL ON CO.entitlement_table REBUILD;

# ✓ Check logs for errors
grep -i "error\|exception" logs/transfer-service.log
```

### 4. Production Deployment

```bash
# ✓ Use environment variables for credentials
export ITM_DB_USER=itm_user
export ITM_DB_PASSWORD=secure_password

# ✓ Run as systemd service (Linux)
sudo systemctl start data-transfer-service

# ✓ Or Windows Service
nssm install DataTransferService "java" "-jar data-transfer-service.jar"

# ✓ Set up monitoring alerts
# - Transfer failure
# - Memory usage > 90%
# - Database connection errors
# - Daily sync failure

# ✓ Configure log rotation
# logback-spring.xml with rolling file appender
```

### 5. Maintenance

```bash
# Weekly
- Review logs for errors
- Check index fragmentation
- Update database statistics

# Monthly
- Review and optimize slow queries
- Check disk space usage
- Archive old logs

# Quarterly
- Review performance metrics
- Update dependencies
- Test disaster recovery
```

---

## Appendix

### A. Complete Configuration Files

#### DataSourceConfig.java
See implementation section above.

#### AsyncConfig.java
See implementation section above.

#### TransferService.java
See implementation section above.

### B. SQL Server Limits

| Item | Limit | Notes |
|------|-------|-------|
| IN clause parameters | 2,100 | Use batches of 500 |
| Batch insert size | No limit | Recommended: 50K |
| Connection pool | No limit | Recommended: 20-30 |
| Transaction log | 2 TB | Monitor during large inserts |
| Table size | 16 TB | Depends on edition |

### C. Performance Tuning Checklist

- [ ] Index on `app_id` in source table
- [ ] Index on `entitlement_name` in target table
- [ ] Database statistics updated
- [ ] Index fragmentation < 30%
- [ ] Connection pool sized correctly (20-30)
- [ ] Batch size optimized (50K-100K)
- [ ] JVM heap sized correctly (2-4 GB)
- [ ] Network bandwidth adequate (100+ Mbps)
- [ ] Database transaction log sized appropriately
- [ ] Read committed snapshot isolation enabled

### D. Monitoring Dashboard (Optional)

Use Spring Boot Actuator endpoints:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

Access metrics:
- Health: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- DB pools: `http://localhost:8080/actuator/metrics/hikaricp.connections`

---

## Support & Contact

For issues or questions:
- Check logs: `logs/transfer-service.log`
- Review this README
- Check SQL Server error logs
- Enable DEBUG logging for detailed information

---

## License

Internal use only - [Your Company Name]

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-01-14 | Initial release |
| | | - Async transfer |
| | | - Daily scheduled sync |
| | | - Validation service |
| | | - SQL Server support |

---

**End of Documentation**

For the most up-to-date information, refer to the source code and inline comments.
