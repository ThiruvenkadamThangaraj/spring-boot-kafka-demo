package com.example.common.ai.tracking;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistent record of AI agent decisions for tracking and reporting
 */
@Document(collection = "agent_decisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDecisionRecord {
    
    @Id
    private String id;
    
    private String agentName;
    
    private String username;
    
    private String email;
    
    private String action; // ALLOW, FLAG_FOR_REVIEW, QUARANTINE
    
    private Double confidence;
    
    private Double anomalyScore;
    
    private String reasoning;
    
    private LocalDateTime timestamp;
    
    private Boolean jiraTicketCreated;
    
    private String jiraTicketTitle;
    
    private String jiraTicketPriority;
    
    private Boolean emailSent;
    
    private String context;
    
    public AgentDecisionRecord(String agentName, String username, String email, 
                               String action, Double confidence, Double anomalyScore,
                               String reasoning, LocalDateTime timestamp) {
        this.agentName = agentName;
        this.username = username;
        this.email = email;
        this.action = action;
        this.confidence = confidence;
        this.anomalyScore = anomalyScore;
        this.reasoning = reasoning;
        this.timestamp = timestamp;
    }
}
