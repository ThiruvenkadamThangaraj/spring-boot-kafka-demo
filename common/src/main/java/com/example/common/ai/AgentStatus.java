package com.example.common.ai;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Current status and metrics of an AI Agent
 */
public class AgentStatus {
    
    private String agentName;
    private boolean active;
    private int decisionsProcessed;
    private double averageConfidence;
    private double successRate;
    private LocalDateTime lastActivity;
    private Map<String, Object> metrics;
    
    public AgentStatus() {
        this.metrics = new HashMap<>();
        this.active = true;
        this.lastActivity = LocalDateTime.now();
    }
    
    public AgentStatus(String agentName) {
        this();
        this.agentName = agentName;
    }
    
    // Getters and Setters
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public boolean isActive() {
        return active;
    }
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    public int getDecisionsProcessed() {
        return decisionsProcessed;
    }
    
    public void setDecisionsProcessed(int decisionsProcessed) {
        this.decisionsProcessed = decisionsProcessed;
    }
    
    public double getAverageConfidence() {
        return averageConfidence;
    }
    
    public void setAverageConfidence(double averageConfidence) {
        this.averageConfidence = averageConfidence;
    }
    
    public double getSuccessRate() {
        return successRate;
    }
    
    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }
    
    public LocalDateTime getLastActivity() {
        return lastActivity;
    }
    
    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }
    
    public Map<String, Object> getMetrics() {
        return metrics;
    }
    
    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }
    
    public void addMetric(String key, Object value) {
        this.metrics.put(key, value);
    }
    
    @Override
    public String toString() {
        return String.format("AgentStatus{name='%s', active=%s, decisions=%d, avgConfidence=%.2f, successRate=%.2f}",
                agentName, active, decisionsProcessed, averageConfidence, successRate);
    }
}
