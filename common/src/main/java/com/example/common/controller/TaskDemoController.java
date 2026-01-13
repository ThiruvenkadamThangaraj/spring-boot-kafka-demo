package com.example.common.controller;

import com.example.common.dto.ApiResponse;
import com.example.common.service.TaskSeparationDemoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Demo Controller to test IO vs CPU task separation with CompletableFuture
 * 
 * Test endpoints:
 * - GET /api/demo/io-task - Pure IO task example
 * - GET /api/demo/cpu-task - Pure CPU task example  
 * - GET /api/demo/workflow - Complete workflow (IO → CPU → IO)
 * - GET /api/demo/parallel-io - Parallel IO operations
 * - GET /api/demo/fan-out - Fan-out pattern
 * - GET /api/demo/pipeline - Complex pipeline
 */
@RestController
@RequestMapping("/api/demo")
public class TaskDemoController {

    @Autowired(required = false)
    private TaskSeparationDemoService demoService;

    /**
     * Test pure IO task - fetching from database
     */
    @GetMapping("/io-task")
    public CompletableFuture<ResponseEntity<ApiResponse<List<String>>>> testIOTask() {
        return demoService.fetchDataFromDatabase()
            .thenApply(data -> 
                ResponseEntity.ok(ApiResponse.success("IO Task completed", data))
            );
    }

    /**
     * Test pure CPU task - data processing
     * Note: This expects input data, so we fetch first
     */
    @GetMapping("/cpu-task")
    public CompletableFuture<ResponseEntity<ApiResponse<List<String>>>> testCPUTask() {
        return demoService.fetchDataFromDatabase()
            .thenCompose(data -> demoService.processDataWithCPU(data))
            .thenApply(processed -> 
                ResponseEntity.ok(ApiResponse.success("CPU Task completed", processed))
            );
    }

    /**
     * Test complete workflow: IO → CPU → IO
     */
    @GetMapping("/workflow")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> testCompleteWorkflow() {
        return demoService.completeWorkflow()
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Workflow completed successfully", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Workflow failed: " + ex.getMessage()))
            );
    }

    /**
     * Test parallel IO operations followed by CPU aggregation
     */
    @GetMapping("/parallel-io")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> testParallelIO() {
        return demoService.parallelIOWithCPU()
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Parallel IO completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Parallel IO failed: " + ex.getMessage()))
            );
    }

    /**
     * Test fan-out pattern: 1 IO → Multiple CPU tasks
     */
    @GetMapping("/fan-out")
    public CompletableFuture<ResponseEntity<ApiResponse<List<String>>>> testFanOut() {
        return demoService.fanOutPattern()
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Fan-out completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Fan-out failed: " + ex.getMessage()))
            );
    }

    /**
     * Test complex pipeline with multiple IO and CPU tasks
     */
    @GetMapping("/pipeline")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> testComplexPipeline() {
        return demoService.complexPipeline()
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Pipeline completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Pipeline failed: " + ex.getMessage()))
            );
    }

    /**
     * Test error handling in async operations
     */
    @GetMapping("/error-handling")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> testErrorHandling() {
        return demoService.errorHandlingExample()
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Error handling test completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error handling failed: " + ex.getMessage()))
            );
    }

    /**
     * Test external API call (IO task)
     */
    @GetMapping("/api-call")
    public CompletableFuture<ResponseEntity<ApiResponse<String>>> testAPICall(
            @RequestParam(defaultValue = "/api/test") String endpoint) {
        return demoService.callExternalAPI(endpoint)
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("API call completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("API call failed: " + ex.getMessage()))
            );
    }

    /**
     * Test data aggregation (CPU task)
     */
    @GetMapping("/aggregate")
    public CompletableFuture<ResponseEntity<ApiResponse<Integer>>> testAggregation() {
        return demoService.fetchDataFromDatabase()
            .thenCompose(data -> demoService.aggregateData(data))
            .thenApply(result -> 
                ResponseEntity.ok(ApiResponse.success("Aggregation completed", result))
            )
            .exceptionally(ex -> 
                ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Aggregation failed: " + ex.getMessage()))
            );
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<String>> health() {
        if (demoService == null) {
            return ResponseEntity.status(503)
                .body(ApiResponse.error("Demo Service not available"));
        }
        return ResponseEntity.ok(ApiResponse.success("Demo Service is running", "OK"));
    }

    /**
     * Info endpoint explaining the demo
     */
    @GetMapping("/info")
    public ResponseEntity<ApiResponse<TaskInfo>> info() {
        TaskInfo info = new TaskInfo();
        info.description = "CompletableFuture Task Separation Demo";
        info.ioTaskExecutor = "ioTaskExecutor - For I/O operations (DB, Network, File)";
        info.cpuTaskExecutor = "cpuTaskExecutor - For CPU-intensive operations (Processing, Calculations)";
        info.endpoints = List.of(
            "/api/demo/io-task - Test pure IO task",
            "/api/demo/cpu-task - Test pure CPU task",
            "/api/demo/workflow - Test complete workflow (IO → CPU → IO)",
            "/api/demo/parallel-io - Test parallel IO operations",
            "/api/demo/fan-out - Test fan-out pattern (1 IO → Multiple CPU)",
            "/api/demo/pipeline - Test complex pipeline",
            "/api/demo/error-handling - Test error handling",
            "/api/demo/api-call - Test external API call",
            "/api/demo/aggregate - Test data aggregation"
        );
        
        return ResponseEntity.ok(ApiResponse.success("Demo Info", info));
    }

    /**
     * Task info response model
     */
    public static class TaskInfo {
        public String description;
        public String ioTaskExecutor;
        public String cpuTaskExecutor;
        public List<String> endpoints;
    }
}
