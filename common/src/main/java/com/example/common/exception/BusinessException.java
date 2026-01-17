package com.example.common.exception;

/**
 * Business Exception for business logic errors that should not be retried
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
public class BusinessException extends RuntimeException {
    
    public BusinessException(String message) {
        super(message);
    }
    
    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
