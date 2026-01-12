package com.example.common.ai.agents;

import com.example.common.ai.AgentDecision;
import com.example.common.ai.BaseAgent;
import com.example.common.event.UserCreatedEvent;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Anomaly Detection Agent - Detects unusual patterns in user creation events
 * Uses rule-based and statistical methods to identify anomalies
 */
@Component
public class AnomalyDetectionAgent extends BaseAgent {
    
    private final Map<String, UserEventStats> userEmailStats = new ConcurrentHashMap<>();
    private final Map<String, List<LocalDateTime>> recentEvents = new ConcurrentHashMap<>();
    private final List<String> suspiciousPatterns = new ArrayList<>();
    
    // Thresholds
    private static final int MAX_EVENTS_PER_EMAIL_PER_MINUTE = 5;
    private static final int MAX_EVENTS_FROM_SAME_IP_PER_MINUTE = 10;
    private static final double ANOMALY_THRESHOLD = 0.7;
    
    public AnomalyDetectionAgent() {
        super("AnomalyDetectionAgent");
        initializeSuspiciousPatterns();
    }
    
    private void initializeSuspiciousPatterns() {
        suspiciousPatterns.add(".*test\\d{3,}.*"); // test123, test456, etc.
        suspiciousPatterns.add(".*temp\\d{3,}.*");
        suspiciousPatterns.add(".*fake.*");
        suspiciousPatterns.add(".*spam.*");
        suspiciousPatterns.add(".*bot.*");
    }
    
    @Override
    protected AgentDecision executeDecisionLogic(Object input) {
        if (!(input instanceof UserCreatedEvent)) {
            return createErrorDecision("Invalid input type, expected UserCreatedEvent");
        }
        
        UserCreatedEvent event = (UserCreatedEvent) input;
        
        // Multi-criteria anomaly detection
        double anomalyScore = 0.0;
        List<String> reasons = new ArrayList<>();
        
        // 1. Check for suspicious email patterns
        if (hasSuspiciousEmailPattern(event.getEmail())) {
            anomalyScore += 0.3;
            reasons.add("Suspicious email pattern detected");
        }
        
        // 2. Check for rapid creation rate
        if (isRapidCreation(event.getEmail())) {
            anomalyScore += 0.4;
            reasons.add("Rapid account creation detected");
        }
        
        // 3. Check for unusual username patterns
        if (hasUnusualUsernamePattern(event.getUsername())) {
            anomalyScore += 0.2;
            reasons.add("Unusual username pattern");
        }
        
        // 4. Check for email domain reputation (simplified)
        if (hasSuspiciousDomain(event.getEmail())) {
            anomalyScore += 0.25;
            reasons.add("Suspicious email domain");
        }
        
        // 5. Check for missing critical fields
        if (hasMissingCriticalFields(event)) {
            anomalyScore += 0.15;
            reasons.add("Missing critical user information");
        }
        
        // Update statistics
        updateStatistics(event);
        
        // Make decision
        String action;
        double confidence;
        String reasoning;
        
        if (anomalyScore >= ANOMALY_THRESHOLD) {
            action = "QUARANTINE";
            confidence = Math.min(anomalyScore, 1.0);
            reasoning = "Anomaly detected: " + String.join(", ", reasons);
            logger.warn("🚨 ANOMALY DETECTED for user '{}': score={}, reasons={}", 
                    event.getUsername(), anomalyScore, reasons);
        } else if (anomalyScore >= 0.4) {
            action = "FLAG_FOR_REVIEW";
            confidence = anomalyScore;
            reasoning = "Potential anomaly: " + String.join(", ", reasons);
            logger.info("⚠️ User '{}' flagged for review: score={}", event.getUsername(), anomalyScore);
        } else {
            action = "ALLOW";
            confidence = 1.0 - anomalyScore;
            reasoning = "User creation appears normal";
        }
        
        AgentDecision decision = new AgentDecision(name, action, confidence, reasoning);
        decision.addParameter("anomalyScore", anomalyScore);
        decision.addParameter("username", event.getUsername());
        decision.addParameter("email", event.getEmail());
        decision.addParameter("detectedIssues", reasons);
        decision.setRequiresHumanApproval(anomalyScore >= ANOMALY_THRESHOLD);
        
        return decision;
    }
    
    private boolean hasSuspiciousEmailPattern(String email) {
        if (email == null || email.isEmpty()) {
            return true;
        }
        
        String lowerEmail = email.toLowerCase();
        for (String pattern : suspiciousPatterns) {
            if (Pattern.matches(pattern, lowerEmail)) {
                return true;
            }
        }
        
        // Check for sequential numbers in email
        if (Pattern.matches(".*\\d{5,}.*", lowerEmail)) {
            return true;
        }
        
        return false;
    }
    
    private boolean isRapidCreation(String email) {
        LocalDateTime now = LocalDateTime.now();
        recentEvents.computeIfAbsent(email, k -> new ArrayList<>()).add(now);
        
        // Clean old entries
        List<LocalDateTime> events = recentEvents.get(email);
        events.removeIf(time -> ChronoUnit.MINUTES.between(time, now) > 1);
        
        return events.size() > MAX_EVENTS_PER_EMAIL_PER_MINUTE;
    }
    
    private boolean hasUnusualUsernamePattern(String username) {
        if (username == null || username.length() < 3) {
            return true;
        }
        
        // Check for all numbers
        if (username.matches("\\d+")) {
            return true;
        }
        
        // Check for repeated characters (e.g., "aaaaaaa")
        if (username.matches("(.)\\1{4,}")) {
            return true;
        }
        
        // Check for random-looking strings (high consonant-to-vowel ratio)
        long vowels = username.toLowerCase().chars()
                .filter(ch -> "aeiou".indexOf(ch) >= 0)
                .count();
        double vowelRatio = (double) vowels / username.length();
        if (vowelRatio < 0.1 && username.length() > 5) {
            return true;
        }
        
        return false;
    }
    
    private boolean hasSuspiciousDomain(String email) {
        if (email == null || !email.contains("@")) {
            return true;
        }
        
        String domain = email.substring(email.indexOf("@") + 1).toLowerCase();
        
        // Blacklist of common temporary email domains
        List<String> suspiciousDomains = Arrays.asList(
                "tempmail.com", "throwaway.email", "guerrillamail.com",
                "10minutemail.com", "mailinator.com", "trashmail.com"
        );
        
        return suspiciousDomains.contains(domain);
    }
    
    private boolean hasMissingCriticalFields(UserCreatedEvent event) {
        return event.getFirstName() == null || event.getFirstName().isEmpty() ||
               event.getLastName() == null || event.getLastName().isEmpty() ||
               event.getEmail() == null || event.getEmail().isEmpty();
    }
    
    private void updateStatistics(UserCreatedEvent event) {
        UserEventStats stats = userEmailStats.computeIfAbsent(
                event.getEmail(), 
                k -> new UserEventStats()
        );
        stats.incrementCount();
        stats.setLastSeen(LocalDateTime.now());
    }
    
    /**
     * Get anomaly statistics for monitoring
     */
    public Map<String, Object> getAnomalyStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEmailsTracked", userEmailStats.size());
        stats.put("activeMonitoringWindows", recentEvents.size());
        stats.put("decisionsProcessed", decisionsProcessed.get());
        stats.put("successfulDecisions", successfulDecisions.get());
        return stats;
    }
    
    /**
     * Reset statistics (useful for testing or scheduled maintenance)
     */
    public void resetStatistics() {
        userEmailStats.clear();
        recentEvents.clear();
        logger.info("🔄 Anomaly detection statistics reset");
    }
    
    /**
     * Internal class to track user event statistics
     */
    private static class UserEventStats {
        private int eventCount = 0;
        private LocalDateTime firstSeen = LocalDateTime.now();
        private LocalDateTime lastSeen = LocalDateTime.now();
        
        public void incrementCount() {
            eventCount++;
        }
        
        public void setLastSeen(LocalDateTime lastSeen) {
            this.lastSeen = lastSeen;
        }
        
        public int getEventCount() {
            return eventCount;
        }
    }
}
