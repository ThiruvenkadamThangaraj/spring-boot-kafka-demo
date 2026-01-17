package com.example.transfer.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
public class MonitorController {
    
    @Autowired
    private JdbcTemplate coordJdbcTemplate;
    
    /**
     * Get overall progress
     * GET http://localhost:8080/api/monitor/progress
     */
    @GetMapping("/progress")
    public Map<String, Object> getProgress() {
        Map<String, Object> progress = new HashMap<>();
        
        try {
            // Get partition counts by status
            Map<String, Object> stats = coordJdbcTemplate.queryForMap(
                "SELECT " +
                "    COUNT(*) as total_partitions, " +
                "    SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed, " +
                "    SUM(CASE WHEN status = 'PROCESSING' THEN 1 ELSE 0 END) as processing, " +
                "    SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END) as pending, " +
                "    SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) as failed, " +
                "    SUM(ISNULL(records_transferred, 0)) as total_records " +
                "FROM transfer_partitions"
            );
            
            progress.putAll(stats);
            
            // Calculate percentage
            int total = ((Number) stats.get("total_partitions")).intValue();
            int completed = ((Number) stats.get("completed")).intValue();
            double percentage = total > 0 ? (completed * 100.0 / total) : 0;
            progress.put("progress_percent", Math.round(percentage * 10) / 10.0);
            
            return progress;
            
        } catch (Exception e) {
            progress.put("error", e.getMessage());
            return progress;
        }
    }
    
    /**
     * Get partition details
     * GET http://localhost:8080/api/monitor/partitions
     */
    @GetMapping("/partitions")
    public List<Map<String, Object>> getPartitions() {
        return coordJdbcTemplate.queryForList(
            "SELECT " +
            "    partition_id, " +
            "    app_id_start, " +
            "    app_id_end, " +
            "    status, " +
            "    worker_id, " +
            "    records_transferred, " +
            "    started_at, " +
            "    completed_at " +
            "FROM transfer_partitions " +
            "ORDER BY partition_id"
        );
    }
    
    /**
     * Get worker statistics
     * GET http://localhost:8080/api/monitor/workers
     */
    @GetMapping("/workers")
    public List<Map<String, Object>> getWorkers() {
        return coordJdbcTemplate.queryForList(
            "SELECT " +
            "    worker_id, " +
            "    COUNT(*) as partitions_processed, " +
            "    SUM(records_transferred) as total_records, " +
            "    MIN(started_at) as first_partition_start, " +
            "    MAX(completed_at) as last_partition_complete " +
            "FROM transfer_partitions " +
            "WHERE worker_id IS NOT NULL " +
            "GROUP BY worker_id " +
            "ORDER BY worker_id"
        );
    }
    
    /**
     * Get job history
     * GET http://localhost:8080/api/monitor/jobs
     */
    @GetMapping("/jobs")
    public List<Map<String, Object>> getJobs(@RequestParam(defaultValue = "10") int limit) {
        return coordJdbcTemplate.queryForList(
            "SELECT TOP " + limit + " " +
            "    job_id, " +
            "    status, " +
            "    started_at, " +
            "    completed_at, " +
            "    total_records, " +
            "    duration_seconds, " +
            "    error_message " +
            "FROM transfer_jobs " +
            "ORDER BY job_id DESC"
        );
    }
    
    /**
     * Reset stuck partitions (admin endpoint)
     * POST http://localhost:8080/api/monitor/reset
     */
    @PostMapping("/reset")
    public Map<String, Object> resetPartitions() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // Reset partitions that have been processing for > 30 minutes
            int resetCount = coordJdbcTemplate.update(
                "UPDATE transfer_partitions " +
                "SET status = 'PENDING', " +
                "    worker_id = NULL, " +
                "    started_at = NULL, " +
                "    updated_at = GETDATE() " +
                "WHERE status = 'PROCESSING' " +
                "AND DATEDIFF(MINUTE, started_at, GETDATE()) > 30"
            );
            
            result.put("reset_count", resetCount);
            result.put("message", "Reset " + resetCount + " stuck partitions");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }
}
