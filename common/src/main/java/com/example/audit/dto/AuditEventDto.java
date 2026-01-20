package com.example.audit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEventDto {
    
    private String serviceName;
    private String httpMethod;
    private String endpoint;
    private String fullUrl;
    private String requestBody;
    private String responseBody;
    private Integer httpStatusCode;
    private Long executionTimeMs;
    private String username;
    private String userRoles;
    private String clientIp;
    private String correlationId;
    private LocalDateTime timestamp;
    private String requestHeaders;
    private String responseHeaders;
    private String queryParams;
    private String pathVariables;
    private String userAgent;
    private String sessionId;
    private String referrer;
    private String errorMessage;
    private String stackTrace;
    private String metadata;
    private Boolean success;
}
