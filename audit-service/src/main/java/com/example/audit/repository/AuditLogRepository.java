
package com.example.audit.repository;

import com.example.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for AuditLog entity (MongoDB)
 */
@Repository
public interface AuditLogRepository extends MongoRepository<AuditLog, String> {

    Page<AuditLog> findByServiceName(String serviceName, Pageable pageable);
    Page<AuditLog> findByUsername(String username, Pageable pageable);
    List<AuditLog> findByCorrelationIdOrderByTimestampAsc(String correlationId);
    Page<AuditLog> findByEndpointContaining(String endpoint, Pageable pageable);
    Page<AuditLog> findByHttpMethod(String httpMethod, Pageable pageable);
    Page<AuditLog> findBySuccess(Boolean success, Pageable pageable);
    Page<AuditLog> findByTimestampBetween(LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    Long countByServiceName(String serviceName);

    // For complex search/statistics, use custom implementation or MongoTemplate if needed
}
