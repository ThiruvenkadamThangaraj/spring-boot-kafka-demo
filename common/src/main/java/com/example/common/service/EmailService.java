package com.example.common.service;

import org.springframework.stereotype.Service;

@Service
public class EmailService {

    public void sendWelcomeEmail(String email, String firstName, String lastName, String serviceName) {
        // Simulating email sending
        System.out.println("=".repeat(80));
        System.out.println("📧 EMAIL SENT TO: " + email);
        System.out.println("Subject: Welcome to " + serviceName);
        System.out.println("Body: Hello " + firstName + " " + lastName + ",");
        System.out.println("      Your account has been successfully created in " + serviceName + "!");
        System.out.println("      Thank you for joining us.");
        System.out.println("=".repeat(80));
        
        // In production, use JavaMail or external email service:
        // mailSender.send(createMimeMessage(email, firstName, lastName, serviceName));
    }
}
