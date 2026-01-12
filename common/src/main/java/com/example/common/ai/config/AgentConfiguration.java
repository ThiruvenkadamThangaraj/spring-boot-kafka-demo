package com.example.common.ai.config;

import com.example.common.ai.agents.AnomalyDetectionAgent;
import com.example.common.ai.agents.JiraIntelligenceAgent;
import com.example.common.ai.agents.SelfHealingAgent;
import com.example.common.ai.agents.MessageRoutingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for AI Agents
 * Enable/disable agents via application properties
 */
@Configuration
public class AgentConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentConfiguration.class);
    
    @Bean
    @ConditionalOnProperty(name = "ai.agents.anomaly-detection.enabled", havingValue = "true", matchIfMissing = true)
    public AnomalyDetectionAgent anomalyDetectionAgent() {
        logger.info("🤖 Initializing Anomaly Detection Agent");
        return new AnomalyDetectionAgent();
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.agents.jira-intelligence.enabled", havingValue = "true", matchIfMissing = true)
    public JiraIntelligenceAgent jiraIntelligenceAgent() {
        logger.info("🤖 Initializing Jira Intelligence Agent");
        return new JiraIntelligenceAgent();
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.agents.self-healing.enabled", havingValue = "true", matchIfMissing = true)
    public SelfHealingAgent selfHealingAgent() {
        logger.info("🤖 Initializing Self-Healing Agent");
        return new SelfHealingAgent();
    }
    
    @Bean
    @ConditionalOnProperty(name = "ai.agents.smart-routing.enabled", havingValue = "true", matchIfMissing = true)
    public MessageRoutingAgent messageRoutingAgent() {
        logger.info("🤖 Initializing Message Routing Agent");
        return new MessageRoutingAgent();
    }
}
