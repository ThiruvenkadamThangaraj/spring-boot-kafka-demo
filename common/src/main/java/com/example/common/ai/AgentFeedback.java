package com.example.common.ai;

import java.time.LocalDateTime;

/**
 * Feedback for agent learning and improvement
 */
public class AgentFeedback {
    
    private String agentName;
    private String decisionId;
    private boolean successful;
    private String outcome;
    private double reward;
    private LocalDateTime timestamp;
    
    public AgentFeedback() {
        this.timestamp = LocalDateTime.now();
    }
    
    public AgentFeedback(String agentName, boolean successful, String outcome, double reward) {
        this();
        this.agentName = agentName;
        this.successful = successful;
        this.outcome = outcome;
        this.reward = reward;
    }
    
    // Getters and Setters
    public String getAgentName() {
        return agentName;
    }
    
    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }
    
    public String getDecisionId() {
        return decisionId;
    }
    
    public void setDecisionId(String decisionId) {
        this.decisionId = decisionId;
    }
    
    public boolean isSuccessful() {
        return successful;
    }
    
    public void setSuccessful(boolean successful) {
        this.successful = successful;
    }
    
    public String getOutcome() {
        return outcome;
    }
    
    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }
    
    public double getReward() {
        return reward;
    }
    
    public void setReward(double reward) {
        this.reward = reward;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
