package com.example.common.ai.tracking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for tracking and reporting AI agent decisions
 */
@Service
public class AgentTrackingService {
    
    private static final Logger logger = LoggerFactory.getLogger(AgentTrackingService.class);
    
    @Autowired(required = false)
    private AgentDecisionRepository repository;
    
    /**
     * Track a decision made by an AI agent
     */
    public AgentDecisionRecord trackDecision(AgentDecisionRecord record) {
        logger.info("📊 Tracking decision: Agent={}, User={}, Action={}, Score={}", 
                record.getAgentName(), record.getUsername(), record.getAction(), record.getAnomalyScore());
        return repository.save(record);
    }
    
    /**
     * Get decision report for a time period
     */
    public DecisionReport getReport(LocalDateTime start, LocalDateTime end) {
        List<AgentDecisionRecord> decisions = repository.findByTimestampBetweenOrderByTimestampDesc(start, end);
        return generateReport(decisions);
    }
    
    /**
     * Get recent decisions
     */
    public List<AgentDecisionRecord> getRecentDecisions(int limit) {
        return repository.findTop10ByOrderByTimestampDesc();
    }
    
    /**
     * Get high-risk decisions
     */
    public List<AgentDecisionRecord> getHighRiskDecisions(double threshold) {
        return repository.findHighRiskDecisions(threshold);
    }
    
    /**
     * Get decisions by agent
     */
    public List<AgentDecisionRecord> getDecisionsByAgent(String agentName) {
        return repository.findByAgentNameOrderByTimestampDesc(agentName);
    }
    
    /**
     * Get decisions for a user
     */
    public List<AgentDecisionRecord> getDecisionsByUser(String username) {
        return repository.findByUsernameOrderByTimestampDesc(username);
    }
    
    /**
     * Get all Jira tickets created
     */
    public List<AgentDecisionRecord> getJiraTickets() {
        return repository.findByJiraTicketCreatedTrueOrderByTimestampDesc();
    }
    
    /**
     * Generate comprehensive report
     */
    private DecisionReport generateReport(List<AgentDecisionRecord> decisions) {
        DecisionReport report = new DecisionReport();
        report.setTotalDecisions(decisions.size());
        report.setStartDate(decisions.isEmpty() ? null : decisions.get(decisions.size() - 1).getTimestamp());
        report.setEndDate(decisions.isEmpty() ? null : decisions.get(0).getTimestamp());
        
        // Count by action
        Map<String, Long> actionCounts = decisions.stream()
                .collect(Collectors.groupingBy(AgentDecisionRecord::getAction, Collectors.counting()));
        report.setActionCounts(actionCounts);
        
        // Count by agent
        Map<String, Long> agentCounts = decisions.stream()
                .collect(Collectors.groupingBy(AgentDecisionRecord::getAgentName, Collectors.counting()));
        report.setAgentCounts(agentCounts);
        
        // Calculate average anomaly score
        double avgScore = decisions.stream()
                .mapToDouble(AgentDecisionRecord::getAnomalyScore)
                .average()
                .orElse(0.0);
        report.setAverageAnomalyScore(avgScore);
        
        // Count Jira tickets
        long jiraTickets = decisions.stream()
                .filter(d -> Boolean.TRUE.equals(d.getJiraTicketCreated()))
                .count();
        report.setJiraTicketsCreated(jiraTickets);
        
        // High risk count (score > 0.7)
        long highRisk = decisions.stream()
                .filter(d -> d.getAnomalyScore() > 0.7)
                .count();
        report.setHighRiskCount(highRisk);
        
        return report;
    }
    
    /**
     * Decision Report DTO
     */
    public static class DecisionReport {
        private Integer totalDecisions;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private Map<String, Long> actionCounts;
        private Map<String, Long> agentCounts;
        private Double averageAnomalyScore;
        private Long jiraTicketsCreated;
        private Long highRiskCount;
        
        // Getters and Setters
        public Integer getTotalDecisions() { return totalDecisions; }
        public void setTotalDecisions(Integer totalDecisions) { this.totalDecisions = totalDecisions; }
        
        public LocalDateTime getStartDate() { return startDate; }
        public void setStartDate(LocalDateTime startDate) { this.startDate = startDate; }
        
        public LocalDateTime getEndDate() { return endDate; }
        public void setEndDate(LocalDateTime endDate) { this.endDate = endDate; }
        
        public Map<String, Long> getActionCounts() { return actionCounts; }
        public void setActionCounts(Map<String, Long> actionCounts) { this.actionCounts = actionCounts; }
        
        public Map<String, Long> getAgentCounts() { return agentCounts; }
        public void setAgentCounts(Map<String, Long> agentCounts) { this.agentCounts = agentCounts; }
        
        public Double getAverageAnomalyScore() { return averageAnomalyScore; }
        public void setAverageAnomalyScore(Double averageAnomalyScore) { this.averageAnomalyScore = averageAnomalyScore; }
        
        public Long getJiraTicketsCreated() { return jiraTicketsCreated; }
        public void setJiraTicketsCreated(Long jiraTicketsCreated) { this.jiraTicketsCreated = jiraTicketsCreated; }
        
        public Long getHighRiskCount() { return highRiskCount; }
        public void setHighRiskCount(Long highRiskCount) { this.highRiskCount = highRiskCount; }
    }
}
