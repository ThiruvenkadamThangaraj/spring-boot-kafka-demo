package com.example.demo.service;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public ProducerService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendMessage(String topic, String key, String message) {
        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex != null) {
                System.err.println("Failed to send message: " + ex.getMessage());
            } else if (result != null) {
                System.out.println("Message sent: " + message + " to partition=" + result.getRecordMetadata().partition() + " offset=" + result.getRecordMetadata().offset());
            }
        });
    }
}
