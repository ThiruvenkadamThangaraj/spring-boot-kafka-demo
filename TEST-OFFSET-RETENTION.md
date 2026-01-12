# Kafka Offset Retention Test

## How Consumer Offsets Work

When you stop a consumer and restart it, Kafka remembers the last committed offset for the consumer group (`email-service-group`). This ensures no messages are lost or re-processed (unless you explicitly reset).

## Current Configuration

All services use the same consumer group and auto-commit:

```yaml
spring:
  kafka:
    consumer:
      group-id: email-service-group
      enable-auto-commit: true  # Default
      auto-commit-interval-ms: 5000  # Commits every 5 seconds
```

## Test Offset Retention

### Step 1: Create some users (generates Kafka events)
```powershell
.\create-test-load.ps1 -UserCount 100
```

### Step 2: Stop ONE service (e.g., Evaluation Service)
- Close the "Evaluation Service" PowerShell window
- Other 4 services continue consuming events

### Step 3: Create more users while service is stopped
```powershell
.\create-test-load.ps1 -UserCount 50
```

### Step 4: Check Kafka consumer lag
```powershell
cd C:\kafka
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --describe
```

**Output shows:**
- `CURRENT-OFFSET`: Last committed offset
- `LOG-END-OFFSET`: Latest message in topic
- `LAG`: Messages behind (accumulated while stopped)

### Step 5: Restart the stopped service
```powershell
cd C:\Users\thiru_qoss8b1\Downloads\demo\demo\evaluation-service
mvn spring-boot:run
```

### Step 6: Verify catch-up
The restarted service will:
1. Read last committed offset from Kafka
2. Process all missed messages (catch up on LAG)
3. You'll see email notifications for all missed events in console

## Enhanced Configuration Options

### Option 1: Manual Commit (More Control)
```yaml
spring:
  kafka:
    consumer:
      enable-auto-commit: false  # Disable auto-commit
```

Then in consumer code:
```java
@KafkaListener(topics = "user-created-events", groupId = "email-service-group")
public void handleUserCreatedEvent(
    UserCreatedEvent event,
    Acknowledgment acknowledgment) {  // Add acknowledgment parameter
    
    try {
        emailService.sendWelcomeEmail(
            event.getEmail(),
            event.getFirstName(),
            event.getLastName(),
            event.getServiceName()
        );
        
        // Manually commit offset after successful processing
        acknowledgment.acknowledge();
        
    } catch (Exception e) {
        log.error("Failed to send email: {}", e.getMessage());
        // Don't commit - will retry on restart
    }
}
```

### Option 2: Commit on Success Only
```java
@KafkaListener(
    topics = "user-created-events",
    groupId = "email-service-group",
    containerFactory = "kafkaListenerContainerFactory"
)
public void handleUserCreatedEvent(UserCreatedEvent event) {
    emailService.sendWelcomeEmail(...);
    // Auto-commit happens only if no exception thrown
}
```

### Option 3: Batch Processing with Manual Commit
```java
@KafkaListener(topics = "user-created-events", groupId = "email-service-group")
public void handleUserCreatedEventBatch(
    List<UserCreatedEvent> events,
    Acknowledgment acknowledgment) {
    
    for (UserCreatedEvent event : events) {
        emailService.sendWelcomeEmail(...);
    }
    
    // Commit after entire batch processed
    acknowledgment.acknowledge();
}
```

## View Consumer Group Offsets

### Check all consumer groups:
```powershell
cd C:\kafka
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --list
```

### Check specific group details:
```powershell
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --describe
```

**Output:**
```
GROUP                  TOPIC                PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
email-service-group    user-created-events  0          5234            5234            0
email-service-group    user-created-events  1          5189            5189            0
email-service-group    user-created-events  2          5276            5276            0
...
```

- **LAG = 0**: All caught up
- **LAG > 0**: Behind, processing backlog

## Reset Offsets (Start Fresh)

### Reset to earliest (reprocess all messages):
```powershell
# Stop all consumers first!
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --reset-offsets --to-earliest --topic user-created-events --execute
```

### Reset to latest (skip all accumulated messages):
```powershell
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --reset-offsets --to-latest --topic user-created-events --execute
```

### Reset to specific offset:
```powershell
.\bin\windows\kafka-consumer-groups.bat --bootstrap-server localhost:9092 --group email-service-group --reset-offsets --to-offset 1000 --topic user-created-events:0 --execute
```

## Best Practices

1. **Use consistent group IDs** - Same group shares offsets
2. **Enable auto-commit for simple cases** - Default works well
3. **Use manual commit for critical processing** - Ensures at-least-once delivery
4. **Monitor consumer lag** - Detect processing bottlenecks
5. **Set appropriate retention** - Keep messages long enough for recovery

## Kafka Message Retention

Current default: 7 days

Configure in `C:\kafka\config\server.properties`:
```properties
# Keep messages for 7 days
log.retention.hours=168

# Or by size (1GB per partition)
log.retention.bytes=1073741824
```

## Summary

✅ Your current setup retains offsets automatically
✅ Consumers resume from last committed position on restart
✅ No messages lost (within retention period)
✅ All 5 services share same consumer group - load balanced across partitions
✅ Each partition consumed by only one consumer at a time

**To verify it's working:** Stop a service, create users, restart service, watch it catch up!
