
package com.example.audit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * Audit Log Entity - Stores all API request/response audit information
 */
@Document(collection = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    private String id;

    /**
     * Service name that generated the audit log
     * Example: evaluation-service, sampling-service
     */

    @Indexed
    private String serviceName;

    /**
     * HTTP method (GET, POST, PUT, DELETE, PATCH)
     */

    private String httpMethod;

    /**
     * API endpoint path
     * Example: /api/users/123, /api/samples/create
     */

    @Indexed
    private String endpoint;

    /**
     * Full request URL with query parameters
     */

    private String fullUrl;

    /**
     * Request body (JSON format)
     */

    private String requestBody;

    /**
     * Response body (JSON format)
     */

    private String responseBody;

    /**
     * HTTP status code (200, 201, 400, 401, 500, etc.)
     */

    private Integer httpStatusCode;

    /**
     * Request execution time in milliseconds
     */

    private Long executionTimeMs;

    /**
     * Username of the user who made the request
     */

    @Indexed
    private String username;

    /**
     * User roles (comma-separated)
     * Example: ROLE_USER,ROLE_ADMIN
     */

    private String userRoles;

    /**
     * Client IP address
     */

    private String clientIp;

    /**
     * User agent string (browser, mobile app, etc.)
     */

    private String userAgent;

    /**
     * Correlation ID for distributed tracing
     * Same ID used across all microservices for a single request
     */

    @Indexed
    private String correlationId;

    /**
     * Exception message if request failed
     */

    private String errorMessage;

    /**
     * Stack trace if exception occurred
     */

    private String stackTrace;

    /**
     * Timestamp when the request was made
     */

    @Indexed
    private LocalDateTime timestamp;

    /**
     * Additional metadata (JSON format)
     * Can store custom fields like: environment, version, region, etc.
     */

    private String metadata;

    /**
     * Whether the request was successful or not
     */

    private Boolean success;

    /**
     * Request headers (JSON format) - optional for sensitive data filtering
     */

    private String requestHeaders;

    /**
     * Response headers (JSON format) - optional
     */

    private String responseHeaders;

    /**
     * Session ID if available
     */

    private String sessionId;


    // MongoDB does not support @PrePersist, so use builder/defaults or set in service
}
