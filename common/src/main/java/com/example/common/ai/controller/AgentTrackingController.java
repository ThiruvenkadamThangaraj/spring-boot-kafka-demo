package com.example.common.ai.controller;

import com.example.common.ai.tracking.AgentDecisionRecord;
import com.example.common.ai.tracking.AgentTrackingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API for tracking and reporting AI agent decisions
 */
@RestController
@RequestMapping("/api/ai-agents/tracking")
public class AgentTrackingController {
    
    @Autowired(required = false)
    private AgentTrackingService trackingService;
    
    /**
     * Get recent decisions (last 10)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AgentDecisionRecord>> getRecentDecisions() {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(trackingService.getRecentDecisions(10));
    }
    
    /**
     * Get high-risk decisions (anomaly score > threshold)
     */
    @GetMapping("/high-risk")
    public ResponseEntity<List<AgentDecisionRecord>> getHighRiskDecisions(
            @RequestParam(defaultValue = "0.7") double threshold) {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(trackingService.getHighRiskDecisions(threshold));
    }
    
    /**
     * Get decisions for a specific user
     */
    @GetMapping("/user/{username}")
    public ResponseEntity<List<AgentDecisionRecord>> getDecisionsByUser(@PathVariable String username) {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(trackingService.getDecisionsByUser(username));
    }
    
    /**
     * Get decisions by agent
     */
    @GetMapping("/agent/{agentName}")
    public ResponseEntity<List<AgentDecisionRecord>> getDecisionsByAgent(@PathVariable String agentName) {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(trackingService.getDecisionsByAgent(agentName));
    }
    
    /**
     * Get all Jira tickets created
     */
    @GetMapping("/jira-tickets")
    public ResponseEntity<List<AgentDecisionRecord>> getJiraTickets() {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        return ResponseEntity.ok(trackingService.getJiraTickets());
    }
    
    /**
     * Get comprehensive report for a time period
     */
    @GetMapping("/report")
    public ResponseEntity<AgentTrackingService.DecisionReport> getReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        
        // Default to last 24 hours if not specified
        if (start == null) {
            start = LocalDateTime.now().minusDays(1);
        }
        if (end == null) {
            end = LocalDateTime.now();
        }
        
        return ResponseEntity.ok(trackingService.getReport(start, end));
    }
    
    /**
     * Get quick stats summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        if (trackingService == null) {
            return ResponseEntity.status(503).build();
        }
        
        Map<String, Object> summary = new HashMap<>();
        
        // Get report for last 24 hours
        LocalDateTime start = LocalDateTime.now().minusDays(1);
        LocalDateTime end = LocalDateTime.now();
        AgentTrackingService.DecisionReport report = trackingService.getReport(start, end);
        
        summary.put("last24Hours", report);
        summary.put("recentDecisions", trackingService.getRecentDecisions(5));
        summary.put("highRiskDecisions", trackingService.getHighRiskDecisions(0.7).size());
        summary.put("jiraTicketsCreated", trackingService.getJiraTickets().size());
        
        return ResponseEntity.ok(summary);
    }
}
