package com.example.common.consumer;

import com.example.common.event.UserCreatedEvent;
import com.example.common.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class UserCreatedEventConsumer {

    @Autowired
    private EmailService emailService;

    @KafkaListener(topics = "user-created-events", groupId = "email-service-group")
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        System.out.println("📨 Received User Created Event: " + event.getUsername() + " from " + event.getServiceName());
        
        // Send welcome email
        emailService.sendWelcomeEmail(
            event.getEmail(),
            event.getFirstName(),
            event.getLastName(),
            event.getServiceName()
        );
    }
}
