# High Throughput Configuration - Millions of Records/Second

## Current Optimizations Implemented

### 1. **Kafka Topic Configuration**
- **12 Partitions** - Allows 12 parallel consumers
- Each partition can handle ~10-100K msgs/sec (depends on message size)
- Theoretical capacity: **1.2M - 12M messages/second**

### 2. **Producer Optimizations**
```yaml
batch-size: 32768              # 32KB batches (group multiple messages)
linger-ms: 10                  # Wait 10ms to accumulate more messages
compression-type: snappy       # Fast compression (less network I/O)
acks: 1                        # Wait only for leader (faster than acks=all)
buffer-memory: 67108864        # 64MB buffer for batching
```

**Impact**: Batching reduces network overhead by ~90%

### 3. **Consumer Optimizations**
```yaml
max-poll-records: 500          # Fetch 500 records at once
fetch-min-size: 1048576        # Wait for 1MB of data
fetch-max-wait: 500            # Or max 500ms
concurrency: 3                 # 3 consumer threads per service
```

**Impact**: 
- Fewer network calls
- 15 total consumers (5 services × 3 threads) across 12 partitions
- Processes records in batches of 500

### 4. **Asynchronous Publishing**
- `@Async` on EventPublisher
- Thread pool: 5-10 threads
- Non-blocking API responses

## Scaling to Millions of Records/Second

### Architecture Scaling

#### **Option 1: Horizontal Scaling (Recommended)**
```
Current: 1 Kafka broker + 5 service instances
Target:  3 Kafka brokers + 15 service instances (3 per service type)

Capacity: ~5-10M messages/second
```

**Steps:**
1. Add more Kafka brokers (3-5 brokers)
2. Increase partitions to 24-48
3. Scale each microservice to 3-5 instances
4. Use load balancer for API traffic

**Configuration:**
```yaml
spring:
  kafka:
    bootstrap-servers: kafka1:9092,kafka2:9093,kafka3:9094
```

#### **Option 2: Kafka Cluster + Kubernetes**
```
Kafka Cluster: 5-10 brokers
Microservices: Auto-scaling pods (2-10 instances each)
Partitions: 48-100

Capacity: ~20-50M messages/second
```

### Database Optimization

**Current**: H2 file-based (limited throughput)

**For High Volume:**

1. **PostgreSQL with Connection Pool**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
```

2. **Batch Inserts**
```java
@Transactional
public void saveUserBatch(List<User> users) {
    userRepository.saveAll(users);  // Batch insert
}
```

3. **Asynchronous Database Writes**
```java
@Async
public CompletableFuture<User> saveUserAsync(User user) {
    return CompletableFuture.completedFuture(userRepository.save(user));
}
```

### Kafka Broker Configuration

Edit `C:\kafka\config\server.properties`:

```properties
# Increase network threads
num.network.threads=8

# Increase I/O threads
num.io.threads=16

# Increase socket buffer sizes
socket.send.buffer.bytes=1048576
socket.receive.buffer.bytes=1048576

# Increase log segment size
log.segment.bytes=1073741824

# Enable compression
compression.type=snappy

# Increase replica fetcher threads
num.replica.fetchers=4
```

### JVM Tuning

**Kafka Broker:**
```bash
export KAFKA_HEAP_OPTS="-Xms4G -Xmx4G"
export KAFKA_JVM_PERFORMANCE_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=20"
```

**Microservices:**
```bash
java -Xms2G -Xmx2G -XX:+UseG1GC -jar service.jar
```

### Monitoring & Metrics

Add monitoring to track throughput:

**Dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**Metrics to monitor:**
- Kafka producer rate (messages/sec)
- Consumer lag (messages behind)
- API response time
- Database connection pool usage

### Load Testing

Use Apache JMeter or Gatling to test:

```bash
# Simulate 1M users created in 10 seconds
# = 100K requests/second
```

## Current Capacity Estimate

**With current setup (1 broker, 5 services, 12 partitions, 3 threads/service):**

| Component | Throughput |
|-----------|------------|
| Kafka Producer (batched) | ~100K msgs/sec per service |
| Total Publishing | ~500K msgs/sec (5 services) |
| Kafka Topic | ~1.2M msgs/sec (12 partitions × 100K) |
| Consumers | ~300K msgs/sec (15 threads × 20K) |
| **Bottleneck** | **Consumers: ~300K msgs/sec** |

**To reach millions/second:**
1. Scale to 10 service instances per type (50 total)
2. Add 2 more Kafka brokers
3. Increase partitions to 48
4. Result: **~2-5M messages/second**

## Quick Wins

1. ✅ Already implemented: Async publishing, batching, compression
2. ✅ 12 partitions for parallelism
3. ✅ 3 consumer threads per service
4. TODO: Add more service instances (horizontal scaling)
5. TODO: Add more Kafka brokers
6. TODO: Switch to PostgreSQL/MySQL for production
7. TODO: Implement database connection pooling
8. TODO: Add monitoring dashboards

## Testing Current Performance

```powershell
# Load test with 1000 concurrent requests
$jobs = 1..1000 | ForEach-Object {
    Start-Job -ScriptBlock {
        Invoke-RestMethod -Uri 'http://localhost:8081/api/users' `
            -Method Post -ContentType 'application/json' `
            -Body "{\"username\":\"user$_\",\"email\":\"user$_@example.com\",\"firstName\":\"User\",\"lastName\":\"$_\",\"phoneNumber\":\"+123456$_\",\"department\":\"IT\",\"salary\":75000}"
    }
}
$jobs | Wait-Job | Receive-Job
```

Run `.\build-and-start.ps1` to apply the high-throughput configuration!
