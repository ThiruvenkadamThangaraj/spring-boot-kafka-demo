package com.example.common.ai.agents;

import com.example.common.ai.AgentDecision;
import com.example.common.ai.BaseAgent;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Smart Message Routing Agent - Intelligently routes messages based on learned patterns
 * Optimizes message processing by predicting best service routing
 */
@Component
public class MessageRoutingAgent extends BaseAgent {
    
    private final Map<String, ServicePerformanceStats> servicePerformance = new ConcurrentHashMap<>();
    
    public MessageRoutingAgent() {
        super("MessageRoutingAgent");
        initializeServiceStats();
    }
    
    private void initializeServiceStats() {
        String[] services = {"evaluation-service", "sampling-service", "evidence-service", 
                           "remediation-service", "jira-service"};
        for (String service : services) {
            servicePerformance.put(service, new ServicePerformanceStats());
        }
    }
    
    @Override
    protected AgentDecision executeDecisionLogic(Object input) {
        if (!(input instanceof RoutingRequest)) {
            return createErrorDecision("Invalid input type, expected RoutingRequest");
        }
        
        RoutingRequest request = (RoutingRequest) input;
        
        // Analyze current system state
        String optimalRoute = determineOptimalRoute(request);
        double confidence = calculateRoutingConfidence(request, optimalRoute);
        String reasoning = buildRoutingReasoning(request, optimalRoute);
        
        AgentDecision decision = new AgentDecision(name, "ROUTE", confidence, reasoning);
        decision.addParameter("targetService", optimalRoute);
        decision.addParameter("messageType", request.getMessageType());
        decision.addParameter("priority", request.getPriority());
        
        logger.debug("🔀 Routing decision: {} -> {}, confidence={}", 
                request.getMessageType(), optimalRoute, confidence);
        
        return decision;
    }
    
    private String determineOptimalRoute(RoutingRequest request) {
        // Implement intelligent routing logic
        // This could be enhanced with ML models for pattern learning
        
        String messageType = request.getMessageType();
        
        // Simple rule-based routing (can be enhanced)
        if (messageType.contains("evaluation")) {
            return "evaluation-service";
        } else if (messageType.contains("sampling")) {
            return "sampling-service";
        } else if (messageType.contains("evidence")) {
            return "evidence-service";
        } else if (messageType.contains("remediation")) {
            return "remediation-service";
        } else if (messageType.contains("jira")) {
            return "jira-service";
        }
        
        // Default routing
        return "evaluation-service";
    }
    
    private double calculateRoutingConfidence(RoutingRequest request, String route) {
        // Calculate confidence based on various factors
        double baseConfidence = 0.8;
        
        // Adjust based on message characteristics
        if (request.getPriority() > 5) {
            baseConfidence += 0.1;
        }
        
        return Math.min(baseConfidence, 1.0);
    }
    
    private String buildRoutingReasoning(RoutingRequest request, String route) {
        return String.format("Message type '%s' routed to %s based on content analysis",
                request.getMessageType(), route);
    }
    
    /**
     * Routing request object
     */
    public static class RoutingRequest {
        private String messageType;
        private int priority;
        private Map<String, Object> metadata;
        
        public RoutingRequest() {
            this.metadata = new HashMap<>();
        }
        
        public RoutingRequest(String messageType, int priority) {
            this();
            this.messageType = messageType;
            this.priority = priority;
        }
        
        public String getMessageType() { return messageType; }
        public void setMessageType(String messageType) { this.messageType = messageType; }
        
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
        
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
    
    /**
     * Service performance statistics
     */
    private static class ServicePerformanceStats {
        private int routedMessages = 0;
        private double avgLatency = 0.0;
        
        public void recordRouting(double latency) {
            routedMessages++;
            avgLatency = (avgLatency * (routedMessages - 1) + latency) / routedMessages;
        }
        
        public int getRoutedMessages() { return routedMessages; }
        public double getAvgLatency() { return avgLatency; }
    }
}
