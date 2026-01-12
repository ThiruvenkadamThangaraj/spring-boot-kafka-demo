package com.example.common.ai.controller;

import com.example.common.ai.AgentStatus;
import com.example.common.ai.agents.AnomalyDetectionAgent;
import com.example.common.ai.agents.JiraIntelligenceAgent;
import com.example.common.ai.agents.SelfHealingAgent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST API for AI Agent monitoring and management
 */
@RestController
@RequestMapping("/api/ai-agents")
public class AgentController {
    
    @Autowired(required = false)
    private AnomalyDetectionAgent anomalyDetectionAgent;
    
    @Autowired(required = false)
    private JiraIntelligenceAgent jiraIntelligenceAgent;
    
    @Autowired(required = false)
    private SelfHealingAgent selfHealingAgent;
    
    /**
     * Get status of all AI agents
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getAgentsStatus() {
        Map<String, Object> response = new HashMap<>();
        
        if (anomalyDetectionAgent != null) {
            AgentStatus status = anomalyDetectionAgent.getStatus();
            response.put("anomalyDetection", status);
            response.put("anomalyStats", anomalyDetectionAgent.getAnomalyStatistics());
        }
        
        if (jiraIntelligenceAgent != null) {
            AgentStatus status = jiraIntelligenceAgent.getStatus();
            response.put("jiraIntelligence", status);
        }
        
        if (selfHealingAgent != null) {
            AgentStatus status = selfHealingAgent.getStatus();
            response.put("selfHealing", status);
            response.put("healthStats", selfHealingAgent.getHealthStatistics());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get anomaly detection statistics
     */
    @GetMapping("/anomaly-detection/stats")
    public ResponseEntity<Map<String, Object>> getAnomalyStats() {
        if (anomalyDetectionAgent == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> stats = anomalyDetectionAgent.getAnomalyStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Reset anomaly detection statistics
     */
    @PostMapping("/anomaly-detection/reset")
    public ResponseEntity<String> resetAnomalyStats() {
        if (anomalyDetectionAgent == null) {
            return ResponseEntity.notFound().build();
        }
        
        anomalyDetectionAgent.resetStatistics();
        return ResponseEntity.ok("Anomaly detection statistics reset successfully");
    }
    
    /**
     * Get self-healing agent health statistics
     */
    @GetMapping("/self-healing/stats")
    public ResponseEntity<Map<String, Object>> getHealthStats() {
        if (selfHealingAgent == null) {
            return ResponseEntity.notFound().build();
        }
        
        Map<String, Object> stats = selfHealingAgent.getHealthStatistics();
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Trigger manual health check (for testing)
     */
    @PostMapping("/self-healing/health-check")
    public ResponseEntity<Map<String, Object>> triggerHealthCheck(@RequestBody HealthCheckRequest request) {
        if (selfHealingAgent == null) {
            return ResponseEntity.notFound().build();
        }
        
        SelfHealingAgent.ServiceHealthCheck healthCheck = new SelfHealingAgent.ServiceHealthCheck(
                request.getServiceName(),
                request.isServiceUp(),
                request.getErrorRate(),
                request.getAvgResponseTime()
        );
        
        var decision = selfHealingAgent.process(healthCheck);
        
        Map<String, Object> response = new HashMap<>();
        response.put("decision", decision);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Health check for agent controller
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "AI Agent Controller is operational");
        response.put("anomalyDetection", anomalyDetectionAgent != null ? "ENABLED" : "DISABLED");
        response.put("jiraIntelligence", jiraIntelligenceAgent != null ? "ENABLED" : "DISABLED");
        response.put("selfHealing", selfHealingAgent != null ? "ENABLED" : "DISABLED");
        return ResponseEntity.ok(response);
    }
    
    /**
     * Request object for manual health checks
     */
    public static class HealthCheckRequest {
        private String serviceName;
        private boolean serviceUp;
        private double errorRate;
        private double avgResponseTime;
        
        public HealthCheckRequest() {}
        
        // Getters and Setters
        public String getServiceName() { return serviceName; }
        public void setServiceName(String serviceName) { this.serviceName = serviceName; }
        
        public boolean isServiceUp() { return serviceUp; }
        public void setServiceUp(boolean serviceUp) { this.serviceUp = serviceUp; }
        
        public double getErrorRate() { return errorRate; }
        public void setErrorRate(double errorRate) { this.errorRate = errorRate; }
        
        public double getAvgResponseTime() { return avgResponseTime; }
        public void setAvgResponseTime(double avgResponseTime) { this.avgResponseTime = avgResponseTime; }
    }
}
