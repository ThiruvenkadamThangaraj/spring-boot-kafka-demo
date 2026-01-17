package com.example.exception;

/**
 * Exception thrown when downstream service is unavailable
 * 
 * Thrown when:
 * - Concurrency limit reached (all 10 slots busy)
 * - Timeout occurs
 * - Connection error
 * 
 * Part of Phase 1 fix for thread pool saturation
 */
public class ServiceUnavailableException extends RuntimeException {
    
    public ServiceUnavailableException(String message) {
        super(message);
    }
    
    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
