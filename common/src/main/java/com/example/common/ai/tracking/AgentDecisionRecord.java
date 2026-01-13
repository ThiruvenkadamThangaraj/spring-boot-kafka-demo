package com.example.common.ai.tracking;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Persistent record of AI agent decisions for tracking and reporting
 */
@Entity
@Table(name = "agent_decisions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDecisionRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String agentName;
    
    @Column(nullable = false)
    private String username;
    
    @Column(nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String action; // ALLOW, FLAG_FOR_REVIEW, QUARANTINE
    
    @Column(nullable = false)
    private Double confidence;
    
    @Column(nullable = false)
    private Double anomalyScore;
    
    @Column(length = 1000)
    private String reasoning;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column
    private Boolean jiraTicketCreated;
    
    @Column
    private String jiraTicketTitle;
    
    @Column
    private String jiraTicketPriority;
    
    @Column
    private Boolean emailSent;
    
    @Column(length = 500)
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
