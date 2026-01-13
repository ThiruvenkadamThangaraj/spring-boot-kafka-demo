package com.example.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configuration for separate thread pools for IO and CPU-intensive tasks.
 * Follows Java concurrency best practices and international coding standards.
 * 
 * <p>Thread Pool Strategy:
 * <ul>
 *   <li><b>IO Tasks:</b> Database operations, Kafka publishing, network calls, file I/O</li>
 *   <li><b>CPU Tasks:</b> Data processing, calculations, transformations, business logic</li>
 * </ul>
 * 
 * @author System
 * @version 1.0
 * @since 2026-01-13
 */
@Configuration
@EnableAsync
public class AsyncExecutorConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncExecutorConfig.class);
    
    // Thread Pool Configuration Constants
    private static final int IO_CORE_POOL_SIZE = 20;
    private static final int IO_MAX_POOL_SIZE = 50;
    private static final int IO_QUEUE_CAPACITY = 1000;
    private static final String IO_THREAD_PREFIX = "IO-Task-";
    
    private static final int CPU_QUEUE_CAPACITY = 500;
    private static final String CPU_THREAD_PREFIX = "CPU-Task-";
    
    private static final int DEFAULT_CORE_POOL_SIZE = 10;
    private static final int DEFAULT_MAX_POOL_SIZE = 20;
    private static final int DEFAULT_QUEUE_CAPACITY = 500;
    private static final String DEFAULT_THREAD_PREFIX = "Async-Task-";
    
    private static final int SHUTDOWN_AWAIT_TERMINATION_SECONDS = 60;

    /**
     * Creates a thread pool executor for I/O-bound tasks.
     * 
     * <p>I/O-bound tasks include:
     * <ul>
     *   <li>Database queries and transactions</li>
     *   <li>Kafka message publishing</li>
     *   <li>External API calls</li>
     *   <li>File system operations</li>
     * </ul>
     * 
     * <p>Higher thread count is used as threads will be mostly in waiting state.
     * 
     * @return configured executor for I/O tasks
     * @see ThreadPoolTaskExecutor
     */
    @Bean(name = "ioTaskExecutor")
    public Executor ioTaskExecutor() {
        logger.info("Initializing I/O Task Executor with core={}, max={}, queue={}", 
            IO_CORE_POOL_SIZE, IO_MAX_POOL_SIZE, IO_QUEUE_CAPACITY);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(IO_CORE_POOL_SIZE);
        executor.setMaxPoolSize(IO_MAX_POOL_SIZE);
        executor.setQueueCapacity(IO_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(IO_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        
        logger.info("I/O Task Executor initialized successfully");
        return executor;
    }

    /**
     * Creates a thread pool executor for CPU-intensive tasks.
     * 
     * <p>CPU-intensive tasks include:
     * <ul>
     *   <li>Data transformation and mapping</li>
     *   <li>Business logic calculations</li>
     *   <li>Data aggregation and processing</li>
     *   <li>Algorithm execution</li>
     * </ul>
     * 
     * <p>Thread count is based on available processors for optimal CPU utilization.
     * Core pool size equals processor count, max is double to handle bursts.
     * 
     * @return configured executor for CPU-intensive tasks
     * @see ThreadPoolTaskExecutor
     * @see Runtime#availableProcessors()
     */
    @Bean(name = "cpuTaskExecutor")
    public Executor cpuTaskExecutor() {
        int processors = Runtime.getRuntime().availableProcessors();
        logger.info("Initializing CPU Task Executor with processors={}, core={}, max={}, queue={}", 
            processors, processors, processors * 2, CPU_QUEUE_CAPACITY);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(processors);
        executor.setMaxPoolSize(processors * 2);
        executor.setQueueCapacity(CPU_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(CPU_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        
        logger.info("CPU Task Executor initialized successfully");
        return executor;
    }

    /**
     * Creates a default thread pool executor for general asynchronous tasks.
     * 
     * <p>This executor is used for tasks that don't clearly fit into
     * I/O or CPU categories, or for general @Async annotated methods.
     * 
     * @return configured executor for general async tasks
     * @see org.springframework.scheduling.annotation.Async
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        logger.info("Initializing Default Task Executor with core={}, max={}, queue={}", 
            DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(DEFAULT_CORE_POOL_SIZE);
        executor.setMaxPoolSize(DEFAULT_MAX_POOL_SIZE);
        executor.setQueueCapacity(DEFAULT_QUEUE_CAPACITY);
        executor.setThreadNamePrefix(DEFAULT_THREAD_PREFIX);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(SHUTDOWN_AWAIT_TERMINATION_SECONDS);
        executor.initialize();
        
        logger.info("Default Task Executor initialized successfully");
        return executor;
    }
}
