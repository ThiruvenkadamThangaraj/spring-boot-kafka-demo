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
 * Health Check Controller
 * 
 * Monitors Phase 1 fix effectiveness:
 * - Thread pool utilization
 * - Downstream concurrency usage
 * - Overall system health
 * 
 * Endpoints:
 * - GET /api/health/status - Detailed health information
 * - GET /api/health/quick - Quick OK/503 for load balancer
 */
@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {
    
    @Autowired
    private Semaphore downstreamConcurrencyLimit;
    
    /**
     * GET /api/health/status
     * 
     * Returns detailed system health including:
     * - Thread count and utilization
     * - Downstream concurrency metrics
     * - Overall health status
     * 
     * Example response:
     * {
     *   "status": "HEALTHY",
     *   "threads": {
     *     "current": 45,
     *     "peak": 52,
     *     "daemon": 38
     *   },
     *   "downstreamConcurrency": {
     *     "maxConcurrent": 10,
     *     "available": 8,
     *     "inUse": 2,
     *     "queued": 0,
     *     "utilizationPercent": 20.0
     *   }
     * }
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        Map<String, Object> status = new HashMap<>();
        
        // Thread pool information
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int peakThreadCount = threadBean.getPeakThreadCount();
        int daemonThreadCount = threadBean.getDaemonThreadCount();
        
        Map<String, Integer> threadInfo = new HashMap<>();
        threadInfo.put("current", threadCount);
        threadInfo.put("peak", peakThreadCount);
        threadInfo.put("daemon", daemonThreadCount);
        status.put("threads", threadInfo);
        
        // Downstream concurrency limit information
        int availablePermits = downstreamConcurrencyLimit.availablePermits();
        int queueLength = downstreamConcurrencyLimit.getQueueLength();
        int maxConcurrent = 10;  // Must match HttpClientConfig value
        int inUse = maxConcurrent - availablePermits;
        double utilizationPercent = (inUse * 100.0) / maxConcurrent;
        
        Map<String, Object> concurrencyInfo = new HashMap<>();
        concurrencyInfo.put("maxConcurrent", maxConcurrent);
        concurrencyInfo.put("available", availablePermits);
        concurrencyInfo.put("inUse", inUse);
        concurrencyInfo.put("queued", queueLength);
        concurrencyInfo.put("utilizationPercent", Math.round(utilizationPercent * 10.0) / 10.0);
        status.put("downstreamConcurrency", concurrencyInfo);
        
        // Determine overall health
        // HEALTHY: threads < 90% of max (200), downstream permits available
        // DEGRADED: threads >= 90% OR all downstream slots busy
        int maxThreads = 200;  // From server.tomcat.threads.max
        boolean threadsHealthy = threadCount < (maxThreads * 0.9);
        boolean downstreamHealthy = availablePermits > 0;
        
        String healthStatus;
        if (threadsHealthy && downstreamHealthy) {
            healthStatus = "HEALTHY";
        } else if (!threadsHealthy && !downstreamHealthy) {
            healthStatus = "CRITICAL";
        } else {
            healthStatus = "DEGRADED";
        }
        status.put("status", healthStatus);
        
        // Add warning messages if unhealthy
        if (!threadsHealthy) {
            status.put("warning", "High thread count: " + threadCount + "/" + maxThreads);
        }
        if (!downstreamHealthy) {
            status.put("warning", "All downstream slots busy (" + maxConcurrent + "/" + maxConcurrent + ")");
        }
        
        log.info("Health check: status={}, threads={}/{}, downstream={}/{}", 
                 healthStatus, threadCount, maxThreads, inUse, maxConcurrent);
        
        return ResponseEntity.ok(status);
    }
    
    /**
     * GET /api/health/quick
     * 
     * Quick health check for load balancers
     * Returns:
     * - 200 OK if system healthy
     * - 503 Service Unavailable if degraded
     * 
     * Used by:
     * - Load balancer health probes
     * - Kubernetes liveness/readiness probes
     */
    @GetMapping("/quick")
    public ResponseEntity<String> quickHealth() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int availablePermits = downstreamConcurrencyLimit.availablePermits();
        
        // Check critical thresholds
        boolean critical = (threadCount >= 180) || (availablePermits == 0);
        
        if (critical) {
            log.warn("Health check FAILED: threads={}, downstreamAvailable={}", 
                     threadCount, availablePermits);
            return ResponseEntity.status(503).body("Service degraded");
        }
        
        return ResponseEntity.ok("OK");
    }
    
    /**
     * GET /api/health/metrics
     * 
     * Returns metrics in Prometheus format
     * Useful for monitoring systems
     */
    @GetMapping("/metrics")
    public ResponseEntity<String> getMetrics() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        int threadCount = threadBean.getThreadCount();
        int availablePermits = downstreamConcurrencyLimit.availablePermits();
        int inUse = 10 - availablePermits;
        
        StringBuilder metrics = new StringBuilder();
        metrics.append("# HELP app_threads_current Current thread count\n");
        metrics.append("# TYPE app_threads_current gauge\n");
        metrics.append("app_threads_current ").append(threadCount).append("\n\n");
        
        metrics.append("# HELP app_downstream_available Available downstream slots\n");
        metrics.append("# TYPE app_downstream_available gauge\n");
        metrics.append("app_downstream_available ").append(availablePermits).append("\n\n");
        
        metrics.append("# HELP app_downstream_in_use Downstream slots in use\n");
        metrics.append("# TYPE app_downstream_in_use gauge\n");
        metrics.append("app_downstream_in_use ").append(inUse).append("\n");
        
        return ResponseEntity.ok()
            .header("Content-Type", "text/plain; version=0.0.4")
            .body(metrics.toString());
    }
}
