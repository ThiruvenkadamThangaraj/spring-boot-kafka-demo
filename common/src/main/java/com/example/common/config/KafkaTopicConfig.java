package com.example.common.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    /**
     * Configure topic with multiple partitions for high throughput.
     * For millions of records per second:
     * - More partitions = more parallelism
     * - Each partition can handle ~10-100K messages/sec depending on message size
     * - Scale consumers to match partition count
     */
    @Bean
    public NewTopic userCreatedTopic() {
        return TopicBuilder.name("user-created-events")
                .partitions(12)        // 12 partitions for parallel processing
                .replicas(1)           // 1 replica (increase for fault tolerance in production)
                .build();
    }
}
