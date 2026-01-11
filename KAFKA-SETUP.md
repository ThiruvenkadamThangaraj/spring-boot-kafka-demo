# Kafka Event-Driven Email Notification

## Overview
The microservices architecture now includes Kafka event-driven email notifications. When a user is created in any service, a Kafka event is published and consumed to send a welcome email.

## Architecture

### Event Flow
1. User creates a POST request to any service (e.g., `POST http://localhost:8081/api/users`)
2. Service saves the user to its database
3. Service publishes `UserCreatedEvent` to Kafka topic `user-created-events`
4. `UserCreatedEventConsumer` (in all services) listens to the topic
5. Consumer triggers `EmailService` to send welcome email
6. Email details are logged to console

### Components

#### 1. UserCreatedEvent
- Location: `common/src/main/java/com/example/common/event/UserCreatedEvent.java`
- Contains: userId, username, email, firstName, lastName, department, salary, serviceName, createdAt

#### 2. EventPublisher
- Location: `common/src/main/java/com/example/common/service/EventPublisher.java`
- Publishes events to Kafka topic `user-created-events`
- Used by all service UserService classes

#### 3. UserCreatedEventConsumer
- Location: `common/src/main/java/com/example/common/consumer/UserCreatedEventConsumer.java`
- Listens to `user-created-events` topic
- Group ID: `email-service-group`
- Triggers EmailService

#### 4. EmailService
- Location: `common/src/main/java/com/example/common/service/EmailService.java`
- Simulates sending welcome email
- Logs email details to console (replace with actual email service in production)

## Kafka Configuration

### Prerequisites
You need Apache Kafka running on `localhost:9092`. 

#### Quick Start with Kafka:
```powershell
# Download Kafka from https://kafka.apache.org/downloads
# Extract and navigate to Kafka directory

# Start Zookeeper
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties

# Start Kafka (in new terminal)
.\bin\windows\kafka-server-start.bat .\config\server.properties
```

### Configuration (application.yaml)
```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      group-id: email-service-group
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
```

## Testing

### 1. Start Kafka
```powershell
# Terminal 1: Start Zookeeper
.\bin\windows\zookeeper-server-start.bat .\config\zookeeper.properties

# Terminal 2: Start Kafka
.\bin\windows\kafka-server-start.bat .\config\server.properties
```

### 2. Start Services
```powershell
.\start-without-docker.ps1
```

### 3. Create a User
```bash
curl -X POST http://localhost:8081/api/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john.doe",
    "email": "john.doe@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "+1234567890",
    "department": "Engineering",
    "salary": 75000.00
  }'
```

### 4. Observe Email Notification
Check the console output of all running services. You'll see:
```
📨 Received User Created Event: john.doe from evaluation-service
================================================================================
📧 EMAIL SENT TO: john.doe@example.com
Subject: Welcome to evaluation-service
Body: Hello John Doe,
      Your account has been successfully created in evaluation-service!
      Thank you for joining us.
================================================================================
```

## Production Considerations

### Replace Console Email with Real Email Service
Update [EmailService.java](common/src/main/java/com/example/common/service/EmailService.java):

```java
// Add dependency: spring-boot-starter-mail
@Autowired
private JavaMailSender mailSender;

public void sendWelcomeEmail(String email, String firstName, String lastName, String serviceName) {
    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true);
    
    helper.setTo(email);
    helper.setSubject("Welcome to " + serviceName);
    helper.setText("Hello " + firstName + " " + lastName + ",\n\n" +
                   "Your account has been successfully created!");
    
    mailSender.send(message);
}
```

### Email Service Configuration
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-email@gmail.com
    password: your-app-password
    properties:
      mail.smtp.auth: true
      mail.smtp.starttls.enable: true
```

## Event Topics

| Topic Name | Purpose | Producer | Consumer |
|------------|---------|----------|----------|
| `user-created-events` | User creation notifications | All 5 services | All 5 services (email-service-group) |

## Benefits

1. **Decoupling**: User creation and email sending are decoupled via events
2. **Scalability**: Multiple consumers can process events in parallel
3. **Reliability**: Kafka ensures event delivery even if services are temporarily down
4. **Audit Trail**: All user creation events are stored in Kafka
5. **Flexibility**: Easy to add more event consumers (SMS, webhooks, analytics, etc.)

## Adding More Event Types

To add new events (e.g., UserUpdatedEvent, UserDeletedEvent):

1. Create event class in `common/src/main/java/com/example/common/event/`
2. Publish event in service layer
3. Create consumer in `common/src/main/java/com/example/common/consumer/`
4. Add Kafka listener method with appropriate topic

Example:
```java
@KafkaListener(topics = "user-updated-events", groupId = "notification-group")
public void handleUserUpdatedEvent(UserUpdatedEvent event) {
    // Handle event
}
```
