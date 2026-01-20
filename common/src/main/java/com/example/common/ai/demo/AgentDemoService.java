package com.example.common.ai.demo;

import com.example.common.ai.AgentDecision;
import com.example.common.ai.agents.AnomalyDetectionAgent;
import com.example.common.ai.agents.JiraIntelligenceAgent;
import com.example.common.ai.tracking.AgentDecisionRecord;
import com.example.common.ai.tracking.AgentTrackingService;
import com.example.common.event.UserCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Demo service to showcase AI agents without Kafka
 * For demonstration purposes only
 */
@Service
public class AgentDemoService {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentDemoService.class);
    
    @Autowired(required = false)
    private AnomalyDetectionAgent anomalyDetectionAgent;
    
    @Autowired(required = false)
    private JiraIntelligenceAgent jiraIntelligenceAgent;
    
    @Autowired(required = false)
    private AgentTrackingService trackingService;
    
    /**
     * Simulates the full AI agent workflow for a user
     */
    public AgentDemoResult demonstrateAgents(UserDemoRequest request) {
        logger.info("🎬 DEMO MODE: Testing AI agents for user: {}", request.getUsername());
        
        AgentDemoResult result = new AgentDemoResult();
        result.setUsername(request.getUsername());
        result.setEmail(request.getEmail());
        result.setTimestamp(LocalDateTime.now());
        
        // Create a simulated event
        UserCreatedEvent event = new UserCreatedEvent(
                request.getUserId(),
                request.getUsername(),
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                "DEMO",           // department
                50000.0,          // salary
                "DEMO-SERVICE",   // serviceName
                LocalDateTime.now() // createdAt
        );
        
        // 1. Run Anomaly Detection
        if (anomalyDetectionAgent != null) {
            logger.info("🔍 Running Anomaly Detection Agent...");
            AgentDecision anomalyDecision = anomalyDetectionAgent.process(event);
            
            result.setAnomalyDetection(new AnomalyResult(
                    anomalyDecision.getAction(),
                    anomalyDecision.getConfidence(),
                    anomalyDecision.getReasoning(),
                    (Double) anomalyDecision.getParameters().getOrDefault("anomalyScore", 0.0)
            ));
            
            logger.info("✅ Anomaly Detection: {} (confidence: {}, score: {})", 
                    anomalyDecision.getAction(), 
                    anomalyDecision.getConfidence(),
                    result.getAnomalyDetection().getAnomalyScore());
            
            // Save decision to database for tracking
            if (trackingService != null) {
                AgentDecisionRecord record = new AgentDecisionRecord(
                        "AnomalyDetectionAgent",
                        request.getUsername(),
                        request.getEmail(),
                        anomalyDecision.getAction(),
                        anomalyDecision.getConfidence(),
                        result.getAnomalyDetection().getAnomalyScore(),
                        anomalyDecision.getReasoning(),
                        LocalDateTime.now()
                );
                record.setContext("DEMO");
                
                // Track Jira ticket if created
                if (result.getJiraTicket() != null) {
                    record.setJiraTicketCreated(true);
                    record.setJiraTicketTitle(result.getJiraTicket().getTitle());
                    record.setJiraTicketPriority(result.getJiraTicket().getPriority());
                }
                
                record.setEmailSent(result.isEmailSent());
                
                trackingService.trackDecision(record);
            }
            
            // 2. If suspicious, create Jira ticket
            if (jiraIntelligenceAgent != null && 
                ("QUARANTINE".equals(anomalyDecision.getAction()) || 
                 "FLAG_FOR_REVIEW".equals(anomalyDecision.getAction()))) {
                
                logger.info("🎫 Creating Jira ticket via Intelligence Agent...");
                
                JiraIntelligenceAgent.TicketRequest ticketRequest = new JiraIntelligenceAgent.TicketRequest();
                ticketRequest.setType(anomalyDecision.getAction().equals("QUARANTINE") ? 
                        "USER_ANOMALY" : "DATA_QUALITY");
                ticketRequest.setDescription(String.format(
                        "User: %s (%s)\nReason: %s\nAnomaly Score: %.2f",
                        request.getUsername(),
                        request.getEmail(),
                        anomalyDecision.getReasoning(),
                        result.getAnomalyDetection().getAnomalyScore()
                ));
                ticketRequest.setContext("DEMO-SERVICE");
                ticketRequest.setUserIdentifier(request.getUsername());
                ticketRequest.setSource("AnomalyDetectionAgent");
                ticketRequest.setConfidence(anomalyDecision.getConfidence());
                
                AgentDecision jiraDecision = jiraIntelligenceAgent.process(ticketRequest);
                JiraIntelligenceAgent.JiraTicket ticket = 
                        (JiraIntelligenceAgent.JiraTicket) jiraDecision.getParameters().get("ticket");
                
                result.setJiraTicket(new JiraTicketResult(
                        ticket.getTitle(),
                        ticket.getPriority(),
                        ticket.getSuggestedAssignee(),
                        ticket.getEstimatedEffort(),
                        ticket.getIssueType()
                ));
                
                logger.info("✅ Jira Ticket: {} - Priority: {}, Assignee: {}", 
                        ticket.getTitle(), 
                        ticket.getPriority(),
                        ticket.getSuggestedAssignee());
            }
            
            // 3. Email decision
            if ("QUARANTINE".equals(anomalyDecision.getAction())) {
                result.setEmailSent(false);
                result.setEmailDecision("⛔ Email blocked - User quarantined");
                logger.warn("🚨 USER QUARANTINED - No email sent");
            } else if ("FLAG_FOR_REVIEW".equals(anomalyDecision.getAction())) {
                result.setEmailSent(true);
                result.setEmailDecision("✅ Email sent - User flagged for review");
                logger.info("⚠️ User flagged but email sent");
            } else {
                result.setEmailSent(true);
                result.setEmailDecision("✅ Email sent - User appears normal");
                logger.info("✅ Normal user - Email sent");
            }
        } else {
            result.setEmailSent(true);
            result.setEmailDecision("⚠️ AI agents not available - Email sent by default");
            logger.warn("⚠️ Anomaly detection agent not available");
        }
        
        logger.info("🎬 DEMO COMPLETE for user: {}", request.getUsername());
        return result;
    }
    
    /**
     * User demo request
     */
    public static class UserDemoRequest {
        private String userId;
        private String username;
        private String email;
        private String firstName;
        private String lastName;
        
        public UserDemoRequest() {}
        
        public UserDemoRequest(String userId, String username, String email, String firstName, String lastName) {
            this.userId = userId;
            this.username = username;
            this.email = email;
            this.firstName = firstName;
            this.lastName = lastName;
        }
        
        // Getters and Setters
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public String getFirstName() { return firstName; }
        public void setFirstName(String firstName) { this.firstName = firstName; }
        
        public String getLastName() { return lastName; }
        public void setLastName(String lastName) { this.lastName = lastName; }
    }
    
    /**
     * Demo result containing all AI agent decisions
     */
    public static class AgentDemoResult {
        private String username;
        private String email;
        private LocalDateTime timestamp;
        private AnomalyResult anomalyDetection;
        private JiraTicketResult jiraTicket;
        private boolean emailSent;
        private String emailDecision;
        
        // Getters and Setters
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public AnomalyResult getAnomalyDetection() { return anomalyDetection; }
        public void setAnomalyDetection(AnomalyResult anomalyDetection) { this.anomalyDetection = anomalyDetection; }
        
        public JiraTicketResult getJiraTicket() { return jiraTicket; }
        public void setJiraTicket(JiraTicketResult jiraTicket) { this.jiraTicket = jiraTicket; }
        
        public boolean isEmailSent() { return emailSent; }
        public void setEmailSent(boolean emailSent) { this.emailSent = emailSent; }
        
        public String getEmailDecision() { return emailDecision; }
        public void setEmailDecision(String emailDecision) { this.emailDecision = emailDecision; }
    }
    
    /**
     * Anomaly detection result
     */
    public static class AnomalyResult {
        private String action;
        private double confidence;
        private String reasoning;
        private double anomalyScore;
        
        public AnomalyResult() {}
        
        public AnomalyResult(String action, double confidence, String reasoning, double anomalyScore) {
            this.action = action;
            this.confidence = confidence;
            this.reasoning = reasoning;
            this.anomalyScore = anomalyScore;
        }
        
        // Getters and Setters
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        
        public double getConfidence() { return confidence; }
        public void setConfidence(double confidence) { this.confidence = confidence; }
        
        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }
        
        public double getAnomalyScore() { return anomalyScore; }
        public void setAnomalyScore(double anomalyScore) { this.anomalyScore = anomalyScore; }
    }
    
    /**
     * Jira ticket result
     */
    public static class JiraTicketResult {
        private String title;
        private String priority;
        private String assignee;
        private String estimatedEffort;
        private String issueType;
        
        public JiraTicketResult() {}
        
        public JiraTicketResult(String title, String priority, String assignee, String estimatedEffort, String issueType) {
            this.title = title;
            this.priority = priority;
            this.assignee = assignee;
            this.estimatedEffort = estimatedEffort;
            this.issueType = issueType;
        }
        
        // Getters and Setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getPriority() { return priority; }
        public void setPriority(String priority) { this.priority = priority; }
        
        public String getAssignee() { return assignee; }
        public void setAssignee(String assignee) { this.assignee = assignee; }
        
        public String getEstimatedEffort() { return estimatedEffort; }
        public void setEstimatedEffort(String estimatedEffort) { this.estimatedEffort = estimatedEffort; }
        
        public String getIssueType() { return issueType; }
        public void setIssueType(String issueType) { this.issueType = issueType; }
    }
}
