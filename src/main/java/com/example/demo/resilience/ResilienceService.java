package com.example.demo.resilience;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.stereotype.Service;

import java.util.Random;

/**
 * Demonstrates Resilience4j patterns:
 * 1. Retry with Exponential Backoff
 * 2. Bulkhead (Thread Pool Isolation)
 * 3. Circuit Breaker
 */
@Service
public class ResilienceService {

    private final Random random = new Random();
    private int callCount = 0;

    /**
     * RETRY WITH EXPONENTIAL BACKOFF
     * Configuration in application.yaml:
     * - maxAttempts: 5 (tries up to 5 times)
     * - waitDuration: 500ms (initial wait)
     * - exponentialBackoffMultiplier: 2 (doubles each retry)
     * - retryExceptions: Exception.class (retry on any exception)
     * 
     * Retry sequence: 500ms -> 1000ms -> 2000ms -> 4000ms -> 8000ms
     */
    @Retry(name = "orderServiceRetry", fallbackMethod = "retryFallback")
    public String processOrderWithRetry(String orderId) {
        callCount++;
        System.out.println("Attempt #" + callCount + " for order: " + orderId);
        
        // Simulate 60% failure rate
        if (random.nextDouble() < 0.6) {
            throw new RuntimeException("Temporary service unavailable");
        }
        
        callCount = 0; // Reset on success
        return "Order " + orderId + " processed successfully!";
    }

    private String retryFallback(String orderId, Exception ex) {
        callCount = 0; // Reset counter
        return "Order " + orderId + " failed after retries. Error: " + ex.getMessage();
    }

    /**
     * BULKHEAD (THREAD POOL ISOLATION)
     * Configuration in application.yaml:
     * - maxThreadPoolSize: 5 (max 5 concurrent threads)
     * - coreThreadPoolSize: 3 (3 threads always running)
     * - queueCapacity: 10 (10 tasks can wait in queue)
     * 
     * Prevents one service from consuming all threads and starving others
     */
    @Bulkhead(name = "paymentServiceBulkhead", fallbackMethod = "bulkheadFallback", type = Bulkhead.Type.THREADPOOL)
    public String processPaymentWithBulkhead(String paymentId) {
        System.out.println("Processing payment: " + paymentId + " on thread: " + Thread.currentThread().getName());
        
        try {
            // Simulate payment processing (2 seconds)
            Thread.sleep(2000);
            
            // Simulate 20% failure rate
            if (random.nextDouble() < 0.2) {
                throw new RuntimeException("Payment gateway timeout");
            }
            
            return "Payment " + paymentId + " processed successfully!";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Payment processing interrupted", e);
        }
    }

    private String bulkheadFallback(String paymentId, Exception ex) {
        return "Payment " + paymentId + " rejected - System overloaded. Please try again later.";
    }

    /**
     * COMBINED: RETRY + BULKHEAD + CIRCUIT BREAKER
     * Demonstrates all three patterns working together for maximum resilience
     */
    @Retry(name = "orderServiceRetry")
    @Bulkhead(name = "paymentServiceBulkhead", type = Bulkhead.Type.THREADPOOL)
    @CircuitBreaker(name = "paymentServiceCB", fallbackMethod = "combinedFallback")
    public String processPaymentWithFullResilience(String paymentId, double amount) {
        System.out.println("Processing $" + amount + " payment: " + paymentId);
        
        try {
            Thread.sleep(1000);
            
            // Simulate failures
            if (random.nextDouble() < 0.3) {
                throw new RuntimeException("Payment service error");
            }
            
            return "Payment " + paymentId + " of $" + amount + " completed!";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    private String combinedFallback(String paymentId, double amount, Exception ex) {
        return "Payment " + paymentId + " of $" + amount + " failed. Will be retried later. Error: " + ex.getMessage();
    }

    /**
     * EXPONENTIAL BACKOFF - Manual Implementation (without Resilience4j)
     * Useful when you need custom retry logic
     */
    public String manualRetryWithExponentialBackoff(String operation, int maxRetries) {
        int attempt = 0;
        long waitTime = 500; // Start with 500ms
        
        while (attempt < maxRetries) {
            try {
                attempt++;
                System.out.println("Manual retry attempt #" + attempt);
                
                // Simulate operation
                if (random.nextDouble() < 0.7) {
                    throw new RuntimeException("Operation failed");
                }
                
                return "Operation " + operation + " succeeded on attempt " + attempt;
                
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    return "Operation " + operation + " failed after " + maxRetries + " attempts";
                }
                
                try {
                    System.out.println("Waiting " + waitTime + "ms before retry...");
                    Thread.sleep(waitTime);
                    waitTime *= 2; // Double the wait time (exponential backoff)
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return "Operation interrupted";
                }
            }
        }
        
        return "Max retries reached";
    }
}
