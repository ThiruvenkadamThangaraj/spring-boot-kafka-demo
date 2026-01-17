package com.example.service;

import com.example.exception.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Example Service with Phase 1 Protection
 * 
 * Demonstrates how to use:
 * - RestTemplate with timeouts (from HttpClientConfig)
 * - Semaphore for concurrency limiting
 * - Proper error handling
 * 
 * BEFORE Phase 1:
 * - No timeout → threads hang 30+ seconds
 * - No concurrency limit → all 200 threads can call simultaneously
 * - Cascading failure when downstream slow
 * 
 * AFTER Phase 1:
 * - Timeout after 5 seconds → fast failure
 * - Max 10 concurrent calls → prevents overwhelming downstream
 * - System stays responsive even when downstream fails
 */
@Service
@Slf4j
public class ExampleDownstreamService {
    
    @Autowired
    private RestTemplate restTemplate;  // Configured with timeouts
    
    @Autowired
    private Semaphore downstreamConcurrencyLimit;  // Max 10 concurrent
    
    /**
     * Call external API with Phase 1 protections
     * 
     * Flow:
     * 1. Try to acquire semaphore permit (wait max 1 second)
     * 2. If acquired, make HTTP call (timeout after 5 seconds)
     * 3. Release permit in finally block
     * 4. If timeout/error, throw ServiceUnavailableException
     * 
     * @param requestData Data to send to downstream
     * @return Response from downstream service
     * @throws ServiceUnavailableException if concurrency limit reached or timeout
     */
    public String callExternalApi(String requestData) {
        boolean acquired = false;
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Try to acquire permit (max wait 1 second)
            log.debug("Attempting to acquire downstream call permit...");
            acquired = downstreamConcurrencyLimit.tryAcquire(1, TimeUnit.SECONDS);
            
            if (!acquired) {
                // Concurrency limit reached - all 10 slots busy
                log.warn("Concurrency limit reached (10/10 slots busy), rejecting request");
                throw new ServiceUnavailableException(
                    "Downstream service overloaded, please retry later"
                );
            }
            
            int available = downstreamConcurrencyLimit.availablePermits();
            log.info("Acquired downstream call permit (available: {}/10)", available);
            
            // Step 2: Make the actual HTTP call
            // RestTemplate will timeout after 5 seconds (configured in HttpClientConfig)
            String url = "https://external-api.example.com/endpoint";
            log.debug("Calling downstream service: {}", url);
            
            String response = restTemplate.postForObject(url, requestData, String.class);
            
            long duration = System.currentTimeMillis() - startTime;
            log.info("Downstream call SUCCESS in {}ms", duration);
            
            return response;
            
        } catch (InterruptedException e) {
            // Thread interrupted while waiting for permit
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for downstream permit");
            throw new RuntimeException("Request interrupted", e);
            
        } catch (ResourceAccessException e) {
            // Timeout or connection error from RestTemplate
            long duration = System.currentTimeMillis() - startTime;
            log.error("Downstream call TIMEOUT after {}ms: {}", duration, e.getMessage());
            
            // This is expected with Phase 1 - fail fast instead of hanging
            throw new ServiceUnavailableException("Downstream service timeout", e);
            
        } catch (Exception e) {
            // Other errors (4xx, 5xx, etc.)
            long duration = System.currentTimeMillis() - startTime;
            log.error("Downstream call FAILED after {}ms: {}", duration, e.getMessage());
            throw new ServiceUnavailableException("Downstream service error", e);
            
        } finally {
            // Step 3: Always release permit
            if (acquired) {
                downstreamConcurrencyLimit.release();
                int available = downstreamConcurrencyLimit.availablePermits();
                log.debug("Released downstream call permit (available: {}/10)", available);
            }
        }
    }
    
    /**
     * Example: Batch processing with concurrency control
     * 
     * Processes multiple items but respects concurrency limit
     * Good pattern for ETL jobs
     */
    public void processBatchWithRateLimit(java.util.List<String> items) {
        log.info("Processing batch of {} items with concurrency limit", items.size());
        
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            
            try {
                String result = callExternalApi(item);
                log.info("Item {}/{} processed successfully", i + 1, items.size());
                
            } catch (ServiceUnavailableException e) {
                // Log and continue with next item
                log.warn("Item {}/{} failed: {}", i + 1, items.size(), e.getMessage());
                // Could implement retry logic here
            }
        }
        
        log.info("Batch processing complete");
    }
}
