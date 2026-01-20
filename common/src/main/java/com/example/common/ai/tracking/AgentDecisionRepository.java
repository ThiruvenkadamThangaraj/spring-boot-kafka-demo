package com.example.common.ai.tracking;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentDecisionRepository extends MongoRepository<AgentDecisionRecord, String> {
    
    // Find all decisions by agent name
    List<AgentDecisionRecord> findByAgentNameOrderByTimestampDesc(String agentName);
    
    // Find all decisions by action type
    List<AgentDecisionRecord> findByActionOrderByTimestampDesc(String action);
    
    // Find decisions within time range
    List<AgentDecisionRecord> findByTimestampBetweenOrderByTimestampDesc(
            LocalDateTime start, LocalDateTime end);
    
    // Find high-risk decisions (anomaly score above threshold)
    List<AgentDecisionRecord> findByAnomalyScoreGreaterThanEqualOrderByTimestampDesc(Double threshold);
    
    // Get decisions for a specific user
    List<AgentDecisionRecord> findByUsernameOrderByTimestampDesc(String username);
    
    
    // Get recent decisions (last N)
    List<AgentDecisionRecord> findTop10ByOrderByTimestampDesc();
    
    // Get decisions that created Jira tickets
    List<AgentDecisionRecord> findByJiraTicketCreatedTrueOrderByTimestampDesc();
}
