package com.example.common.consumer;

import com.example.common.ai.AgentDecision;
import com.example.common.ai.agents.AnomalyDetectionAgent;
import com.example.common.ai.agents.JiraIntelligenceAgent;
import com.example.common.event.UserCreatedEvent;
import com.example.common.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
public class UserCreatedEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserCreatedEventConsumer.class);

    @Autowired
    private EmailService emailService;
    
    @Autowired(required = false)
    private AnomalyDetectionAgent anomalyDetectionAgent;
    
    @Autowired(required = false)
    private JiraIntelligenceAgent jiraIntelligenceAgent;
    
    @PostConstruct
    public void initializeAgents() {
        if (anomalyDetectionAgent != null) {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);
            config.put("anomalyThreshold", 0.7);
            anomalyDetectionAgent.initialize(config);
            logger.info("🤖 Anomaly Detection Agent initialized");
        }
        
        if (jiraIntelligenceAgent != null) {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);
            jiraIntelligenceAgent.initialize(config);
            logger.info("🤖 Jira Intelligence Agent initialized");
        }
    }

    @KafkaListener(topics = "user-created-events", groupId = "email-service-group")
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        logger.info("📨 Received User Created Event: {} from {}", 
                event.getUsername(), event.getServiceName());
        
        // AI-powered anomaly detection
        if (anomalyDetectionAgent != null) {
            AgentDecision anomalyDecision = anomalyDetectionAgent.process(event);
            
            String action = anomalyDecision.getAction();
            
            if ("QUARANTINE".equals(action)) {
                logger.warn("🚨 USER QUARANTINED: {} - Reason: {}", 
                        event.getUsername(), anomalyDecision.getReasoning());
                
                // Create Jira ticket for security review
                if (jiraIntelligenceAgent != null) {
                    createSecurityTicket(event, anomalyDecision);
                }
                
                // Skip email for quarantined users
                logger.info("⚠️ Skipping email for quarantined user: {}", event.getUsername());
                return;
                
            } else if ("FLAG_FOR_REVIEW".equals(action)) {
                logger.info("⚠️ User flagged for review: {} - Reason: {}", 
                        event.getUsername(), anomalyDecision.getReasoning());
                
                // Create low-priority ticket
                if (jiraIntelligenceAgent != null) {
                    createReviewTicket(event, anomalyDecision);
                }
            } else {
                logger.debug("✅ User passed anomaly check: {}", event.getUsername());
            }
        }
        
        // Send welcome email
        emailService.sendWelcomeEmail(
            event.getEmail(),
            event.getFirstName(),
            event.getLastName(),
            event.getServiceName()
        );
    }
    
    private void createSecurityTicket(UserCreatedEvent event, AgentDecision anomalyDecision) {
        JiraIntelligenceAgent.TicketRequest ticketRequest = new JiraIntelligenceAgent.TicketRequest();
        ticketRequest.setType("USER_ANOMALY");
        ticketRequest.setDescription(String.format(
                "Suspicious user account detected:\n" +
                "Username: %s\n" +
                "Email: %s\n" +
                "Service: %s\n\n" +
                "Anomaly Score: %.2f\n" +
                "Detected Issues: %s",
                event.getUsername(),
                event.getEmail(),
                event.getServiceName(),
                anomalyDecision.getParameters().get("anomalyScore"),
                anomalyDecision.getParameters().get("detectedIssues")
        ));
        ticketRequest.setContext(event.getServiceName());
        ticketRequest.setUserIdentifier(event.getUsername());
        ticketRequest.setSource("AnomalyDetectionAgent");
        ticketRequest.setConfidence(anomalyDecision.getConfidence());
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("email", event.getEmail());
        metadata.put("userId", event.getUserId());
        metadata.put("detectedAt", anomalyDecision.getTimestamp());
        ticketRequest.setMetadata(metadata);
        
        AgentDecision jiraDecision = jiraIntelligenceAgent.process(ticketRequest);
        logger.info("🎫 Security ticket created: {}", jiraDecision.getReasoning());
    }
    
    private void createReviewTicket(UserCreatedEvent event, AgentDecision anomalyDecision) {
        JiraIntelligenceAgent.TicketRequest ticketRequest = new JiraIntelligenceAgent.TicketRequest();
        ticketRequest.setType("DATA_QUALITY");
        ticketRequest.setDescription(String.format(
                "User account flagged for review:\n" +
                "Username: %s\n" +
                "Email: %s\n" +
                "Reason: %s",
                event.getUsername(),
                event.getEmail(),
                anomalyDecision.getReasoning()
        ));
        ticketRequest.setContext(event.getServiceName());
        ticketRequest.setUserIdentifier(event.getUsername());
        ticketRequest.setSource("AnomalyDetectionAgent");
        ticketRequest.setConfidence(anomalyDecision.getConfidence());
        
        jiraIntelligenceAgent.process(ticketRequest);
    }
}
