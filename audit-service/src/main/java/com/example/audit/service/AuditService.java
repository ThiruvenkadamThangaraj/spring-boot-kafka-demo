package com.example.audit.service;

import com.example.audit.dto.AuditEventDto;
import com.example.audit.entity.AuditLog;
import com.example.audit.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Audit Service - Handles audit log processing and storage
 */
@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);

    @Autowired
    private AuditLogRepository auditLogRepository;

    /**
     * Kafka Consumer - Listens to audit-events topic
     * Processes audit events asynchronously from all microservices
     */
    @KafkaListener(topics = "audit-events", groupId = "audit-service-group")
    @Transactional
    public void consumeAuditEvent(AuditEventDto auditEvent) {
        try {
            logger.info("📝 Received audit event: {} {} from {}",
                    auditEvent.getHttpMethod(),
                    auditEvent.getEndpoint(),
                    auditEvent.getServiceName());

            // Convert DTO to Entity
            AuditLog auditLog = convertToEntity(auditEvent);

            // Save to database
            auditLogRepository.save(auditLog);

            logger.debug("✅ Audit log saved successfully. ID: {}, Correlation ID: {}",
                    auditLog.getId(), auditLog.getCorrelationId());

        } catch (Exception ex) {
            logger.error("❌ Failed to process audit event: {}", ex.getMessage(), ex);
            // Note: In production, you might want to send this to a dead-letter queue
        }
    }

    /**
     * Save audit log directly (synchronous - for critical operations)
     */
    @Transactional
    public AuditLog saveAuditLog(AuditEventDto auditEvent) {
        AuditLog auditLog = convertToEntity(auditEvent);
        return auditLogRepository.save(auditLog);
    }

    /**
     * Get all audit logs with pagination
     */
    public Page<AuditLog> getAllAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Get audit logs by service name
     */
    public Page<AuditLog> getAuditLogsByService(String serviceName, Pageable pageable) {
        return auditLogRepository.findByServiceName(serviceName, pageable);
    }

    /**
     * Get audit logs by username
     */
    public Page<AuditLog> getAuditLogsByUsername(String username, Pageable pageable) {
        return auditLogRepository.findByUsername(username, pageable);
    }

    /**
     * Get audit logs by correlation ID (distributed tracing)
     */
    public List<AuditLog> getAuditLogsByCorrelationId(String correlationId) {
        return auditLogRepository.findByCorrelationIdOrderByTimestampAsc(correlationId);
    }

    /**
     * Search audit logs with filters
     */

    // MongoDB does not support complex JPQL queries directly. Use simple filters or MongoTemplate for advanced queries.


    public Page<AuditLog> searchAuditLogs(
            String serviceName,
            String username,
            String httpMethod,
            Boolean success,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        List<AuditLog> filtered = auditLogRepository.findAll().stream()
                .filter(auditLog -> serviceName == null || serviceName.equals(auditLog.getServiceName()))
                .filter(auditLog -> username == null || username.equals(auditLog.getUsername()))
                .filter(auditLog -> httpMethod == null || httpMethod.equals(auditLog.getHttpMethod()))
                .filter(auditLog -> success == null || success.equals(auditLog.getSuccess()))
                .filter(auditLog -> startDate == null || !auditLog.getTimestamp().isBefore(startDate))
                .filter(auditLog -> endDate == null || !auditLog.getTimestamp().isAfter(endDate))
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<AuditLog> pageContent = (start <= end) ? filtered.subList(start, end) : List.of();
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
    }

    public Page<AuditLog> getFailedRequests(Pageable pageable) {
        return auditLogRepository.findBySuccess(false, pageable);
    }


    public Page<AuditLog> getSlowRequests(Long thresholdMs, Pageable pageable) {
        List<AuditLog> filtered = auditLogRepository.findAll().stream()
                .filter(auditLog -> auditLog.getExecutionTimeMs() != null && auditLog.getExecutionTimeMs() > thresholdMs)
                .toList();
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filtered.size());
        List<AuditLog> pageContent = (start <= end) ? filtered.subList(start, end) : List.of();
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, filtered.size());
    }

    public List<Object[]> getStatisticsByService() {
        // MongoRepository does not support aggregation out of the box; use MongoTemplate for real aggregation
        // Here, we provide a simple in-memory aggregation for demonstration
        List<AuditLog> all = auditLogRepository.findAll();
        return all.stream().collect(java.util.stream.Collectors.groupingBy(
                AuditLog::getServiceName,
                java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        logs -> new Object[]{
                                logs.get(0).getServiceName(),
                                logs.size(),
                                logs.stream().mapToLong(l -> l.getExecutionTimeMs() != null ? l.getExecutionTimeMs() : 0).average().orElse(0),
                                logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count()
                        }
                )
        )).values().stream().toList();
    }

    /**
     * Convert DTO to Entity
     */
    private AuditLog convertToEntity(AuditEventDto dto) {
        return AuditLog.builder()
                .serviceName(dto.getServiceName())
                .httpMethod(dto.getHttpMethod())
                .endpoint(dto.getEndpoint())
                .fullUrl(dto.getFullUrl())
                .requestBody(dto.getRequestBody())
                .responseBody(dto.getResponseBody())
                .httpStatusCode(dto.getHttpStatusCode())
                .executionTimeMs(dto.getExecutionTimeMs())
                .username(dto.getUsername())
                .userRoles(dto.getUserRoles())
                .clientIp(dto.getClientIp())
                .userAgent(dto.getUserAgent())
                .correlationId(dto.getCorrelationId())
                .errorMessage(dto.getErrorMessage())
                .stackTrace(dto.getStackTrace())
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                .metadata(dto.getMetadata())
                .success(dto.getSuccess())
                .requestHeaders(dto.getRequestHeaders())
                .responseHeaders(dto.getResponseHeaders())
                .sessionId(dto.getSessionId())
                .build();
    }
}
