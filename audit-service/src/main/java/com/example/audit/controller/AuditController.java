package com.example.audit.controller;

import com.example.audit.entity.AuditLog;
import com.example.audit.service.AuditService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Audit Controller - REST API for querying audit logs
 */
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = "*")
public class AuditController {

    @Autowired
    private AuditService auditService;

    /**
     * Get all audit logs with pagination
     * 
     * Example: GET /api/audit?page=0&size=20&sort=timestamp,desc
     */
    @GetMapping
    public ResponseEntity<Page<AuditLog>> getAllAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "timestamp,desc") String sort) {

        String[] sortParams = sort.split(",");
        Sort.Direction direction = sortParams.length > 1 && sortParams[1].equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortParams[0]));
        Page<AuditLog> auditLogs = auditService.getAllAuditLogs(pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get audit logs by service name
     * 
     * Example: GET /api/audit/service/evaluation-service?page=0&size=20
     */
    @GetMapping("/service/{serviceName}")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByService(
            @PathVariable String serviceName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> auditLogs = auditService.getAuditLogsByService(serviceName, pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get audit logs by username
     * 
     * Example: GET /api/audit/user/john.doe?page=0&size=20
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<Page<AuditLog>> getAuditLogsByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> auditLogs = auditService.getAuditLogsByUsername(username, pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get audit logs by correlation ID (distributed tracing)
     * Returns all microservice calls for a single request
     * 
     * Example: GET /api/audit/trace/abc-123-def-456
     */
    @GetMapping("/trace/{correlationId}")
    public ResponseEntity<List<AuditLog>> getAuditLogsByCorrelationId(
            @PathVariable String correlationId) {

        List<AuditLog> auditLogs = auditService.getAuditLogsByCorrelationId(correlationId);
        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Search audit logs with multiple filters
     * 
     * Example: GET /api/audit/search?serviceName=evaluation-service&username=admin&success=false
     */
    @GetMapping("/search")
    public ResponseEntity<Page<AuditLog>> searchAuditLogs(
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "timestamp"));
        Page<AuditLog> auditLogs = auditService.searchAuditLogs(
                serviceName, username, httpMethod, success, startDate, endDate, pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get failed requests (errors)
     * 
     * Example: GET /api/audit/failures?page=0&size=50
     */
    @GetMapping("/failures")
    public ResponseEntity<Page<AuditLog>> getFailedRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.getFailedRequests(pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get slow requests (execution time > threshold)
     * 
     * Example: GET /api/audit/slow?threshold=1000&page=0&size=50
     */
    @GetMapping("/slow")
    public ResponseEntity<Page<AuditLog>> getSlowRequests(
            @RequestParam(defaultValue = "1000") Long threshold,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AuditLog> auditLogs = auditService.getSlowRequests(threshold, pageable);

        return ResponseEntity.ok(auditLogs);
    }

    /**
     * Get statistics by service
     * Returns: service name, total requests, avg execution time, success count
     * 
     * Example: GET /api/audit/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<List<Map<String, Object>>> getStatistics() {
        List<Object[]> stats = auditService.getStatisticsByService();

        List<Map<String, Object>> response = stats.stream()
                .map(stat -> Map.of(
                        "serviceName", stat[0],
                        "totalRequests", stat[1],
                        "avgExecutionTimeMs", stat[2],
                        "successCount", stat[3]
                ))
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "audit-service",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
