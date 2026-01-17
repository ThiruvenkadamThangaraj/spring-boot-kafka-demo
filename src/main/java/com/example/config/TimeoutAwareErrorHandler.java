package com.example.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.DefaultResponseErrorHandler;

import java.io.IOException;

/**
 * Custom error handler for RestTemplate
 * 
 * Logs timeout and connection errors for troubleshooting
 * Part of Phase 1 fix for thread pool saturation
 */
@Slf4j
public class TimeoutAwareErrorHandler extends DefaultResponseErrorHandler {
    
    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        HttpStatus statusCode = (HttpStatus) response.getStatusCode();
        String statusText = response.getStatusText();
        
        // Log error details before throwing exception
        log.error("Downstream service error: status={}, statusText={}", 
                  statusCode, statusText);
        
        // Log additional context for specific errors
        if (statusCode.is5xxServerError()) {
            log.error("Downstream service returned 5xx error - service may be unhealthy");
        } else if (statusCode == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("Downstream service rate limiting (429) - may need to reduce call frequency");
        }
        
        // Delegate to default handler to throw appropriate exception
        super.handleError(response);
    }
    
    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        boolean hasError = super.hasError(response);
        
        if (hasError) {
            log.debug("Downstream service returned error status: {}", response.getStatusCode());
        }
        
        return hasError;
    }
}
