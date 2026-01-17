package com.example.common.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * REST Client Configuration
 * Configures RestTemplate with timeouts for inter-service communication
 * 
 * @author DevOps Team
 * @version 1.0
 * @since 2026-01-13
 */
@Configuration
public class RestClientConfig {
    
    /**
     * RestTemplate bean with configured timeouts
     * 
     * Timeouts:
     * - Connection timeout: 3 seconds
     * - Read timeout: 5 seconds
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    /**
     * Alternative RestTemplate with custom request factory
     * Useful for more granular control
     */
    @Bean(name = "customRestTemplate")
    public RestTemplate customRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);  // 3 seconds
        factory.setReadTimeout(5000);     // 5 seconds
        
        RestTemplate restTemplate = new RestTemplate(factory);
        return restTemplate;
    }
}
