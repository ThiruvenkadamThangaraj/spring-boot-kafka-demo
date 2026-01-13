package com.example.common.ai.tracking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentDecisionRepository extends JpaRepository<AgentDecisionRecord, Long> {
    
    // Find all decisions by agent name
    List<AgentDecisionRecord> findByAgentNameOrderByTimestampDesc(String agentName);
    
    // Find all decisions by action type
    List<AgentDecisionRecord> findByActionOrderByTimestampDesc(String action);
    
    // Find decisions within time range
    List<AgentDecisionRecord> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start, LocalDateTime end);
    
    // Find high-risk decisions (anomaly score above threshold)
    @Query("SELECT d FROM AgentDecisionRecord d WHERE d.anomalyScore >= :threshold ORDER BY d.timestamp DESC")
    List<AgentDecisionRecord> findHighRiskDecisions(@Param("threshold") Double threshold);
    
    // Get decisions for a specific user
    List<AgentDecisionRecord> findByUsernameOrderByTimestampDesc(String username);
    
    // Count decisions by action
    @Query("SELECT d.action, COUNT(d) FROM AgentDecisionRecord d GROUP BY d.action")
    List<Object[]> countByAction();
    
    // Get recent decisions (last N)
    List<AgentDecisionRecord> findTop10ByOrderByTimestampDesc();
    
    // Get decisions that created Jira tickets
    List<AgentDecisionRecord> findByJiraTicketCreatedTrueOrderByTimestampDesc();
}
