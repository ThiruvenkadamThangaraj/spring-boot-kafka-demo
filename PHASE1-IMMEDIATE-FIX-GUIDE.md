# Phase 1: Immediate Fix Implementation Guide

## Emergency Response: Apply Timeouts & Concurrency Limits (Same Day)

**Time Required**: 1-2 hours  
**Risk Level**: Low (fail-safe changes)  
**Rollback**: Easy (revert config file)

---

## Step-by-Step Implementation

### Step 1: Backup Current Configuration (5 minutes)

```powershell
# Backup current application.properties
cd "c:\Users\thiru_qoss8b1\Downloads\demo\demo"
Copy-Item "src/main/resources/application.properties" "src/main/resources/application.properties.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

# Backup current RestTemplate config (if exists)
Get-ChildItem -Recurse -Filter "*RestTemplate*.java" | ForEach-Object {
    Copy-Item $_.FullName "$($_.FullName).backup"
}
```

---

### Step 2: Add Timeout Configuration to application.properties (10 minutes)

**Location**: `src/main/resources/application.properties`

```properties
# ===== PHASE 1 FIX: TIMEOUT CONFIGURATION =====

# Server Thread Pool Settings
server.tomcat.threads.max=200
server.tomcat.threads.min-spare=10
server.tomcat.accept-count=100
server.tomcat.max-connections=10000

# Connection timeouts for outbound HTTP calls
# These prevent threads from hanging indefinitely
http.client.connect-timeout=2000          # 2 seconds to establish connection
http.client.read-timeout=5000             # 5 seconds to read response
http.client.connection-request-timeout=3000  # 3 seconds to get connection from pool

# HikariCP Database Connection Pool Timeouts
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=3000       # 3 sec max wait for connection
spring.datasource.hikari.idle-timeout=600000           # 10 min idle before eviction
spring.datasource.hikari.max-lifetime=1800000          # 30 min max connection lifetime
spring.datasource.hikari.leak-detection-threshold=60000  # Alert if held > 60 sec

# SQL Statement Timeout (prevents long-running queries)
spring.datasource.hikari.data-source-properties.queryTimeout=30  # 30 seconds

# ===== END PHASE 1 FIX =====
```

---

### Step 3: Create/Update RestTemplate Configuration (20 minutes)

**Create**: `src/main/java/com/example/config/HttpClientConfig.java`

```java
package com.example.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * PHASE 1 FIX: HTTP Client with strict timeouts and concurrency limits
 * 
 * Purpose: Prevent thread starvation from slow downstream services
 * - Connection timeout: 2 seconds
 * - Read timeout: 5 seconds  
 * - Concurrency limit: 10 concurrent calls max
 */
@Configuration
public class HttpClientConfig {
    
    @Value("${http.client.connect-timeout:2000}")
    private int connectTimeout;
    
    @Value("${http.client.read-timeout:5000}")
    private int readTimeout;
    
    @Value("${http.client.connection-request-timeout:3000}")
    private int connectionRequestTimeout;
    
    @Value("${http.client.max-connections:50}")
    private int maxConnections;
    
    @Value("${http.client.max-per-route:20}")
    private int maxPerRoute;
    
    /**
     * RestTemplate with Apache HttpClient 5.x and aggressive timeouts
     */
    @Bean
    public RestTemplate restTemplate() {
        // Configure connection pooling
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        
        // Set connection timeouts
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
            .setSocketTimeout(Timeout.ofMilliseconds(readTimeout))
            .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        
        // Configure request timeouts
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
            .build();
        
        // Build HTTP client with timeouts
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();
        
        // Create RestTemplate with configured HTTP client
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Add error handler for better logging
        restTemplate.setErrorHandler(new TimeoutAwareErrorHandler());
        
        return restTemplate;
    }
    
    /**
     * Semaphore to limit concurrent downstream calls
     * Prevents overwhelming slow services
     */
    @Bean
    public Semaphore downstreamConcurrencyLimit() {
        return new Semaphore(10);  // Max 10 concurrent calls
    }
}
```

**Create**: `src/main/java/com/example/config/TimeoutAwareErrorHandler.java`

```java
package com.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;

/**
 * Custom error handler to log timeout issues
 */
@Slf4j
public class TimeoutAwareErrorHandler extends DefaultResponseErrorHandler {
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        log.error("HTTP Error: Status={}, StatusText={}", 
            response.getStatusCode(), 
            response.getStatusText());
        
        super.handleError(response);
    }
}
```

---

### Step 4: Update Service to Use Concurrency Limit (20 minutes)

**Example**: Update your service that calls downstream APIs

**Before (No Protection)**:
```java
@Service
@Slf4j
public class DownstreamService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public String callExternalApi(String requestData) {
        // ❌ No timeout, no concurrency limit
        // All 200 threads can call this simultaneously
        String url = "https://external-api.com/endpoint";
        return restTemplate.postForObject(url, requestData, String.class);
    }
}
```

**After (With Phase 1 Protection)**:
```java
@Service
@Slf4j
public class DownstreamService {
    
    @Autowired
    private RestTemplate restTemplate;  // Now has timeouts from config
    
    @Autowired
    private Semaphore downstreamConcurrencyLimit;
    
    /**
     * Call external API with concurrency limit
     * 
     * Phase 1 Protection:
     * - RestTemplate has 2s connect timeout, 5s read timeout
     * - Semaphore limits to 10 concurrent calls
     * - If all 10 slots busy, thread waits max 1 second then fails
     */
    public String callExternalApi(String requestData) {
        boolean acquired = false;
        long startTime = System.currentTimeMillis();
        
        try {
            // Try to acquire permit (max wait 1 second)
            acquired = downstreamConcurrencyLimit.tryAcquire(1, TimeUnit.SECONDS);
            
            if (!acquired) {
                log.warn("Concurrency limit reached (10/10 slots busy), rejecting request");
                throw new ServiceUnavailableException(
                    "Downstream service overloaded, please retry later"
                );
            }
            
            log.debug("Acquired downstream call permit (available: {})", 
                downstreamConcurrencyLimit.availablePermits());
            
            // Make the actual HTTP call
            String url = "https://external-api.com/endpoint";
            String response = restTemplate.postForObject(url, requestData, String.class);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Downstream call completed in {}ms", duration);
            
            return response;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for downstream permit");
            throw new RuntimeException("Request interrupted", e);
            
        } catch (ResourceAccessException e) {
            // Timeout exception from RestTemplate
            long duration = System.currentTimeMillis() - startTime;
            log.error("Downstream timeout after {}ms: {}", duration, e.getMessage());
            throw new ServiceUnavailableException("Downstream service timeout", e);
            
        } finally {
            if (acquired) {
                downstreamConcurrencyLimit.release();
                log.debug("Released downstream call permit (available: {})", 
                    downstreamConcurrencyLimit.availablePermits());
            }
        }
    }
}
```

**Create Exception Class** (if not exists):
```java
package com.example.exception;

public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
    
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

### Step 5: Add Monitoring Endpoints (15 minutes)

**Create**: `src/main/java/com/example/controller/HealthController.java`

```java
package com.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

/**
 * Health check endpoint to monitor Phase 1 fix effectiveness
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {
    
    @Autowired
    private Semaphore downstreamConcurrencyLimit;
    
    /**
     * GET /api/health/status
     * Returns thread pool and concurrency status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Thread pool info
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        
        status.put("threads", Map.of(
            "current", threadCount,
            "peak", peakThreadCount,
            "daemon", threadBean.getDaemonThreadCount()
        ));
        
        // Downstream concurrency limit info
        int availablePermits = downstreamConcurrencyLimit.availablePermits();
        int queueLength = downstreamConcurrencyLimit.getQueueLength();
        
        status.put("downstreamConcurrency", Map.of(
            "maxConcurrent", 10,
            "available", availablePermits,
            "inUse", 10 - availablePermits,
            "queued", queueLength,
            "utilizationPercent", ((10 - availablePermits) * 100.0 / 10)
        ));
        
        // Overall health
        boolean healthy = availablePermits > 0 && threadCount < 180; // 90% of max 200
        status.put("status", healthy ? "HEALTHY" : "DEGRADED");
        
        log.info("Health check: threads={}, downstream={}/10 busy", 
            threadCount, 10 - availablePermits);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * GET /api/health/quick
     * Quick health check for load balancer
     */
    @GetMapping("/quick")
    public ResponseEntity<String> quickHealth() {
        int availablePermits = downstreamConcurrencyLimit.availablePermits();
        
        if (availablePermits == 0) {
            // All downstream slots busy
            return ResponseEntity.status(503).body("Service degraded");
        }
        
        return ResponseEntity.ok("OK");
    }
}
```

---

### Step 6: Update pom.xml Dependencies (10 minutes)

**Add to**: `pom.xml`

```xml
<dependencies>
    <!-- Apache HttpClient 5.x for better timeout control -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
        <version>5.2.1</version>
    </dependency>
    
    <!-- Lombok for cleaner code -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
    
    <!-- Spring Boot Actuator for monitoring -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
</dependencies>
```

---

### Step 7: Build and Deploy (15 minutes)

```powershell
# Navigate to project directory
cd "c:\Users\thiru_qoss8b1\Downloads\demo\demo"

# Clean build
mvn clean package -DskipTests

# Verify JAR created
ls target/*.jar

# Stop current application (if running)
# Option 1: If running as service
Stop-Service -Name "YourServiceName"

# Option 2: If running in terminal
# Press Ctrl+C in the terminal window

# Backup old JAR
Copy-Item "target/*.jar" "target/backup-$(Get-Date -Format 'yyyyMMdd-HHmmss').jar"

# Start application with new configuration
java -jar target/your-application.jar

# Or if Windows Service
Start-Service -Name "YourServiceName"
```

---

### Step 8: Verify Fix is Working (10 minutes)

#### Test 1: Check Health Endpoint

```powershell
# Check overall health
Invoke-RestMethod -Uri "http://localhost:8080/api/health/status" | ConvertTo-Json

# Expected output:
{
  "status": "HEALTHY",
  "threads": {
    "current": 45,
    "peak": 52,
    "daemon": 38
  },
  "downstreamConcurrency": {
    "maxConcurrent": 10,
    "available": 10,
    "inUse": 0,
    "queued": 0,
    "utilizationPercent": 0.0
  }
}
```

#### Test 2: Verify Timeouts are Applied

```powershell
# Test timeout behavior (simulate slow downstream)
# This should fail after 5 seconds (read timeout)
Measure-Command {
    try {
        # Call your endpoint that uses downstream service
        Invoke-RestMethod -Uri "http://localhost:8080/api/your-endpoint" -Method Post -Body '{"test": "data"}'
    } catch {
        Write-Host "Error: $($_.Exception.Message)"
    }
}

# Should show: TotalSeconds around 5-6 (not 30+)
```

#### Test 3: Verify Concurrency Limit

```powershell
# Start 20 concurrent requests (should only allow 10 at a time)
$jobs = 1..20 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($id)
        $response = Invoke-RestMethod -Uri "http://localhost:8080/api/your-endpoint" -Method Post -Body "{`"id`": $id}"
        Write-Output "Request $id completed"
    } -ArgumentList $_
}

# Monitor health endpoint
while ((Get-Job -State Running).Count -gt 0) {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health/status"
    Write-Host "In-use: $($health.downstreamConcurrency.inUse)/10, Queued: $($health.downstreamConcurrency.queued)"
    Start-Sleep -Seconds 1
}

# Clean up jobs
Get-Job | Remove-Job
```

#### Test 4: Check Application Logs

```powershell
# Check logs for timeout messages
Get-Content "logs/application.log" -Tail 50 | Select-String "timeout|Downstream"

# Should see logs like:
# "Acquired downstream call permit (available: 9)"
# "Downstream call completed in 234ms"
# "Released downstream call permit (available: 10)"
```

---

### Step 9: Monitoring During Peak Traffic

```powershell
# Create monitoring script
$monitorScript = @'
while ($true) {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/api/health/status"
    $timestamp = Get-Date -Format "HH:mm:ss"
    
    $threads = $health.threads.current
    $inUse = $health.downstreamConcurrency.inUse
    $queued = $health.downstreamConcurrency.queued
    $status = $health.status
    
    Write-Host "[$timestamp] Status: $status | Threads: $threads | Downstream: $inUse/10 | Queued: $queued"
    
    # Alert if utilization high
    if ($threads -gt 160) {
        Write-Host "⚠️  WARNING: Thread count high ($threads/200)" -ForegroundColor Yellow
    }
    if ($inUse -eq 10) {
        Write-Host "⚠️  WARNING: All downstream slots busy (10/10)" -ForegroundColor Yellow
    }
    
    Start-Sleep -Seconds 5
}
'@

# Run monitoring
Invoke-Expression $monitorScript
```

---

## Expected Results After Phase 1 Fix

### Before Fix:
```
Thread Pool Utilization: 98-100% (190+ threads busy)
API Latency (P95): 30-45 seconds
Downstream Call Queue: 50+ waiting
Error Rate: 15%
Connection Pool: Exhausted (20/20 busy)
```

### After Phase 1 Fix:
```
Thread Pool Utilization: 20-40% (40-80 threads busy)
API Latency (P95): 2-5 seconds (timeout enforced)
Downstream Call Queue: 0-2 waiting (limited by semaphore)
Error Rate: 5-8% (higher short-term due to fast failures, but system stable)
Connection Pool: 5-10/20 busy (healthy)
```

---

## Rollback Plan (If Issues Occur)

```powershell
# Stop application
Stop-Service -Name "YourServiceName"  # Or Ctrl+C

# Restore backup configuration
Copy-Item "src/main/resources/application.properties.backup-*" "src/main/resources/application.properties" -Force

# Restore backup JAR
Copy-Item "target/backup-*.jar" "target/your-application.jar" -Force

# Restart with old version
Start-Service -Name "YourServiceName"  # Or java -jar ...

# Verify rollback
Invoke-RestMethod -Uri "http://localhost:8080/api/health/quick"
```

---

## Troubleshooting

### Issue 1: "Too many timeout errors after Phase 1"

**Cause**: Downstream service is genuinely slow/broken  
**Solution**: This is expected! Phase 1 exposes the real problem. Move to Phase 2 (Circuit Breaker) for graceful degradation.

```java
// Temporary workaround: Increase timeout slightly
http.client.read-timeout=10000  // 10 seconds instead of 5
```

### Issue 2: "Concurrency limit causing user errors"

**Cause**: More than 10 concurrent users need downstream service  
**Solution**: Increase semaphore limit temporarily

```java
@Bean
public Semaphore downstreamConcurrencyLimit() {
    return new Semaphore(20);  // Increase from 10 to 20
}
```

### Issue 3: "Health endpoint returns 503"

**Cause**: All downstream slots busy (expected during high load)  
**Action**: This is working as designed - monitor and move to Phase 2

---

## Next Steps: Phase 2 (Circuit Breaker)

Once Phase 1 is stable (24-48 hours), implement Phase 2:
1. Add Resilience4j dependency
2. Configure circuit breaker
3. Implement fallback responses
4. Add bulkhead isolation

See `PHASE2-RESILIENCE-GUIDE.md` for details.

---

## Quick Reference Card

```
PHASE 1 FIX SUMMARY
===================

What we fixed:
✅ RestTemplate timeouts: 2s connect, 5s read
✅ Connection pool timeouts: 3s max wait
✅ Concurrency limit: 10 max concurrent downstream calls
✅ Health monitoring: /api/health/status

How to verify:
1. curl http://localhost:8080/api/health/status
2. Check thread count < 80 (40% of 200)
3. Check downstream.inUse ≤ 10
4. Check logs for "timeout" messages

Emergency commands:
# Check health
curl http://localhost:8080/api/health/status

# Restart service
Restart-Service -Name YourService

# View logs
Get-Content logs/application.log -Tail 50

# Rollback
Copy-Item backup-*.jar target/app.jar; Restart-Service
```

---

**Status**: Phase 1 complete. System now fails fast instead of hanging.  
**Next**: Monitor for 24-48 hours, then implement Phase 2 (Circuit Breaker).
