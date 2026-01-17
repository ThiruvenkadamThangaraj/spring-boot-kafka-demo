package com.example.transfer.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class CoordinatorService {
    
    private static final Logger log = LoggerFactory.getLogger(CoordinatorService.class);
    private static final int WORKER_COUNT = 10;
    private static final int TIMEOUT_MINUTES = 15;
    
    @Autowired
    private JdbcTemplate itmJdbcTemplate;
    
    @Autowired
    private JdbcTemplate coJdbcTemplate;
    
    @Autowired
    private JdbcTemplate coordJdbcTemplate;
    
    private ExecutorService executorService;
    private volatile boolean isRunning = false;
    
    public CoordinatorService() {
        // Create thread pool for 10 workers
        this.executorService = Executors.newFixedThreadPool(
            WORKER_COUNT,
            new ThreadFactory() {
                private int counter = 1;
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "Worker-" + counter++);
                    t.setDaemon(false);
                    return t;
                }
            }
        );
    }
    
    /**
     * ⭐ MAIN ENTRY POINT - Runs daily at 2 AM automatically
     * This replaces PowerShell scripts!
     * Cron format: second minute hour day month day-of-week
     */
    @Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM (6 fields required by Spring)
    public void runDailyTransfer() {
        if (isRunning) {
            log.warn("Transfer already in progress, skipping this run");
            return;
        }
        
        isRunning = true;
        log.info("════════════════════════════════════════════════");
        log.info("  Daily Transfer Started at {}", LocalDateTime.now());
        log.info("════════════════════════════════════════════════");
        
        Long jobId = null;
        long startTime = System.currentTimeMillis();
        
        try {
            // Step 1: Create job entry
            jobId = coordJdbcTemplate.queryForObject(
                "INSERT INTO transfer_jobs (status, started_at) " +
                "VALUES ('PENDING', GETDATE()); " +
                "SELECT SCOPE_IDENTITY()",
                Long.class
            );
            log.info("Created job ID: {}", jobId);
            
            // Step 2: Reset partitions to PENDING
            coordJdbcTemplate.update(
                "UPDATE transfer_partitions " +
                "SET status = 'PENDING', worker_id = NULL, " +
                "started_at = NULL, completed_at = NULL, " +
                "records_transferred = 0, updated_at = GETDATE()"
            );
            log.info("Reset all partitions to PENDING");
            
            // Step 3: Launch 10 worker threads in parallel using CompletableFuture
            List<CompletableFuture<PartitionResult>> futures = new ArrayList<>();
            for (int i = 1; i <= WORKER_COUNT; i++) {
                final int workerId = i;
                CompletableFuture<PartitionResult> future = CompletableFuture.supplyAsync(
                    () -> processPartitionsForWorker(workerId),
                    executorService
                );
                futures.add(future);
            }
            log.info("Started {} worker threads", WORKER_COUNT);
            
            // Step 4: Wait for all workers to complete (with timeout)
            // CompletableFuture.allOf is more efficient than waiting one by one
            CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
            );
            
            try {
                allFutures.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("Transfer timed out after {} minutes", TIMEOUT_MINUTES);
                // Cancel all pending futures
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Transfer timeout", e);
            }
            
            // Step 5: Collect results from completed futures
            List<PartitionResult> results = new ArrayList<>();
            for (CompletableFuture<PartitionResult> future : futures) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    log.error("Error getting worker result: {}", e.getMessage());
                }
            }
            
            // Step 6: Calculate totals
            long totalRecords = results.stream()
                .mapToLong(r -> r.recordsTransferred)
                .sum();
            
            int totalPartitions = results.stream()
                .mapToInt(r -> r.partitionsProcessed)
                .sum();
            
            long durationMs = System.currentTimeMillis() - startTime;
            double durationMin = durationMs / 60000.0;
            
            // Log worker completion
            for (int i = 0; i < results.size(); i++) {
                PartitionResult result = results.get(i);
                log.info("Worker {} finished - {} partitions, {} records", 
                    i + 1, result.partitionsProcessed, result.recordsTransferred);
            }
            
            // Step 7: Validate results
            Long sourceCount = itmJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ITM.bam_table " +
                "WHERE app_id BETWEEN 1001 AND 4500",
                Long.class
            );
            
            Long targetCount = coJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CO.entitlement_table",
                Long.class
            );
            
            log.info("Validation - Source: {}, Target: {}", sourceCount, targetCount);
            
            // Step 8: Update job status
            coordJdbcTemplate.update(
                "UPDATE transfer_jobs " +
                "SET status = 'COMPLETED', completed_at = GETDATE(), " +
                "total_records = ?, duration_seconds = ? " +
                "WHERE job_id = ?",
                totalRecords, durationMs / 1000, jobId
            );
            
            log.info("════════════════════════════════════════════════");
            log.info("  ✓ Transfer COMPLETED Successfully!");
            log.info("  Partitions: {}", totalPartitions);
            log.info("  Records: {:,}", totalRecords);
            log.info("  Duration: {:.2f} minutes", durationMin);
            log.info("  Rate: {:,.0f} records/sec", totalRecords / (durationMs / 1000.0));
            log.info("════════════════════════════════════════════════");
            
            // Optional: Send notification
            sendNotification("✅ Transfer Completed", 
                String.format("Transferred %,d records in %.2f minutes", totalRecords, durationMin));
            
        } catch (Exception e) {
            log.error("════════════════════════════════════════════════");
            log.error("  ✗ Transfer FAILED: {}", e.getMessage(), e);
            log.error("════════════════════════════════════════════════");
            
            // Update job as failed
            if (jobId != null) {
                coordJdbcTemplate.update(
                    "UPDATE transfer_jobs " +
                    "SET status = 'FAILED', completed_at = GETDATE(), " +
                    "error_message = ? WHERE job_id = ?",
                    e.getMessage(), jobId
                );
            }
            
            // Send failure notification
            sendNotification("⚠️ Transfer FAILED", "Error: " + e.getMessage());
            
        } finally {
            isRunning = false;
        }
    }
    
    /**
     * Worker thread - processes partitions until none left
     * Each of the 10 threads runs this method
     */
    private PartitionResult processPartitionsForWorker(int workerId) {
        log.info("Worker {} started", workerId);
        long totalRecords = 0;
        int partitionsProcessed = 0;
        
        while (true) {
            try {
                // Claim next available partition (thread-safe with database lock)
                Map<String, Object> partition = claimNextPartition(workerId);
                
                if (partition == null) {
                    log.info("Worker {} - No more partitions available", workerId);
                    break;
                }
                
                Long partitionId = ((Number) partition.get("partition_id")).longValue();
                Integer appIdStart = (Integer) partition.get("app_id_start");
                Integer appIdEnd = (Integer) partition.get("app_id_end");
                
                log.info("Worker {} claimed partition {} (app_id {}-{})", 
                    workerId, partitionId, appIdStart, appIdEnd);
                
                // Fetch data from source
                List<String> entitlements = itmJdbcTemplate.query(
                    "SELECT DISTINCT entitlement_name " +
                    "FROM ITM.bam_table WITH (NOLOCK) " +
                    "WHERE app_id >= ? AND app_id <= ?",
                    (rs, rowNum) -> rs.getString("entitlement_name"),
                    appIdStart, appIdEnd
                );
                
                log.info("Worker {} - Fetched {} records for partition {}", 
                    workerId, entitlements.size(), partitionId);
                
                // Get unique entitlements
                List<String> uniqueEntitlements = entitlements.stream()
                    .distinct()
                    .collect(Collectors.toList());
                
                // Insert into target in chunks of 50,000
                int chunkSize = 50000;
                int totalChunks = (int) Math.ceil((double) uniqueEntitlements.size() / chunkSize);
                
                for (int i = 0; i < uniqueEntitlements.size(); i += chunkSize) {
                    int chunkNum = (i / chunkSize) + 1;
                    List<String> chunk = uniqueEntitlements.subList(
                        i, Math.min(i + chunkSize, uniqueEntitlements.size())
                    );
                    
                    coJdbcTemplate.batchUpdate(
                        "MERGE CO.entitlement_table AS target " +
                        "USING (SELECT ? AS entitlement_name) AS source " +
                        "ON target.entitlement_name = source.entitlement_name " +
                        "WHEN NOT MATCHED THEN " +
                        "INSERT (entitlement_name) VALUES (source.entitlement_name);",
                        chunk,
                        chunk.size(),
                        (ps, entitlement) -> ps.setString(1, entitlement)
                    );
                    
                    if (chunkNum % 10 == 0) {
                        log.info("Worker {} - Partition {} progress: {}/{} chunks", 
                            workerId, partitionId, chunkNum, totalChunks);
                    }
                }
                
                // Mark partition as completed
                coordJdbcTemplate.update(
                    "UPDATE transfer_partitions " +
                    "SET status = 'COMPLETED', completed_at = GETDATE(), " +
                    "records_transferred = ?, updated_at = GETDATE() " +
                    "WHERE partition_id = ?",
                    uniqueEntitlements.size(), partitionId
                );
                
                totalRecords += uniqueEntitlements.size();
                partitionsProcessed++;
                
                log.info("Worker {} ✓ Completed partition {} - {:,} records", 
                    workerId, partitionId, uniqueEntitlements.size());
                
            } catch (Exception e) {
                log.error("Worker {} error: {}", workerId, e.getMessage(), e);
                // Worker continues to try next partition
            }
        }
        
        log.info("Worker {} FINISHED - {} partitions, {:,} records", 
            workerId, partitionsProcessed, totalRecords);
        
        return new PartitionResult(partitionsProcessed, totalRecords);
    }
    
    /**
     * Thread-safe partition claiming using database row locks
     * Only ONE thread can claim a partition at a time
     */
    private Map<String, Object> claimNextPartition(int workerId) {
        try {
            return coordJdbcTemplate.queryForMap(
                "WITH NextPartition AS ( " +
                "    SELECT TOP 1 partition_id, app_id_start, app_id_end " +
                "    FROM transfer_partitions WITH (UPDLOCK, ROWLOCK) " +
                "    WHERE status = 'PENDING' " +
                "    ORDER BY partition_id " +
                ") " +
                "UPDATE transfer_partitions " +
                "SET status = 'PROCESSING', " +
                "    worker_id = ?, " +
                "    started_at = GETDATE(), " +
                "    updated_at = GETDATE() " +
                "OUTPUT inserted.partition_id, inserted.app_id_start, inserted.app_id_end " +
                "WHERE partition_id = (SELECT partition_id FROM NextPartition)",
                workerId
            );
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;  // No more partitions available
        }
    }
    
    /**
     * Send notification (implement your preferred method)
     */
    private void sendNotification(String subject, String message) {
        // TODO: Implement notification logic
        // Options:
        // 1. Email using JavaMailSender
        // 2. Slack webhook
        // 3. Teams webhook
        // 4. SMS via Twilio
        log.info("Notification: {} - {}", subject, message);
    }
    
    /**
     * Manual trigger for testing (call via REST endpoint)
     */
    public void manualTrigger() {
        log.info("Manual trigger requested");
        runDailyTransfer();
    }
    
    /**
     * Graceful shutdown
     */
    @PreDestroy
    public void cleanup() {
        log.info("Shutting down coordinator service...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Result class to track worker progress
     */
    private static class PartitionResult {
        final int partitionsProcessed;
        final long recordsTransferred;
        
        PartitionResult(int partitionsProcessed, long recordsTransferred) {
            this.partitionsProcessed = partitionsProcessed;
            this.recordsTransferred = recordsTransferred;
        }
    }
}
