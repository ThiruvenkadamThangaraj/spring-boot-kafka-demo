package com.example.common.audit;

import com.example.audit.dto.AuditEventDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit Interceptor - Captures all HTTP requests and responses
 * Automatically publishes audit events to Kafka
 * 
 * Usage: Add this interceptor to WebMvcConfigurer in each microservice
 */
@Component
public class AuditInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(AuditInterceptor.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String AUDIT_START_TIME = "audit.startTime";

    @Autowired(required = false)
    private KafkaTemplate<String, AuditEventDto> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${spring.application.name:unknown-service}")
    private String serviceName;

    /**
     * Before request processing - record start time and correlation ID
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute(AUDIT_START_TIME, startTime);

        // Generate or extract correlation ID for distributed tracing
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }
        request.setAttribute(CORRELATION_ID_HEADER, correlationId);

        // Add correlation ID to response headers
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        logger.debug("📥 Incoming request: {} {} - Correlation ID: {}",
                request.getMethod(), request.getRequestURI(), correlationId);

        return true;
    }

    /**
     * After request completion - capture audit data and publish to Kafka
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        try {
            // Calculate execution time
            Long startTime = (Long) request.getAttribute(AUDIT_START_TIME);
            long executionTime = startTime != null ? System.currentTimeMillis() - startTime : 0;

            // Extract correlation ID
            String correlationId = (String) request.getAttribute(CORRELATION_ID_HEADER);

            // Build audit event
            AuditEventDto auditEvent = buildAuditEvent(request, response, executionTime, correlationId, ex);

            // Publish to Kafka asynchronously
            if (kafkaTemplate != null) {
                kafkaTemplate.send("audit-events", correlationId, auditEvent);
                logger.debug("📤 Audit event published: {} {} - Status: {} - Time: {}ms",
                        auditEvent.getHttpMethod(), auditEvent.getEndpoint(),
                        auditEvent.getHttpStatusCode(), executionTime);
            } else {
                logger.warn("⚠️ KafkaTemplate not available - audit event not published");
            }

        } catch (Exception exception) {
            logger.error("❌ Failed to publish audit event: {}", exception.getMessage(), exception);
        }
    }

    /**
     * Build audit event from request and response
     */
    private AuditEventDto buildAuditEvent(HttpServletRequest request, HttpServletResponse response,
                                          long executionTime, String correlationId, Exception ex) {

        // Extract request body
        String requestBody = null;
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                requestBody = new String(buf, 0, Math.min(buf.length, 10000), StandardCharsets.UTF_8);
            }
        }

        // Extract response body
        String responseBody = null;
        if (response instanceof ContentCachingResponseWrapper) {
            ContentCachingResponseWrapper wrapper = (ContentCachingResponseWrapper) response;
            byte[] buf = wrapper.getContentAsByteArray();
            if (buf.length > 0) {
                responseBody = new String(buf, 0, Math.min(buf.length, 10000), StandardCharsets.UTF_8);
                try {
                    wrapper.copyBodyToResponse(); // Important: copy content back to response
                } catch (Exception e) {
                    logger.warn("Failed to copy response body: {}", e.getMessage());
                }
            }
        }

        // Extract request headers (filter sensitive headers)
        String requestHeaders = extractHeaders(request);

        // Extract username from JWT or session
        String username = extractUsername(request);

        // Build audit event
        return AuditEventDto.builder()
                .serviceName(serviceName)
                .httpMethod(request.getMethod())
                .endpoint(request.getRequestURI())
                .fullUrl(buildFullUrl(request))
                .requestBody(requestBody)
                .responseBody(responseBody)
                .httpStatusCode(response.getStatus())
                .executionTimeMs(executionTime)
                .username(username)
                .userRoles(extractUserRoles(request))
                .clientIp(extractClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .correlationId(correlationId)
                .errorMessage(ex != null ? ex.getMessage() : null)
                .stackTrace(ex != null ? getStackTraceAsString(ex) : null)
                .timestamp(LocalDateTime.now())
                .success(response.getStatus() >= 200 && response.getStatus() < 400 && ex == null)
                .requestHeaders(requestHeaders)
                .sessionId(request.getSession(false) != null ? request.getSession(false).getId() : null)
                .build();
    }

    /**
     * Extract headers from request (filter sensitive headers like Authorization)
     */
    private String extractHeaders(HttpServletRequest request) {
        try {
            Map<String, String> headers = new HashMap<>();
            Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();

                // Skip sensitive headers
                if (headerName.equalsIgnoreCase("Authorization") ||
                    headerName.equalsIgnoreCase("Cookie") ||
                    headerName.equalsIgnoreCase("X-API-Key")) {
                    headers.put(headerName, "[REDACTED]");
                } else {
                    headers.put(headerName, request.getHeader(headerName));
                }
            }

            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            return "{}";
        }
    }

    /**
     * Extract username from JWT token or session
     */
    private String extractUsername(HttpServletRequest request) {
        // Try to get from request attribute (set by JWT filter)
        Object usernameAttr = request.getAttribute("username");
        if (usernameAttr != null) {
            return usernameAttr.toString();
        }

        // Try to get from principal
        if (request.getUserPrincipal() != null) {
            return request.getUserPrincipal().getName();
        }

        return "anonymous";
    }

    /**
     * Extract user roles from request
     */
    private String extractUserRoles(HttpServletRequest request) {
        Object rolesAttr = request.getAttribute("userRoles");
        if (rolesAttr != null) {
            return rolesAttr.toString();
        }
        return null;
    }

    /**
     * Extract client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * Build full URL with query parameters
     */
    private String buildFullUrl(HttpServletRequest request) {
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            return request.getRequestURI() + "?" + queryString;
        }
        return request.getRequestURI();
    }

    /**
     * Convert exception stack trace to string
     */
    private String getStackTraceAsString(Exception ex) {
        if (ex == null) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(ex.getClass().getName()).append(": ").append(ex.getMessage()).append("\n");

        StackTraceElement[] elements = ex.getStackTrace();
        int maxElements = Math.min(elements.length, 10); // Limit to first 10 stack frames

        for (int i = 0; i < maxElements; i++) {
            sb.append("\tat ").append(elements[i].toString()).append("\n");
        }

        if (elements.length > maxElements) {
            sb.append("\t... ").append(elements.length - maxElements).append(" more");
        }

        return sb.toString();
    }
}
