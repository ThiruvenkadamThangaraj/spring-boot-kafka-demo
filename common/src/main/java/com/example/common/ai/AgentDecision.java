package com.example.common.ai;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a decision made by an AI Agent
 */
public class AgentDecision {
    
    private String agentName;
    private String action;
    private Map<String, Object> parameters;
    private double confidence;
    private String reasoning;
    private LocalDateTime timestamp;
    private boolean requiresHumanApproval;
    
    public AgentDecision() {
        this.parameters = new HashMap<>();
        this.timestamp = LocalDateTime.now();
        this.requiresHumanApproval = false;
    }
    
    public AgentDecision(String agentName, String action, double confidence, String reasoning) {
        this();
        this.agentName = agentName;
        this.action = action;
        this.confidence = confidence;
        this.reasoning = reasoning;
    }
    
    // Getters and Setters
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
    
    public void addParameter(String key, Object value) {
        this.parameters.put(key, value);
    }
    
    public double getConfidence() {
        return confidence;
    }
    
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
    
    public String getReasoning() {
        return reasoning;
    }
    
    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isRequiresHumanApproval() {
        return requiresHumanApproval;
    }
    
    public void setRequiresHumanApproval(boolean requiresHumanApproval) {
        this.requiresHumanApproval = requiresHumanApproval;
    }
    
    @Override
    public String toString() {
        return String.format("AgentDecision{agent='%s', action='%s', confidence=%.2f, reasoning='%s', timestamp=%s}",
                agentName, action, confidence, reasoning, timestamp);
    }
}
