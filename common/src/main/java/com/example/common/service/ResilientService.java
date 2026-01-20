package com.example.common.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Resilient Service demonstrating Circuit Breaker, Retry, Timeout, and Bulkhead patterns
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@Service
public class ResilientService {
    
    private static final Logger logger = LoggerFactory.getLogger(ResilientService.class);
    
    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    // ========================================
    // CIRCUIT BREAKER EXAMPLES
    // ========================================
    
    /**
     * Circuit Breaker protects against cascading failures
     * 
     * States:
     * - CLOSED: Normal operation, calls go through
     * - OPEN: Circuit is open, calls fail fast, fallback executed
     * - HALF_OPEN: Testing if service recovered
     */
    @CircuitBreaker(name = "samplingService", fallbackMethod = "getSamplingDataFallback")
    public Map<String, Object> getSamplingData(String id) {
        logger.info("Calling Sampling Service for ID: {}", id);
        
        // Simulate external service call
        if (restTemplate != null) {
            String url = "http://sampling-service:8082/api/samples/" + id;
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.getForEntity(url, (Class<Map<String, Object>>)(Class<?>)Map.class);
            return response.getBody();
        }
        
        // Demo: Simulate failure
        try {
            long idNum = Long.parseLong(id);
            if (idNum > 100) {
                throw new RuntimeException("Sampling service unavailable");
            }
        } catch (NumberFormatException e) {
            // If not a number, skip failure simulation
        }
        
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("samplingData", "Sample Data " + id);
        data.put("status", "SUCCESS");
        return data;
    }
    
    /**
     * Fallback method for getSamplingData
     * Called when circuit is OPEN or method throws exception
     */
    private Map<String, Object> getSamplingDataFallback(String id, Exception ex) {
        logger.warn("Circuit breaker activated for Sampling Service. Using fallback for ID: {}. Error: {}", 
                    id, ex.getMessage());
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id);
        fallback.put("samplingData", "Cached/Default Data");
        fallback.put("status", "FALLBACK");
        fallback.put("message", "Sampling service temporarily unavailable");
        fallback.put("error", ex.getMessage());
        return fallback;
    }
    
    // ========================================
    // RETRY EXAMPLES
    // ========================================
    
    /**
     * Retry automatically retries failed calls
     * 
     * Configuration:
     * - maxAttempts: 3
     * - waitDuration: 300ms between retries
     * - Exponential backoff: 300ms, 600ms, 1200ms
     */
    @Retry(name = "evaluationService", fallbackMethod = "processEvaluationFallback")
    public Map<String, Object> processEvaluation(String evaluationId) {
        logger.info("Processing evaluation ID: {}", evaluationId);
        
        // Simulate transient failure (network glitch)
        if (Math.random() < 0.3) {  // 30% chance of failure
            logger.warn("Transient failure occurred, will retry...");
            throw new RuntimeException("Temporary network error");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("evaluationId", evaluationId);
        result.put("status", "PROCESSED");
        result.put("score", 85);
        return result;
    }
    
    private Map<String, Object> processEvaluationFallback(String evaluationId, Exception ex) {
        logger.error("All retry attempts exhausted for evaluation ID: {}. Error: {}", 
                     evaluationId, ex.getMessage());
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("evaluationId", evaluationId);
        fallback.put("status", "RETRY_FAILED");
        fallback.put("message", "Processing failed after multiple retries");
        fallback.put("error", ex.getMessage());
        return fallback;
    }
    
    // ========================================
    // TIMEOUT (TIME LIMITER) EXAMPLES
    // ========================================
    
    /**
     * Time Limiter enforces timeout on async operations
     * 
     * Configuration:
     * - timeoutDuration: 2s for samplingService
     * - cancelRunningFuture: true (cancels if timeout)
     */
    @TimeLimiter(name = "samplingService", fallbackMethod = "getDataWithTimeoutFallback")
    @CircuitBreaker(name = "samplingService")
    public CompletableFuture<Map<String, Object>> getDataWithTimeout(String id) {
        return CompletableFuture.supplyAsync(() -> {
            logger.info("Fetching data with timeout for ID: {}", id);
            
            try {
                // Simulate slow operation
                long idNum = 0;
                try { idNum = Long.parseLong(id); } catch (NumberFormatException e) {}
                if (idNum % 2 == 0) {
                    Thread.sleep(3000);  // Will timeout!
                } else {
                    Thread.sleep(500);   // Fast response
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Operation interrupted");
            }
            
            Map<String, Object> data = new HashMap<>();
            data.put("id", id);
            data.put("data", "Fetched data for " + id);
            return data;
        });
    }
    
    private CompletableFuture<Map<String, Object>> getDataWithTimeoutFallback(String id, TimeoutException ex) {
        logger.warn("Operation timed out for ID: {}. Using fallback.", id);
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id);
        fallback.put("data", "Timeout - using cached data");
        fallback.put("status", "TIMEOUT");
        return CompletableFuture.completedFuture(fallback);
    }
    
    // ========================================
    // BULKHEAD EXAMPLES
    // ========================================
    
    /**
     * Bulkhead limits concurrent calls to prevent resource exhaustion
     * 
     * Configuration:
     * - maxConcurrentCalls: 5 for jiraService
     * - maxWaitDuration: 1s
     * 
     * If more than 5 concurrent calls, additional calls wait up to 1s
     * After 1s, they are rejected with BulkheadFullException
     */
    @Bulkhead(name = "jiraService", fallbackMethod = "createJiraTicketFallback")
    @Retry(name = "jiraService")
    public Map<String, Object> createJiraTicket(String title, String description) {
        logger.info("Creating JIRA ticket: {}", title);
        
        try {
            // Simulate JIRA API call (slow operation)
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operation interrupted");
        }
        
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("ticketId", "TICKET-" + System.currentTimeMillis());
        ticket.put("title", title);
        ticket.put("description", description);
        ticket.put("status", "CREATED");
        return ticket;
    }
    
    private Map<String, Object> createJiraTicketFallback(String title, String description, Exception ex) {
        logger.error("Failed to create JIRA ticket: {}. Error: {}", title, ex.getMessage());
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("title", title);
        fallback.put("description", description);
        fallback.put("status", "FAILED");
        fallback.put("message", "JIRA service overloaded or unavailable");
        fallback.put("error", ex.getClass().getSimpleName());
        return fallback;
    }
    
    // ========================================
    // COMBINED PATTERNS
    // ========================================
    
    /**
     * Combining Circuit Breaker + Retry + Bulkhead
     * 
     * Order of execution:
     * 1. Bulkhead: Limit concurrent calls
     * 2. Circuit Breaker: Fail fast if service down
     * 3. Retry: Retry transient failures
     * 4. Time Limiter: Enforce timeout
     */
    @Bulkhead(name = "evaluationService")
    @CircuitBreaker(name = "evaluationService", fallbackMethod = "complexOperationFallback")
    @Retry(name = "evaluationService")
    public Map<String, Object> complexOperation(String id) {
        logger.info("Executing complex operation for ID: {}", id);
        
        // Simulate various scenarios
        double random = Math.random();
        
        if (random < 0.1) {
            // 10% chance: slow operation (will be caught by time limiter)
            try {
                Thread.sleep(6000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (random < 0.3) {
            // 20% chance: transient failure (will be retried)
            throw new RuntimeException("Transient error");
        }
        
        // Success case
        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("operation", "complex");
        result.put("result", "SUCCESS");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }
    
    private Map<String, Object> complexOperationFallback(String id, Exception ex) {
        logger.error("Complex operation failed for ID: {}. Using fallback. Error: {}", 
                     id, ex.getMessage());
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id);
        fallback.put("operation", "complex");
        fallback.put("result", "FALLBACK");
        fallback.put("error", ex.getClass().getSimpleName());
        fallback.put("message", "Operation failed, using default behavior");
        return fallback;
    }
    
    // ========================================
    // HEALTH CHECK METHODS
    // ========================================
    
    /**
     * Check if a service is available (no circuit breaker/retry)
     */
    public boolean isServiceHealthy(String serviceName) {
        try {
            if (restTemplate != null) {
                String url = String.format("http://%s/actuator/health", serviceName);
                ResponseEntity<Map<String, Object>> response = restTemplate.exchange(url, HttpMethod.GET, null, new ParameterizedTypeReference<Map<String, Object>>() {});
                return response.getStatusCode() == HttpStatus.OK;
            }
            return true;  // Mock: assume healthy
        } catch (Exception e) {
            logger.warn("Health check failed for {}: {}", serviceName, e.getMessage());
            return false;
        }
    }
}
