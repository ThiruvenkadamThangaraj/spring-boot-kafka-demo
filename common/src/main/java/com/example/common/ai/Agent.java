package com.example.common.ai;

import java.util.Map;

/**
 * Base interface for all AI Agents in the system.
 * Agents are autonomous entities that can perceive, reason, and act.
 */
public interface Agent {
    
    /**
     * Get the unique name/identifier of this agent
     */
    String getName();
    
    /**
     * Initialize the agent with configuration
     */
    void initialize(Map<String, Object> config);
    
    /**
     * Process input and make autonomous decisions
     * @param input The input data to process
     * @return The agent's decision/action result
     */
    AgentDecision process(Object input);
    
    /**
     * Learn from feedback to improve future decisions
     * @param feedback The feedback data
     */
    void learn(AgentFeedback feedback);
    
    /**
     * Get current agent state and metrics
     */
    AgentStatus getStatus();
    
    /**
     * Shutdown the agent gracefully
     */
    void shutdown();
}
