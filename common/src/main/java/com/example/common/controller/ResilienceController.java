package com.example.common.controller;

import com.example.common.service.ResilientService;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

/**
 * Controller demonstrating Resilience4j patterns:
 * - Circuit Breaker
 * - Retry
 * - Timeout (Time Limiter)
 * - Bulkhead
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@RestController
@RequestMapping("/api/resilience")
@Tag(name = "Resilience Patterns", description = "Circuit Breaker, Retry, Timeout, Bulkhead examples")
public class ResilienceController {
    
    private static final Logger logger = LoggerFactory.getLogger(ResilienceController.class);
    
    @Autowired
    private ResilientService resilientService;
    
    // ========================================
    // CIRCUIT BREAKER ENDPOINTS
    // ========================================
    
    @GetMapping("/circuit-breaker/sampling/{id}")
    @Operation(summary = "Circuit Breaker Example", 
               description = "Demonstrates circuit breaker pattern. Try with ID > 100 to trigger failures.")
    public ResponseEntity<?> testCircuitBreaker(@PathVariable String id) {
        try {
            Map<String, Object> result = resilientService.getSamplingData(id);
            return ResponseEntity.ok(result);
        } catch (CallNotPermittedException e) {
            // Circuit is OPEN
            logger.warn("Circuit breaker is OPEN for sampling service");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                        "error", "Service unavailable",
                        "message", "Circuit breaker is OPEN. Service is temporarily unavailable.",
                        "hint", "Wait 5 seconds for circuit to recover"
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    // ========================================
    // RETRY ENDPOINTS
    // ========================================
    
    @PostMapping("/retry/evaluation/{id}")
    @Operation(summary = "Retry Example", 
               description = "Demonstrates automatic retry on transient failures")
    public ResponseEntity<?> testRetry(@PathVariable String id) {
        try {
            Map<String, Object> result = resilientService.processEvaluation(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Evaluation processing failed after retries: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "error", "Processing failed",
                        "message", "All retry attempts exhausted",
                        "evaluationId", id
                    ));
        }
    }
    
    // ========================================
    // TIMEOUT ENDPOINTS
    // ========================================
    
    @GetMapping("/timeout/data/{id}")
    @Operation(summary = "Timeout Example", 
               description = "Demonstrates timeout handling. Even IDs timeout (>3s), odd IDs succeed (<1s)")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> testTimeout(@PathVariable String id) {
        return resilientService.getDataWithTimeout(id)
                .thenApply(ResponseEntity::ok)
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof TimeoutException) {
                        logger.warn("Operation timed out for ID: {}", id);
                        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                                .body(Map.of(
                                    "error", "Timeout",
                                    "message", "Operation took too long (>2s)",
                                    "id", id
                                ));
                    }
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", ex.getMessage()));
                });
    }
    
    // ========================================
    // BULKHEAD ENDPOINTS
    // ========================================
    
    @PostMapping("/bulkhead/jira")
    @Operation(summary = "Bulkhead Example", 
               description = "Limits concurrent calls to 5. Send >5 concurrent requests to see rejection.")
    public ResponseEntity<?> testBulkhead(@RequestBody Map<String, String> request) {
        try {
            String title = request.getOrDefault("title", "Test Ticket");
            String description = request.getOrDefault("description", "Test Description");
            
            Map<String, Object> result = resilientService.createJiraTicket(title, description);
            return ResponseEntity.ok(result);
        } catch (BulkheadFullException e) {
            logger.warn("Bulkhead full: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of(
                        "error", "Too many requests",
                        "message", "JIRA service is overloaded. Max 5 concurrent requests allowed.",
                        "hint", "Try again in a few seconds"
                    ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    // ========================================
    // COMBINED PATTERNS
    // ========================================
    
    @PostMapping("/combined/{id}")
    @Operation(summary = "Combined Patterns", 
               description = "Demonstrates Circuit Breaker + Retry + Bulkhead working together")
    public ResponseEntity<?> testCombinedPatterns(@PathVariable String id) {
        try {
            Map<String, Object> result = resilientService.complexOperation(id);
            return ResponseEntity.ok(result);
        } catch (CallNotPermittedException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Circuit breaker is OPEN"));
        } catch (BulkheadFullException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many concurrent requests"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
    
    // ========================================
    // MONITORING ENDPOINTS
    // ========================================
    
    @GetMapping("/health/{serviceName}")
    @Operation(summary = "Service Health Check", 
               description = "Check if a microservice is healthy")
    public ResponseEntity<?> checkServiceHealth(@PathVariable String serviceName) {
        boolean healthy = resilientService.isServiceHealthy(serviceName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("service", serviceName);
        response.put("healthy", healthy);
        response.put("status", healthy ? "UP" : "DOWN");
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/info")
    @Operation(summary = "Resilience Info", 
               description = "Get information about resilience patterns configured")
    public ResponseEntity<?> getResilienceInfo() {
        Map<String, Object> info = new HashMap<>();
        
        info.put("circuitBreakers", Map.of(
            "samplingService", "failureRate=50%, waitDuration=5s",
            "evaluationService", "failureRate=60%, waitDuration=10s",
            "jiraService", "failureRate=50%, waitDuration=15s"
        ));
        
        info.put("retry", Map.of(
            "samplingService", "maxAttempts=3, waitDuration=300ms",
            "evaluationService", "maxAttempts=4, waitDuration=1s, exponentialBackoff=true",
            "jiraService", "maxAttempts=5, waitDuration=2s"
        ));
        
        info.put("timeouts", Map.of(
            "samplingService", "2s",
            "evaluationService", "5s",
            "jiraService", "10s"
        ));
        
        info.put("bulkhead", Map.of(
            "samplingService", "maxConcurrentCalls=20",
            "evaluationService", "maxConcurrentCalls=15",
            "jiraService", "maxConcurrentCalls=5"
        ));
        
        info.put("actuatorEndpoints", Map.of(
            "circuitBreakers", "/actuator/circuitbreakers",
            "circuitBreakerEvents", "/actuator/circuitbreakerevents",
            "retries", "/actuator/retries",
            "retryEvents", "/actuator/retryevents"
        ));
        
        return ResponseEntity.ok(info);
    }
}
