package com.example.common.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for AI Agents providing common functionality
 */
public abstract class BaseAgent implements Agent {
    
    protected final Logger logger = LoggerFactory.getLogger(getClass());
    
    protected String name;
    protected Map<String, Object> config;
    protected AgentStatus status;
    protected AtomicInteger decisionsProcessed;
    protected AtomicInteger successfulDecisions;
    protected volatile boolean active;
    
    protected BaseAgent(String name) {
        this.name = name;
        this.config = new ConcurrentHashMap<>();
        this.status = new AgentStatus(name);
        this.decisionsProcessed = new AtomicInteger(0);
        this.successfulDecisions = new AtomicInteger(0);
        this.active = false;
    }
    
    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public void initialize(Map<String, Object> config) {
        this.config.putAll(config);
        this.active = true;
        logger.info("🤖 Agent '{}' initialized with config: {}", name, config);
    }
    
    @Override
    public AgentDecision process(Object input) {
        if (!active) {
            logger.warn("Agent '{}' is not active, cannot process input", name);
            return createErrorDecision("Agent not active");
        }
        
        try {
            AgentDecision decision = executeDecisionLogic(input);
            decisionsProcessed.incrementAndGet();
            updateStatus();
            logDecision(decision);
            return decision;
        } catch (Exception e) {
            logger.error("Error processing input in agent '{}': {}", name, e.getMessage(), e);
            return createErrorDecision("Processing error: " + e.getMessage());
        }
    }
    
    /**
     * Core decision-making logic - to be implemented by subclasses
     */
    protected abstract AgentDecision executeDecisionLogic(Object input);
    
    @Override
    public void learn(AgentFeedback feedback) {
        if (feedback.isSuccessful()) {
            successfulDecisions.incrementAndGet();
        }
        updateStatus();
        logger.debug("Agent '{}' learning from feedback: {}", name, feedback.getOutcome());
    }
    
    @Override
    public AgentStatus getStatus() {
        updateStatus();
        return status;
    }
    
    @Override
    public void shutdown() {
        active = false;
        logger.info("🛑 Agent '{}' shutting down. Processed {} decisions", name, decisionsProcessed.get());
    }
    
    protected void updateStatus() {
        int processed = decisionsProcessed.get();
        int successful = successfulDecisions.get();
        
        status.setActive(active);
        status.setDecisionsProcessed(processed);
        status.setSuccessRate(processed > 0 ? (double) successful / processed : 0.0);
    }
    
    protected AgentDecision createErrorDecision(String errorMessage) {
        AgentDecision decision = new AgentDecision(name, "ERROR", 0.0, errorMessage);
        decision.setRequiresHumanApproval(true);
        return decision;
    }
    
    protected void logDecision(AgentDecision decision) {
        if (logger.isDebugEnabled()) {
            logger.debug("🤖 {} decision: action='{}', confidence={}, reasoning='{}'",
                    name, decision.getAction(), decision.getConfidence(), decision.getReasoning());
        }
    }
}
