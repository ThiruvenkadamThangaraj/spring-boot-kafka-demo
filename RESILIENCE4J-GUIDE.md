# Resilience4j Implementation Guide

## 🛡️ Overview
This project implements **Resilience4j** patterns for building fault-tolerant microservices:
- ⚡ **Circuit Breaker** - Fail fast and prevent cascading failures
- 🔄 **Retry** - Automatic retry on transient failures
- ⏱️ **Timeout (Time Limiter)** - Enforce operation timeouts
- 🚧 **Bulkhead** - Limit concurrent calls to prevent resource exhaustion

---

## 📦 Dependencies Added

```xml
<!-- Resilience4j Spring Boot 3 Starter -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Circuit Breaker -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-circuitbreaker</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Retry -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-retry</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Time Limiter (Timeout) -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-timelimiter</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Bulkhead -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-bulkhead</artifactId>
    <version>2.1.0</version>
</dependency>

<!-- Spring AOP (Required for annotations) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

---

## ⚡ Circuit Breaker

### What It Does
Protects your application from cascading failures by failing fast when a service is down.

### States
1. **CLOSED** (Normal) - Calls pass through, failures are counted
2. **OPEN** (Failed) - All calls fail immediately, fallback is executed
3. **HALF_OPEN** (Testing) - Limited calls allowed to test recovery

### Configuration
```yaml
resilience4j:
  circuitbreaker:
    instances:
      samplingService:
        slidingWindowSize: 10              # Last 10 calls
        minimumNumberOfCalls: 5            # Need 5 calls before calculating failure rate
        failureRateThreshold: 50           # Open circuit if >50% fail
        waitDurationInOpenState: 5s        # Wait 5s before trying HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3  # Test with 3 calls
```

### Usage Example
```java
@CircuitBreaker(name = "samplingService", fallbackMethod = "getSamplingDataFallback")
public Map<String, Object> getSamplingData(Long id) {
    // Call external service
    String url = "http://sampling-service:8082/api/samples/" + id;
    return restTemplate.getForEntity(url, Map.class).getBody();
}

// Fallback method - same signature + Exception parameter
private Map<String, Object> getSamplingDataFallback(Long id, Exception ex) {
    logger.warn("Circuit breaker activated: {}", ex.getMessage());
    return Map.of("id", id, "status", "FALLBACK", "data", "Cached data");
}
```

### Test It
```bash
# Normal call (ID < 100)
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/50

# Trigger failures (ID > 100) - call 5+ times
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/101
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/102
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/103
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/104
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/105

# Circuit is now OPEN - all calls fail fast
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/50
# Response: 503 Service Unavailable - Circuit breaker is OPEN

# Wait 5 seconds, circuit goes to HALF_OPEN
# Try with ID < 100 to recover
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/50
```

---

## 🔄 Retry

### What It Does
Automatically retries failed operations (useful for transient network errors).

### Configuration
```yaml
resilience4j:
  retry:
    instances:
      evaluationService:
        maxAttempts: 4                      # Retry up to 4 times
        waitDuration: 1s                    # Wait 1s between retries
        enableExponentialBackoff: true      # Increase wait time exponentially
        exponentialBackoffMultiplier: 2     # Double wait time each retry
        # Wait times: 1s, 2s, 4s
        retryExceptions:                    # Retry these exceptions
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:                   # Don't retry these
          - java.lang.IllegalArgumentException
```

### Usage Example
```java
@Retry(name = "evaluationService", fallbackMethod = "processEvaluationFallback")
public Map<String, Object> processEvaluation(Long id) {
    logger.info("Processing evaluation (attempt will be logged)");
    
    // Simulate transient failure
    if (Math.random() < 0.3) {
        throw new RuntimeException("Temporary network error");
    }
    
    return Map.of("id", id, "status", "PROCESSED");
}

private Map<String, Object> processEvaluationFallback(Long id, Exception ex) {
    logger.error("All retry attempts exhausted: {}", ex.getMessage());
    return Map.of("id", id, "status", "RETRY_FAILED");
}
```

### Test It
```bash
# Call multiple times - some will succeed, some will retry
curl -X POST http://localhost:8081/api/resilience/retry/evaluation/123

# Check logs to see retry attempts:
# 2026-01-13 10:30:00 - Processing evaluation (attempt 1)
# 2026-01-13 10:30:01 - Processing evaluation (attempt 2)
# 2026-01-13 10:30:03 - Processing evaluation (attempt 3)
# 2026-01-13 10:30:07 - Processing evaluation (attempt 4)
```

---

## ⏱️ Timeout (Time Limiter)

### What It Does
Enforces maximum execution time for operations, preventing indefinite waits.

### Configuration
```yaml
resilience4j:
  timelimiter:
    instances:
      samplingService:
        timeoutDuration: 2s                # Max 2 seconds
        cancelRunningFuture: true          # Cancel if timeout
```

### Usage Example
```java
@TimeLimiter(name = "samplingService", fallbackMethod = "getDataFallback")
@CircuitBreaker(name = "samplingService")  // Can combine with Circuit Breaker
public CompletableFuture<Map<String, Object>> getDataWithTimeout(Long id) {
    return CompletableFuture.supplyAsync(() -> {
        // Simulate slow operation
        Thread.sleep(id % 2 == 0 ? 3000 : 500);  // Even IDs timeout
        return Map.of("id", id, "data", "Success");
    });
}

private CompletableFuture<Map<String, Object>> getDataFallback(Long id, TimeoutException ex) {
    return CompletableFuture.completedFuture(
        Map.of("id", id, "status", "TIMEOUT", "data", "Cached data")
    );
}
```

### Test It
```bash
# Fast response (odd ID, <1s)
curl http://localhost:8081/api/resilience/timeout/data/1

# Timeout (even ID, >2s)
curl http://localhost:8081/api/resilience/timeout/data/2
# Response: 408 Request Timeout
```

---

## 🚧 Bulkhead

### What It Does
Limits concurrent calls to prevent resource exhaustion and isolate failures.

### Configuration
```yaml
resilience4j:
  bulkhead:
    instances:
      jiraService:
        maxConcurrentCalls: 5              # Max 5 concurrent requests
        maxWaitDuration: 1s                # Wait up to 1s for slot
```

### Usage Example
```java
@Bulkhead(name = "jiraService", fallbackMethod = "createJiraTicketFallback")
@Retry(name = "jiraService")
public Map<String, Object> createJiraTicket(String title, String description) {
    // Simulate slow JIRA API call
    Thread.sleep(2000);
    return Map.of("ticketId", "TICKET-123", "status", "CREATED");
}

private Map<String, Object> createJiraTicketFallback(String title, String description, Exception ex) {
    return Map.of("status", "FAILED", "error", "JIRA service overloaded");
}
```

### Test It
```bash
# Send 10 concurrent requests (only 5 allowed, rest rejected)
for i in {1..10}; do
  curl -X POST http://localhost:8081/api/resilience/bulkhead/jira \
    -H "Content-Type: application/json" \
    -d '{"title":"Test Ticket '$i'"}' &
done

# First 5: 200 OK
# Next 5: 429 Too Many Requests - Bulkhead full
```

---

## 🔗 Combined Patterns

You can combine multiple patterns for comprehensive resilience:

```java
@Bulkhead(name = "evaluationService")           // 1. Limit concurrent calls
@CircuitBreaker(name = "evaluationService")     // 2. Fail fast if service down
@Retry(name = "evaluationService")              // 3. Retry transient failures
@TimeLimiter(name = "evaluationService")        // 4. Enforce timeout
public CompletableFuture<Map<String, Object>> complexOperation(Long id) {
    // Your business logic
    return CompletableFuture.supplyAsync(() -> {
        // Call external service
        return processData(id);
    });
}
```

**Execution Order:**
1. **Bulkhead** checks if slot available
2. **Circuit Breaker** checks if circuit is closed
3. **Retry** retries on failure
4. **Time Limiter** enforces timeout

---

## 📊 Monitoring

### Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,circuitbreakers,circuitbreakerevents,retries,retryevents
```

### Available Endpoints

```bash
# Circuit Breaker Status
curl http://localhost:8081/actuator/circuitbreakers

# Circuit Breaker Events (last 100)
curl http://localhost:8081/actuator/circuitbreakerevents

# Retry Statistics
curl http://localhost:8081/actuator/retries

# Retry Events
curl http://localhost:8081/actuator/retryevents

# Health Check (includes circuit breaker states)
curl http://localhost:8081/actuator/health
```

### Example Response
```json
{
  "circuitBreakers": {
    "samplingService": {
      "state": "CLOSED",
      "failureRate": "20.0%",
      "slowCallRate": "0.0%",
      "bufferedCalls": 10,
      "failedCalls": 2,
      "successfulCalls": 8
    },
    "evaluationService": {
      "state": "OPEN",
      "failureRate": "80.0%",
      "waitUntil": "2026-01-13T10:35:00Z"
    }
  }
}
```

---

## 🧪 Testing Resilience Patterns

### 1. Test Circuit Breaker
```bash
# Get info about resilience configuration
curl http://localhost:8081/api/resilience/info

# Test circuit breaker
curl http://localhost:8081/api/resilience/circuit-breaker/sampling/50
```

### 2. Test Retry
```bash
# Call multiple times to see retries
for i in {1..5}; do
  curl -X POST http://localhost:8081/api/resilience/retry/evaluation/123
  sleep 1
done
```

### 3. Test Timeout
```bash
# Fast response (ID=1)
time curl http://localhost:8081/api/resilience/timeout/data/1

# Timeout (ID=2)
time curl http://localhost:8081/api/resilience/timeout/data/2
```

### 4. Test Bulkhead
```bash
# Load testing with Apache Bench
ab -n 100 -c 20 -H "Content-Type: application/json" \
  -p data.json http://localhost:8081/api/resilience/bulkhead/jira

# data.json:
# {"title":"Test Ticket","description":"Test"}
```

### 5. Test Combined Patterns
```bash
curl -X POST http://localhost:8081/api/resilience/combined/123
```

---

## 📈 Production Best Practices

### 1. Configure Per Service
```yaml
resilience4j:
  circuitbreaker:
    instances:
      criticalService:
        failureRateThreshold: 30    # More sensitive
        waitDurationInOpenState: 60s # Longer recovery
      
      nonCriticalService:
        failureRateThreshold: 70    # More tolerant
        waitDurationInOpenState: 10s # Faster recovery
```

### 2. Monitor with Prometheus
```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
```

### 3. Use Proper Timeouts
```
RestTemplate timeout (3s) < Service timeout (5s) < Circuit breaker timeout
```

### 4. Log All Fallbacks
```java
private Map<String, Object> fallback(Long id, Exception ex) {
    logger.error("Fallback executed for ID: {}", id, ex);
    // Alert monitoring system
    metrics.counter("fallback.triggered").increment();
    return cachedData(id);
}
```

### 5. Test Failure Scenarios
- Network failures
- Service downtime
- Slow responses
- High load

---

## 🎯 When to Use Each Pattern

| Pattern | Use When | Example |
|---------|----------|---------|
| **Circuit Breaker** | External service can fail | Calling payment gateway, third-party API |
| **Retry** | Transient errors expected | Network glitches, temporary overload |
| **Timeout** | Long operations possible | Database queries, file uploads |
| **Bulkhead** | Need resource isolation | Separate thread pools for different services |

---

## 🔍 Troubleshooting

### Issue: Circuit stays OPEN
```bash
# Check circuit breaker state
curl http://localhost:8081/actuator/circuitbreakers

# Check failure rate
# If >50% failures, wait for waitDurationInOpenState

# Force close (for testing only):
# Restart application or call healthy service multiple times
```

### Issue: Retries not working
```bash
# Check if exception is in retryExceptions list
# Check logs for retry attempts
# Verify @EnableAspectJAutoProxy is present
```

### Issue: Timeout not triggering
```bash
# Verify CompletableFuture is returned (for @TimeLimiter)
# Check timeoutDuration configuration
# Ensure method is async
```

---

## 📚 Additional Resources

- [Resilience4j Documentation](https://resilience4j.readme.io/)
- [Spring Boot Resilience4j Guide](https://docs.spring.io/spring-cloud-circuitbreaker/docs/current/reference/html/)
- [Circuit Breaker Pattern](https://martinfowler.com/bliki/CircuitBreaker.html)
- [Retry Pattern](https://docs.microsoft.com/en-us/azure/architecture/patterns/retry)

---

**Your microservices are now resilient! 🛡️**
