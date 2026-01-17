package com.example.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Semaphore;

/**
 * PHASE 1 FIX: HTTP Client Configuration with Strict Timeouts
 * 
 * Problem: Threads hanging indefinitely waiting for slow downstream services
 * Solution: Apply aggressive timeouts and concurrency limits
 * 
 * Benefits:
 * - Threads fail fast (5 seconds) instead of hanging (30+ seconds)
 * - Connection pool doesn't exhaust
 * - Only 10 concurrent downstream calls allowed (prevents overwhelming)
 * 
 * @author Your Name
 * @date January 15, 2026
 */
@Configuration
@Slf4j
public class HttpClientConfig {
    
    @Value("${http.client.connect-timeout:2000}")
    private int connectTimeout;  // 2 seconds to establish TCP connection
    
    @Value("${http.client.read-timeout:5000}")
    private int readTimeout;     // 5 seconds to receive response data
    
    @Value("${http.client.connection-request-timeout:3000}")
    private int connectionRequestTimeout;  // 3 seconds to get connection from pool
    
    @Value("${http.client.max-connections:50}")
    private int maxConnections;  // Total connections in pool
    
    @Value("${http.client.max-per-route:20}")
    private int maxPerRoute;     // Max connections per destination
    
    /**
     * RestTemplate bean with Apache HttpClient 5.x
     * Configured with aggressive timeouts to prevent thread starvation
     */
    @Bean
    public RestTemplate restTemplate() {
        log.info("Initializing RestTemplate with timeouts: connect={}ms, read={}ms", 
                 connectTimeout, readTimeout);
        
        // Step 1: Configure connection pooling
        PoolingHttpClientConnectionManager connectionManager = 
            new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxPerRoute);
        
        // Step 2: Set connection-level timeouts
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
            .setSocketTimeout(Timeout.ofMilliseconds(readTimeout))
            .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        
        // Step 3: Configure request-level timeouts
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
            .build();
        
        // Step 4: Build HTTP client with all timeout configurations
        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .evictIdleConnections(Timeout.ofSeconds(30))  // Close idle connections after 30s
            .build();
        
        // Step 5: Create RestTemplate with configured HTTP client
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory(httpClient);
        
        RestTemplate restTemplate = new RestTemplate(factory);
        
        // Step 6: Add custom error handler for better logging
        restTemplate.setErrorHandler(new TimeoutAwareErrorHandler());
        
        log.info("RestTemplate initialized successfully with connection pool: max={}, maxPerRoute={}", 
                 maxConnections, maxPerRoute);
        
        return restTemplate;
    }
    
    /**
     * Semaphore to limit concurrent calls to downstream services
     * 
     * Prevents overwhelming slow services with too many requests
     * Max 10 concurrent calls - 11th request waits or gets rejected
     * 
     * Usage in service:
     * <pre>
     * boolean acquired = downstreamConcurrencyLimit.tryAcquire(1, TimeUnit.SECONDS);
     * if (!acquired) {
     *     throw new ServiceUnavailableException("Too busy");
     * }
     * try {
     *     // Make downstream call
     * } finally {
     *     downstreamConcurrencyLimit.release();
     * }
     * </pre>
     */
    @Bean
    public Semaphore downstreamConcurrencyLimit() {
        int maxConcurrent = 10;
        log.info("Initializing downstream concurrency limit: {} concurrent calls", maxConcurrent);
        return new Semaphore(maxConcurrent);
    }
}
