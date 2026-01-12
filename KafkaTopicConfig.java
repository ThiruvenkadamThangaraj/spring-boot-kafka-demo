package com.example.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Configure topic with partitions for high throughput.
     * 
     * Scaling Guidelines:
     * - 12 partitions: ~1M messages/sec (current)
     * - 24 partitions: ~2M messages/sec
     * - 48 partitions: ~5M messages/sec
     * 
     * Consumer Threads Should Match:
     * - 12 partitions → 12-15 consumer threads (current: 15)
     * - 24 partitions → 24-30 consumer threads
     * - 48 partitions → 48-60 consumer threads
     * 
     * To Scale:
     * 1. Increase partitions here
     * 2. Increase listener.concurrency in application.yaml
     * 3. Add more service instances (horizontal scaling)
     */
    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name("user-created-events")
                .partitions(24)        // Increase to 24 for 2M msgs/sec
                .replicas(1)           // Increase to 3 for fault tolerance in production
                .build();
    }
}
