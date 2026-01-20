package com.example.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Audit Service - Centralized audit logging for all microservices
 * 
 * Features:
 * - Captures all REST API requests and responses
 * - Async Kafka-based audit event processing
 * - PostgreSQL storage for audit trail
 * - Query API for audit log retrieval
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-19
 */
@SpringBootApplication(scanBasePackages = {"com.example.audit", "com.example.common"})
@EnableKafka
@EnableAsync
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
        System.out.println("\n✅ Audit Service Started Successfully on Port 8086");
        System.out.println("📊 Audit Dashboard: http://localhost:8086/api/audit");
        System.out.println("🔍 Search Audit Logs: http://localhost:8086/api/audit/search");
    }
}
