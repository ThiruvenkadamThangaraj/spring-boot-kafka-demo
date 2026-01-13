package com.example.common.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Demonstration service showing clear separation of IO vs CPU tasks
 * with CompletableFuture
 */
@Service
public class TaskSeparationDemoService {

    @Autowired
    @Qualifier("ioTaskExecutor")
    private Executor ioExecutor;

    @Autowired
    @Qualifier("cpuTaskExecutor")
    private Executor cpuExecutor;

    /**
     * IO Task Example: Simulates database read
     */
    public CompletableFuture<List<String>> fetchDataFromDatabase() {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("🗄️ [IO Task] Fetching data from database on thread: " 
                + Thread.currentThread().getName());
            
            // Simulate DB I/O delay
            simulateIODelay(500);
            
            List<String> data = new ArrayList<>();
            for (int i = 1; i <= 100; i++) {
                data.add("Record-" + i);
            }
            
            System.out.println("✅ [IO Task] Database fetch completed");
            return data;
        }, ioExecutor);
    }

    /**
     * CPU Task Example: Processes data with heavy computation
     */
    public CompletableFuture<List<String>> processDataWithCPU(List<String> data) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("⚙️ [CPU Task] Processing data on thread: " 
                + Thread.currentThread().getName());
            
            // Simulate CPU-intensive work
            List<String> processed = data.stream()
                .map(record -> {
                    // Simulate complex calculation
                    simulateCPUWork();
                    return record.toUpperCase() + "-PROCESSED";
                })
                .collect(Collectors.toList());
            
            System.out.println("✅ [CPU Task] Data processing completed");
            return processed;
        }, cpuExecutor);
    }

    /**
     * IO Task Example: Simulates saving to database
     */
    public CompletableFuture<Void> saveDataToDatabase(List<String> data) {
        return CompletableFuture.runAsync(() -> {
            System.out.println("💾 [IO Task] Saving data to database on thread: " 
                + Thread.currentThread().getName());
            
            // Simulate DB I/O delay
            simulateIODelay(300);
            
            System.out.println("✅ [IO Task] Database save completed for " + data.size() + " records");
        }, ioExecutor);
    }

    /**
     * IO Task Example: Simulates external API call
     */
    public CompletableFuture<String> callExternalAPI(String endpoint) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("🌐 [IO Task] Calling external API: " + endpoint 
                + " on thread: " + Thread.currentThread().getName());
            
            // Simulate network I/O delay
            simulateIODelay(800);
            
            String response = "API Response from " + endpoint;
            System.out.println("✅ [IO Task] API call completed");
            return response;
        }, ioExecutor);
    }

    /**
     * CPU Task Example: Data transformation and aggregation
     */
    public CompletableFuture<Integer> aggregateData(List<String> data) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("📊 [CPU Task] Aggregating data on thread: " 
                + Thread.currentThread().getName());
            
            // Simulate CPU-intensive aggregation
            int result = data.stream()
                .map(String::length)
                .reduce(0, (a, b) -> {
                    simulateCPUWork();
                    return a + b;
                });
            
            System.out.println("✅ [CPU Task] Aggregation completed: " + result);
            return result;
        }, cpuExecutor);
    }

    /**
     * Combined workflow: IO → CPU → IO
     * Demonstrates proper task separation
     */
    public CompletableFuture<String> completeWorkflow() {
        System.out.println("\n🚀 Starting Complete Workflow (IO → CPU → IO)\n");
        
        return fetchDataFromDatabase()                    // Step 1: IO Task
            .thenComposeAsync(data -> {
                System.out.println("⏭️ Moving from IO to CPU task\n");
                return processDataWithCPU(data);         // Step 2: CPU Task
            })
            .thenComposeAsync(processedData -> {
                System.out.println("⏭️ Moving from CPU to IO task\n");
                return saveDataToDatabase(processedData)  // Step 3: IO Task
                    .thenApply(v -> "Workflow completed: " + processedData.size() + " records processed");
            });
    }

    /**
     * Parallel IO operations followed by CPU aggregation
     * Demonstrates parallel execution of IO tasks
     */
    public CompletableFuture<String> parallelIOWithCPU() {
        System.out.println("\n🚀 Starting Parallel IO Operations\n");
        
        // Execute multiple IO operations in parallel
        CompletableFuture<List<String>> dbFetch = fetchDataFromDatabase();
        CompletableFuture<String> api1 = callExternalAPI("/api/users");
        CompletableFuture<String> api2 = callExternalAPI("/api/orders");
        CompletableFuture<String> api3 = callExternalAPI("/api/products");

        // Wait for all IO operations to complete
        return CompletableFuture.allOf(dbFetch, api1, api2, api3)
            .thenApplyAsync(v -> {
                System.out.println("\n⏭️ All IO operations completed, starting CPU task\n");
                
                // CPU task: Aggregate all results
                List<String> dbData = dbFetch.join();
                String apiData = api1.join() + ", " + api2.join() + ", " + api3.join();
                
                // Simulate CPU-intensive aggregation
                simulateCPUWork();
                
                return String.format("Aggregated: DB records=%d, API data=%s", 
                    dbData.size(), apiData);
            }, cpuExecutor);
    }

    /**
     * Fan-out pattern: One IO task followed by multiple parallel CPU tasks
     */
    public CompletableFuture<List<String>> fanOutPattern() {
        System.out.println("\n🚀 Starting Fan-Out Pattern (1 IO → Multiple CPU)\n");
        
        return fetchDataFromDatabase()
            .thenApplyAsync(data -> {
                System.out.println("⏭️ Fanning out to multiple CPU tasks\n");
                
                // Split data into chunks for parallel CPU processing
                int chunkSize = 25;
                List<CompletableFuture<List<String>>> futures = new ArrayList<>();
                
                for (int i = 0; i < data.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, data.size());
                    List<String> chunk = data.subList(i, end);
                    
                    CompletableFuture<List<String>> future = CompletableFuture.supplyAsync(() -> {
                        System.out.println("⚙️ [CPU Task] Processing chunk on thread: " 
                            + Thread.currentThread().getName());
                        return chunk.stream()
                            .map(String::toUpperCase)
                            .collect(Collectors.toList());
                    }, cpuExecutor);
                    
                    futures.add(future);
                }
                
                // Wait for all CPU tasks and combine results
                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .flatMap(f -> f.join().stream())
                        .collect(Collectors.toList())
                    ).join();
            }, cpuExecutor);
    }

    /**
     * Complex pipeline: Multiple IO and CPU tasks in sequence
     */
    public CompletableFuture<String> complexPipeline() {
        System.out.println("\n🚀 Starting Complex Pipeline\n");
        
        return fetchDataFromDatabase()                     // IO 1
            .thenComposeAsync(data -> {
                System.out.println("⏭️ IO → CPU: Processing data\n");
                return processDataWithCPU(data);          // CPU 1
            })
            .thenComposeAsync(processed -> {
                System.out.println("⏭️ CPU → IO: Calling API\n");
                return callExternalAPI("/api/validate")   // IO 2
                    .thenApply(apiResponse -> new Object() {
                        List<String> data = processed;
                        String response = apiResponse;
                    });
            })
            .thenApplyAsync(result -> {
                System.out.println("⏭️ IO → CPU: Final aggregation\n");
                // CPU 2: Final processing
                simulateCPUWork();
                return "Pipeline completed: " + result.data.size() + " records, " + result.response;
            }, cpuExecutor)
            .thenComposeAsync(summary -> {
                System.out.println("⏭️ CPU → IO: Saving results\n");
                return CompletableFuture.runAsync(() -> {
                    // IO 3: Save results
                    simulateIODelay(200);
                    System.out.println("✅ Results saved");
                }, ioExecutor).thenApply(v -> summary);
            });
    }

    /**
     * Demonstrates error handling in async pipelines
     */
    public CompletableFuture<String> errorHandlingExample() {
        return fetchDataFromDatabase()
            .thenApplyAsync(data -> {
                if (data.isEmpty()) {
                    throw new RuntimeException("No data to process");
                }
                return data;
            }, cpuExecutor)
            .exceptionally(ex -> {
                System.err.println("❌ Error in pipeline: " + ex.getMessage());
                return List.of("DEFAULT-DATA");
            })
            .thenApply(data -> "Processed " + data.size() + " records");
    }

    // Helper methods
    private void simulateIODelay(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void simulateCPUWork() {
        // Simulate CPU-intensive work
        double result = 0;
        for (int i = 0; i < 10000; i++) {
            result += Math.sqrt(i) * Math.sin(i);
        }
    }
}
