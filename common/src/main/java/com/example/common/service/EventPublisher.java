package com.example.common.service;

import com.example.common.event.UserCreatedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {

    private static final String USER_CREATED_TOPIC = "user-created-events";

    @Autowired
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @Async
    public void publishUserCreatedEvent(UserCreatedEvent event) {
        kafkaTemplate.send(USER_CREATED_TOPIC, event.getUsername(), event);
        System.out.println("📤 Event published asynchronously: " + event.getUsername());
    }
}
