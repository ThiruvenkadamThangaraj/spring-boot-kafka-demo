package com.example.common.ai.agents;

import com.example.common.ai.AgentDecision;
import com.example.common.ai.BaseAgent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-Healing Agent - Monitors system health and autonomously triggers remediation
 * Detects failures, analyzes patterns, and executes recovery actions
 */
@Component
public class SelfHealingAgent extends BaseAgent {
    
    private final Map<String, ServiceHealthMetrics> serviceHealthMap = new ConcurrentHashMap<>();
    private final Map<String, Integer> failureCounters = new ConcurrentHashMap<>();
    private final Map<String, List<LocalDateTime>> remediationHistory = new ConcurrentHashMap<>();
    
    // Thresholds
    private static final int FAILURE_THRESHOLD = 3;
    
    public SelfHealingAgent() {
        super("SelfHealingAgent");
    }
    
    @Override
    protected AgentDecision executeDecisionLogic(Object input) {
        if (!(input instanceof ServiceHealthCheck)) {
            return createErrorDecision("Invalid input type, expected ServiceHealthCheck");
        }
        
        ServiceHealthCheck healthCheck = (ServiceHealthCheck) input;
        
        // Analyze service health
        HealthStatus status = analyzeServiceHealth(healthCheck);
        
        // Determine remediation action
        String action;
        double confidence;
        String reasoning;
        
        if (status.getSeverity().equals("CRITICAL")) {
            action = "AUTO_RESTART";
            confidence = 0.95;
            reasoning = String.format("Critical issue detected in %s: %s. Auto-remediation initiated.", 
                    status.getServiceName(), status.getIssue());
            
            triggerAutoRemediation(status.getServiceName(), status.getIssue());
            
            logger.error("🚨 CRITICAL: Self-healing agent triggering auto-remediation for {}", 
                    status.getServiceName());
        } else if (status.getHealthScore() < 0.5) {
            action = "TRIGGER_REMEDIATION";
            confidence = 0.8;
            reasoning = String.format("Service health degraded: %s", status.getIssue());
        } else {
            action = "MONITOR";
            confidence = 1.0 - status.getHealthScore();
            reasoning = "System operating normally, continue monitoring";
        }
        
        updateServiceMetrics(status.getServiceName(), status.isHealthy());
        
        AgentDecision decision = new AgentDecision(name, action, confidence, reasoning);
        decision.addParameter("serviceName", status.getServiceName());
        decision.addParameter("healthScore", status.getHealthScore());
        decision.addParameter("issues", status.getIssue());
        decision.setRequiresHumanApproval(action.equals("AUTO_RESTART"));
        
        return decision;
    }
    
    private HealthStatus analyzeServiceHealth(ServiceHealthCheck healthCheck) {
        HealthStatus status = new HealthStatus(healthCheck.getServiceName());
        
        // Analyze error rate
        if (healthCheck.getErrorRate() > 0.1) {
            status.setSeverity("CRITICAL");
            status.setHealthy(false);
            status.setIssue(String.format("High error rate: %.2f%%", healthCheck.getErrorRate() * 100));
            status.setHealthScore(0.2);
        } else if (healthCheck.getErrorRate() > 0.05) {
            status.setSeverity("WARNING");
            status.setIssue("Elevated error rate");
            status.setHealthScore(0.5);
        }
        
        // Analyze response time
        if (healthCheck.getAvgResponseTime() > 5000) {
            status.setSeverity("WARNING");
            status.setIssue("High response time: " + healthCheck.getAvgResponseTime() + "ms");
            status.setHealthScore(Math.min(status.getHealthScore(), 0.6));
        }
        
        // Check if service is down
        if (!healthCheck.isServiceUp()) {
            status.setSeverity("CRITICAL");
            status.setHealthy(false);
            status.setIssue("Service is down");
            status.setHealthScore(0.0);
        }
        
        // Check consecutive failures
        int failures = failureCounters.getOrDefault(healthCheck.getServiceName(), 0);
        if (failures >= FAILURE_THRESHOLD) {
            status.setSeverity("CRITICAL");
            status.setHealthy(false);
            status.setIssue("Multiple consecutive failures detected");
            status.setHealthScore(0.1);
        }
        
        // Update failure counter
        if (!status.isHealthy()) {
            failureCounters.merge(healthCheck.getServiceName(), 1, Integer::sum);
        } else {
            failureCounters.put(healthCheck.getServiceName(), 0);
        }
        
        return status;
    }
    
    private void updateServiceMetrics(String serviceName, boolean healthy) {
        ServiceHealthMetrics metrics = serviceHealthMap.computeIfAbsent(
                serviceName, k -> new ServiceHealthMetrics());
        
        if (healthy) {
            metrics.recordSuccess();
        } else {
            metrics.recordFailure();
        }
        
        metrics.setLastCheck(LocalDateTime.now());
    }
    
    private void triggerAutoRemediation(String service, String issue) {
        logger.warn("🔧 Auto-remediation triggered for {} - Issue: {}", service, issue);
        
        // Record remediation attempt
        remediationHistory.computeIfAbsent(service, k -> new ArrayList<>())
                .add(LocalDateTime.now());
        
        // In a real implementation, this would:
        // 1. Send restart command to service orchestrator
        // 2. Clear service caches
        // 3. Reset circuit breakers
        // 4. Trigger health checks
        // 5. Notify operations team
        
        logger.info("🔧 Remediation actions initiated for service: {}", service);
    }
    
    /**
     * Get health statistics for all monitored services
     */
    public Map<String, Object> getHealthStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("monitoredServices", serviceHealthMap.size());
        stats.put("decisionsProcessed", decisionsProcessed.get());
        stats.put("remediationActions", successfulDecisions.get());
        
        Map<String, Object> serviceStats = new HashMap<>();
        serviceHealthMap.forEach((service, metrics) -> {
            Map<String, Object> serviceData = new HashMap<>();
            serviceData.put("successRate", metrics.getSuccessRate());
            serviceData.put("totalChecks", metrics.getTotalChecks());
            serviceData.put("lastCheck", metrics.getLastCheck());
            serviceStats.put(service, serviceData);
        });
        stats.put("serviceMetrics", serviceStats);
        
        return stats;
    }
    
    /**
     * Service health check input
     */
    public static class ServiceHealthCheck {
        private String serviceName;
        private boolean serviceUp;
        private double errorRate;
        private double avgResponseTime;
        private Map<String, Object> additionalMetrics;
        
        public ServiceHealthCheck() {
            this.additionalMetrics = new HashMap<>();
        }
        
        public ServiceHealthCheck(String serviceName, boolean serviceUp, double errorRate, double avgResponseTime) {
            this();
            this.serviceName = serviceName;
            this.serviceUp = serviceUp;
            this.errorRate = errorRate;
            this.avgResponseTime = avgResponseTime;
        }
        
        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public boolean isServiceUp() { return serviceUp; }
        public void setServiceUp(boolean serviceUp) { this.serviceUp = serviceUp; }
        
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
        
        public double getAvgResponseTime() { return avgResponseTime; }
        public void setAvgResponseTime(double avgResponseTime) { this.avgResponseTime = avgResponseTime; }
        
        public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Object> additionalMetrics) { this.additionalMetrics = additionalMetrics; }
    }
    
    /**
     * Health status result
     */
    private static class HealthStatus {
        private String serviceName;
        private boolean healthy = true;
        private double healthScore = 1.0;
        private String severity = "NORMAL";
        private String issue = "";
        
        public HealthStatus(String serviceName) {
            this.serviceName = serviceName;
        }
        
        public String getServiceName() { return serviceName; }
        public boolean isHealthy() { return healthy; }
        public void setHealthy(boolean healthy) { this.healthy = healthy; }
        public double getHealthScore() { return healthScore; }
        public void setHealthScore(double healthScore) { this.healthScore = healthScore; }
        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }
        public String getIssue() { return issue; }
        public void setIssue(String issue) { this.issue = issue; }
    }
    
    /**
     * Service health metrics
     */
    private static class ServiceHealthMetrics {
        private int successCount = 0;
        private int failureCount = 0;
        private LocalDateTime lastCheck = LocalDateTime.now();
        
        public void recordSuccess() {
            successCount++;
        }
        
        public void recordFailure() {
            failureCount++;
        }
        
        public void setLastCheck(LocalDateTime lastCheck) {
            this.lastCheck = lastCheck;
        }
        
        public double getSuccessRate() {
            int total = successCount + failureCount;
            return total > 0 ? (double) successCount / total : 1.0;
        }
        
        public int getTotalChecks() {
            return successCount + failureCount;
        }
        
        public LocalDateTime getLastCheck() {
            return lastCheck;
        }
    }
}
