package com.example.demo.resilience;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/resilience")
public class ResilienceController {

    @Autowired
    private ResilienceService resilienceService;

    /**
     * Test Retry with Exponential Backoff
     * GET http://localhost:8080/api/resilience/retry?orderId=ORD123
     */
    @GetMapping("/retry")
    public ResponseEntity<Map<String, String>> testRetry(@RequestParam String orderId) {
        Map<String, String> response = new HashMap<>();
        
        try {
            String result = resilienceService.processOrderWithRetry(orderId);
            response.put("status", "success");
            response.put("message", result);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test Bulkhead (Thread Pool Isolation)
     * GET http://localhost:8080/api/resilience/bulkhead?paymentId=PAY123
     */
    @GetMapping("/bulkhead")
    public ResponseEntity<Map<String, String>> testBulkhead(@RequestParam String paymentId) {
        Map<String, String> response = new HashMap<>();
        
        try {
            String result = resilienceService.processPaymentWithBulkhead(paymentId);
            response.put("status", "success");
            response.put("message", result);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test Combined Patterns (Retry + Bulkhead + Circuit Breaker)
     * GET http://localhost:8080/api/resilience/combined?paymentId=PAY456&amount=99.99
     */
    @GetMapping("/combined")
    public ResponseEntity<Map<String, String>> testCombined(
            @RequestParam String paymentId,
            @RequestParam double amount) {
        Map<String, String> response = new HashMap<>();
        
        try {
            String result = resilienceService.processPaymentWithFullResilience(paymentId, amount);
            response.put("status", "success");
            response.put("message", result);
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * Test Manual Exponential Backoff
     * GET http://localhost:8080/api/resilience/manual-retry?operation=DataSync
     */
    @GetMapping("/manual-retry")
    public ResponseEntity<Map<String, String>> testManualRetry(@RequestParam String operation) {
        Map<String, String> response = new HashMap<>();
        
        String result = resilienceService.manualRetryWithExponentialBackoff(operation, 5);
        response.put("status", "completed");
        response.put("message", result);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Stress Test Bulkhead - Send multiple requests to test thread pool limits
     * GET http://localhost:8080/api/resilience/stress-test?count=20
     */
    @GetMapping("/stress-test")
    public ResponseEntity<Map<String, Object>> stressTestBulkhead(@RequestParam(defaultValue = "20") int count) {
        Map<String, Object> response = new HashMap<>();
        
        long startTime = System.currentTimeMillis();
        
        // Send multiple concurrent requests
        CompletableFuture<?>[] futures = IntStream.range(1, count + 1)
                .mapToObj(i -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return resilienceService.processPaymentWithBulkhead("PAY-" + i);
                    } catch (Exception e) {
                        return "REJECTED: " + e.getMessage();
                    }
                }))
                .toArray(CompletableFuture[]::new);
        
        // Wait for all to complete
        CompletableFuture.allOf(futures).join();
        
        long endTime = System.currentTimeMillis();
        
        response.put("totalRequests", count);
        response.put("durationMs", endTime - startTime);
        response.put("message", "Stress test completed. Check logs for thread pool behavior.");
        
        return ResponseEntity.ok(response);
    }
}
