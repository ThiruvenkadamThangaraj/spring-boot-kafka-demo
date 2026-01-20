# ========================================
# INTERVIEW QUESTIONS & ANSWERS
# Spring Boot + JWT + OAuth2 + AWS + Terraform
# ========================================

## Table of Contents
1. [Java Fundamentals - Threads & Concurrency](#java-fundamentals---threads--concurrency)
2. [Java Collections Framework](#java-collections-framework)
3. [Clean Code Principles in Java](#clean-code-principles-in-java)
4. [Common Code Smells & Anti-Patterns](#common-code-smells--anti-patterns)
5. [Spring Framework Core Concepts](#spring-framework-core-concepts)
6. [Spring Boot Essentials](#spring-boot-essentials)
7. [Spring MVC & REST APIs](#spring-mvc--rest-apis)
8. [Spring Data JPA](#spring-data-jpa)
9. [Spring Boot Configuration & Profiles](#spring-boot-configuration--profiles)
10. [JWT & Spring Security Questions](#jwt--spring-security-questions)
11. [AWS & Terraform Questions](#aws--terraform-questions)
12. [Microservices Questions](#microservices-questions)
13. [Microservices Communication Patterns](#microservices-communication-patterns)
14. [RestTemplate vs WebClient vs Feign Client](#resttemplate-vs-webclient-vs-feign-client)
15. [Async Programming & @Async Annotation](#async-programming--async-annotation)
16. [Transaction Management & @Transactional](#transaction-management--transactional)
17. [Self-Invocation Problem](#self-invocation-problem)
18. [Database Design - Views & Stored Procedures](#database-design---views--stored-procedures)
19. [Real-World Microservices Architecture Examples

### Q14a: "Walk me through your actual microservices implementation with real code examples"

**Answer:**
"Let me show you the **actual event-driven microservices system** I built that processes **757 users/second** with capacity to scale to **300K-500K messages/sec**.

---

### ¯ Architecture Overview

**Services:**
1. **Evaluation Service** (Port 8081) - Core evaluation logic
2. **Sampling Service** (Port 8082) - Sample data management  
3. **Evidence Service** (Port 8083) - Evidence processing
4. **Remediation Service** (Port 8084) - Remediation workflows
5. **Jira Service** (Port 8085) - JIRA integration

**Communication:**
- **Synchronous**: REST APIs with RestTemplate/WebClient + Circuit Breakers
- **Asynchronous**: Apache Kafka with 12 partitions
- **Database**: PostgreSQL (database-per-service pattern)

---

### Real Kafka Producer Implementation

**From my EventPublisher.java:**
```java
@Service
public class EventPublisher {
    private static final String USER_CREATED_TOPIC = "user-created-events";

    @Autowired
    private KafkaTemplate<String, UserCreatedEvent> kafkaTemplate;

    @Async  // Non-blocking - returns immediately
    public void publishUserCreatedEvent(UserCreatedEvent event) {
        // Partition by username  same user always goes to same partition  ordering guaranteed
        kafkaTemplate.send(USER_CREATED_TOPIC, event.getUsername(), event);
        System.out.println("ðŸ“¤ Event published asynchronously: " + event.getUsername());
    }
}
```

**UserService calls it:**
```java
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private EventPublisher eventPublisher;
    
    public User createUser(UserRequest request) {
        // 1. Save to database (synchronous)
        User user = userRepository.save(new User(request));
        
        // 2. Publish event (asynchronous - doesn't block)
        UserCreatedEvent event = new UserCreatedEvent(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            "evaluation-service"
        );
        eventPublisher.publishUserCreatedEvent(event);
        
        // 3. Return immediately (don't wait for Kafka)
        return user;  // Response time: ~50ms
    }
}
```
**Key Benefits:**
- YES API responds in **50ms** (doesn't wait for Kafka, email, or downstream services)
- YES If Kafka is down, database still saves (user not lost)
- YES Event consumers can retry independently

---

###  Real Kafka Consumer Implementation

**From my UserCreatedEventConsumer.java:**
```java
@Component
public class UserCreatedEventConsumer {
    private static final Logger logger = LoggerFactory.getLogger(UserCreatedEventConsumer.class);

    @Autowired
    private EmailService emailService;
    
    @Autowired(required = false)
    private AnomalyDetectionAgent anomalyDetectionAgent;
    
    @Autowired(required = false)
    private JiraIntelligenceAgent jiraIntelligenceAgent;

    @KafkaListener(
        topics = "user-created-events",
        groupId = "email-service-group"  // All 5 services share same group
    )
    public void handleUserCreatedEvent(UserCreatedEvent event) {
        logger.info("ðŸ“¨ Received User Created Event: {} from {}", 
                event.getUsername(), event.getServiceName());
        
        // AI-powered anomaly detection
        if (anomalyDetectionAgent != null) {
            AgentDecision anomalyDecision = anomalyDetectionAgent.process(event);
            
            if ("QUARANTINE".equals(anomalyDecision.getAction())) {
                logger.warn("ðŸš¨ USER QUARANTINED: {} - Reason: {}", 
                        event.getUsername(), anomalyDecision.getReasoning());
                
                // Create HIGH-priority Jira ticket for security review
                if (jiraIntelligenceAgent != null) {
                    createSecurityTicket(event, anomalyDecision);
                }
                
                // Skip email for quarantined users
                return;
                
            } else if ("FLAG_FOR_REVIEW".equals(anomalyDecision.getAction())) {
                logger.info("¸ User flagged for review: {}", event.getUsername());
                createReviewTicket(event, anomalyDecision);
            }
        }
        
        // Send welcome email (simulated with console log)
        emailService.sendWelcomeEmail(
            event.getEmail(),
            event.getFirstName(),
            event.getLastName(),
            event.getServiceName()
        );
    }
    
    private void createSecurityTicket(UserCreatedEvent event, AgentDecision decision) {
        JiraIntelligenceAgent.TicketRequest ticketRequest = new JiraIntelligenceAgent.TicketRequest();
        ticketRequest.setType("USER_ANOMALY");
        ticketRequest.setDescription(String.format(
                "Suspicious user account detected:\n" +
                "Username: %s\n" +
                "Email: %s\n" +
                "Service: %s\n" +
                "Anomaly Score: %.2f",
                event.getUsername(),
                event.getEmail(),
                event.getServiceName(),
                decision.getParameters().get("anomalyScore")
        ));
        
        jiraIntelligenceAgent.process(ticketRequest);
        logger.info("« Security ticket created");
    }
}
```

**What happens when a user is created:**
```
1. POST /api/users  evaluation-service
2. User saved to PostgreSQL  Returns 200 OK (50ms)
3. Event published to Kafka  "user-created-events" topic
4. Kafka distributes to 12 partitions (partitioned by username)
5. ALL 5 services consume event in parallel:
   - evaluation-service: Runs AI anomaly detection
   - sampling-service: Processes sample data
   - evidence-service: Collects evidence
   - remediation-service: Checks remediation rules
   - jira-service: Creates tickets if needed
6. EmailService sends welcome email (or quarantine alert)
```

---

### Real Circuit Breaker Implementation

**From my ResilientService.java:**
```java
@Service
public class ResilientService {
    @Autowired(required = false)
    private RestTemplate restTemplate;
    
    /**
     * Circuit Breaker protects against cascading failures
     * 
     * Configuration (application.yml):
     *   slidingWindowSize: 10          # Last 10 calls
     *   failureRateThreshold: 50       # Open if >50% fail
     *   waitDurationInOpenState: 5s    # Wait 5s before retrying
     */
    @CircuitBreaker(name = "samplingService", fallbackMethod = "getSamplingDataFallback")
    public Map<String, Object> getSamplingData(Long id) {
        logger.info("Calling Sampling Service for ID: {}", id);
        
        String url = "http://sampling-service:8082/api/samples/" + id;
        ResponseEntity<Map<String, Object>> response = 
            restTemplate.getForEntity(url, Map.class);
        return response.getBody();
    }
    
    /**
     * Fallback method - called when circuit is OPEN or method fails
     * Same signature + Exception parameter
     */
    private Map<String, Object> getSamplingDataFallback(Long id, Exception ex) {
        logger.warn("Circuit breaker activated for Sampling Service. Using fallback for ID: {}. Error: {}", 
                    id, ex.getMessage());
        
        // Return cached/default data instead of failing
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("id", id);
        fallback.put("samplingData", "Cached/Default Data");
        fallback.put("status", "FALLBACK");
        fallback.put("message", "Sampling service temporarily unavailable");
        return fallback;
    }
    
    /**
     * Retry pattern - automatically retries transient failures
     * 
     * Configuration:
     *   maxAttempts: 4                    # Retry up to 4 times
     *   waitDuration: 1s                  # Wait 1s between retries
     *   exponentialBackoff: true          # 1s, 2s, 4s
     */
    @Retry(name = "evaluationService", fallbackMethod = "processEvaluationFallback")
    public Map<String, Object> processEvaluation(Long evaluationId) {
        logger.info("Processing evaluation ID: {}", evaluationId);
        
        // Simulate transient failure (network glitch)
        if (Math.random() < 0.3) {  // 30% chance of failure
            logger.warn("Transient failure occurred, will retry...");
            throw new RuntimeException("Temporary network error");
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("evaluationId", evaluationId);
        result.put("status", "PROCESSED");
        result.put("score", 85);
        return result;
    }
    
    private Map<String, Object> processEvaluationFallback(Long evaluationId, Exception ex) {
        logger.error("All retry attempts exhausted for evaluation ID: {}", evaluationId);
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("evaluationId", evaluationId);
        fallback.put("status", "RETRY_FAILED");
        fallback.put("message", "Processing failed after 4 attempts");
        return fallback;
    }
    
    /**
     * Bulkhead - limits concurrent calls to prevent resource exhaustion
     * 
     * Configuration:
     *   maxConcurrentCalls: 5       # Max 5 parallel calls
     *   maxWaitDuration: 1s         # Wait max 1s if all 5 busy
     */
    @Bulkhead(name = "jiraService", fallbackMethod = "createJiraTicketFallback")
    @Retry(name = "jiraService")
    public Map<String, Object> createJiraTicket(String title, String description) {
        logger.info("Creating JIRA ticket: {}", title);
        
        // Simulate slow JIRA API call
        Thread.sleep(2000);
        
        Map<String, Object> ticket = new HashMap<>();
        ticket.put("ticketId", "TICKET-" + System.currentTimeMillis());
        ticket.put("title", title);
        ticket.put("status", "CREATED");
        return ticket;
    }
    
    private Map<String, Object> createJiraTicketFallback(String title, String description, Exception ex) {
        logger.error("Failed to create JIRA ticket: {}. Error: {}", title, ex.getMessage());
        
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("title", title);
        fallback.put("status", "FAILED");
        fallback.put("message", "JIRA service overloaded - ticket queued for retry");
        return fallback;
    }
}
```

**Resilience4j Configuration (application.yml):**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      samplingService:
        slidingWindowSize: 10              # Monitor last 10 calls
        minimumNumberOfCalls: 5            # Need 5 calls before evaluating
        failureRateThreshold: 50           # Open circuit if >50% fail
        waitDurationInOpenState: 5s        # Wait 5s before testing recovery
        permittedNumberOfCallsInHalfOpenState: 3  # Test with 3 calls

  retry:
    instances:
      evaluationService:
        maxAttempts: 4                      # Retry up to 4 times
        waitDuration: 1s                    # Wait 1s between retries
        enableExponentialBackoff: true      # 1s, 2s, 4s
        exponentialBackoffMultiplier: 2
        retryExceptions:                    # Retry these
          - java.io.IOException
          - java.util.concurrent.TimeoutException

  bulkhead:
    instances:
      jiraService:
        maxConcurrentCalls: 5               # Max 5 parallel calls
        maxWaitDuration: 1s                 # Wait max 1s for slot

  timelimiter:
    instances:
      samplingService:
        timeoutDuration: 2s                 # Max 2 seconds per call
        cancelRunningFuture: true           # Cancel if timeout
```

---

### Real Performance Metrics

**From TECHNICAL-DOCUMENTATION.md:**

**Throughput Achieved:**
- **Single Machine**: 757 users/sec
- **With Batching**: 300K-500K messages/sec capacity
- **Kafka Partitions**: 12 (can scale to 48 for 2M/sec)
- **Consumer Threads**: 15 total (5 services Ã— 3 threads each)

**Kafka Configuration for High Throughput:**
```yaml
spring:
  kafka:
    producer:
      batch-size: 32768              # 32KB batches
      linger-ms: 10                  # Wait 10ms to batch messages
      compression-type: snappy       # 3-5x compression
      acks: 1                        # Leader acknowledgment only
      buffer-memory: 67108864        # 64MB buffer
    
    consumer:
      max-poll-records: 500          # Fetch 500 records per poll
      fetch-min-size: 1048576        # Wait for 1MB of data
      fetch-max-wait: 500            # Or max 500ms
      enable-auto-commit: true       # Auto-commit every 5s
      group-id: email-service-group
    
    listener:
      concurrency: 3                 # 3 consumer threads per service
```

**Performance Impact:**
- **Batching**: Reduces network overhead by ~10x
- **Compression**: Reduces payload by 3-5x
- **Concurrency**: 15 threads = 15 partitions processed in parallel
- **Auto-commit**: Reduces offset commit overhead

---

### Database-per-Service Pattern

**Each service has its own database schema:**

```java
// evaluation-service - application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluation_db
    username: eval_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        default_schema: evaluation

// sampling-service - application.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/sampling_db
    username: sampling_user
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        default_schema: sampling
```
**Benefits:**
-  YES Independent scaling: Scale evaluation_db separately from sampling_db
-  YES Technology flexibility: Can use PostgreSQL for evaluation, MongoDB for evidence
-  YES Fault isolation: sampling_db crash doesn't affect evaluation-service
-  YES Independent deployments: Change evaluation schema without coordinating with sampling

**Challenges & Solutions:**
-  NO JOIN across services → YES Use Kafka events + denormalization
-  Distributed transactions → YES Use Saga pattern (see below)
-  Data consistency → YES Eventual consistency + compensating transactions

---

### Saga Pattern (Distributed Transactions)

**Problem:**
Cannot use database transactions across microservices.

**Example Workflow:**
```
User Registration Flow:
1. evaluation-service: Create user account
2. sampling-service: Create sample profile
3. jira-service: Create welcome ticket
4. email-service: Send welcome email

What if step 3 fails? Need to rollback steps 1 and 2!
```

**My Choreography-Based Saga Implementation:**

```java
// Step 1: evaluation-service publishes event
@Transactional
public User createUser(UserRequest request) {
    User user = userRepository.save(new User(request));
    
    UserCreatedEvent event = new UserCreatedEvent(user.getId(), user.getUsername());
    eventPublisher.publishUserCreatedEvent(event);  // Kafka
    
    return user;
}

// Step 2: sampling-service consumes event
@KafkaListener(topics = "user-created-events")
public void handleUserCreated(UserCreatedEvent event) {
    try {
        Sample sample = createSampleProfile(event.getUserId());
        
        // Publish success event
        SampleCreatedEvent successEvent = new SampleCreatedEvent(
            event.getUserId(), sample.getId()
        );
        kafkaTemplate.send("sample-created-events", successEvent);
        
    } catch (Exception ex) {
        // Publish failure event  triggers compensation
        SampleCreationFailedEvent failureEvent = new SampleCreationFailedEvent(
            event.getUserId(), ex.getMessage()
        );
        kafkaTemplate.send("sample-creation-failed-events", failureEvent);
    }
}

// Step 3: evaluation-service compensates on failure
@KafkaListener(topics = "sample-creation-failed-events")
public void handleSampleCreationFailed(SampleCreationFailedEvent event) {
    logger.error("Sample creation failed for user {}. Rolling back...", event.getUserId());
    
    // Compensating transaction: Delete user account
    userRepository.deleteById(event.getUserId());
    
    // Publish compensation complete event
    UserCreationRolledBackEvent rollbackEvent = new UserCreationRolledBackEvent(
        event.getUserId(), "Sample creation failed"
    );
    kafkaTemplate.send("user-rollback-events", rollbackEvent);
}
```

**Saga Event Flow:**
```
SUCCESS PATH:
user-created-events  sample-created-events  jira-created-events  email-sent-events

FAILURE PATH:
user-created-events  sample-creation-failed-events  user-rollback-events
```

---

### Key Takeaways from My Implementation

**What worked well:**
 **Kafka + Circuit Breakers**: System survived 30-minute sampling-service outage (failed fast, used fallbacks)
 **Database-per-Service**: Scaled evaluation-service to 10 instances without touching other services
 **Async Events**: API response time: 50ms (doesn't wait for downstream services)
 **Resilience4j**: Prevented cascading failures during Black Friday load spike

**What I learned:**
 **Eventual Consistency**: Hard to debug when data is out of sync across services
 **Distributed Tracing**: Need correlation IDs to trace requests across services
  **Testing**: Integration tests complex with 5 services + Kafka + PostgreSQL
  **Monitoring**: Need centralized logging (we use CloudWatch with correlation IDs)

**Production Metrics:**
-  **Throughput**: 757 users/sec (single machine)
-  **Scalability**: Can scale to 500K/sec with more partitions
-  **Resilience**: 99.9% uptime (survived multiple service outages)
-  **Response Time**: P50=45ms, P95=120ms, P99=250ms"

---

19. [High-Throughput & Kafka Questions](#high-throughput--kafka-questions)
20. [CompletableFuture & Async Processing](#completablefuture--async-processing)
21. [AI Agents & Intelligent Systems](#ai-agents--intelligent-systems)
22. [Resilience & Fault Tolerance](#resilience--fault-tolerance)
23. [Service Discovery & API Gateway](#service-discovery--api-gateway)
24. [Circuit Breaker Patterns](#circuit-breaker-patterns)
25. [Scenario-Based Questions](#scenario-based-questions)
26. [Success Stories & Failures](#success-stories--failures)

---

## â˜• Java Fundamentals - Threads & Concurrency

### Q1: "Explain the difference between Thread, Runnable, and Callable in Java"

**Answer:**
"There are three main ways to create threads in Java:

**1. Extending Thread Class**
```java
// âŒ BAD: No multiple inheritance, tightly coupled
class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Thread running");
    }
}
// Usage
MyThread t = new MyThread();
t.start();
```

**2. Implementing Runnable (Better)**
```java
//  GOOD: Separation of concerns, allows multiple inheritance
class MyTask implements Runnable {
    @Override
    public void run() {
        System.out.println("Task running");
    }
}
// Usage
Thread t = new Thread(new MyTask());
t.start();

// Or with Lambda (Java 8+)
Thread t = new Thread(() -> System.out.println("Task running"));
t.start();
```

**3. Implementing Callable (Best for return values)**
```java
//  BEST: Returns value, can throw checked exceptions
class MyCallable implements Callable<Integer> {
    @Override
    public Integer call() throws Exception {
        // Do some computation
        return 42;
    }
}
// Usage with ExecutorService
ExecutorService executor = Executors.newFixedThreadPool(5);
Future<Integer> future = executor.submit(new MyCallable());
Integer result = future.get(); // Blocks until result is ready
```

**Key Differences:**
| Feature | Thread | Runnable | Callable |
|---------|--------|----------|----------|
| Return Value | No | No | Yes (via Future) |
| Exception | RuntimeException | RuntimeException | Checked Exception |
| Method | run() | run() | call() |
| Multiple Inheritance | No | Yes | Yes |

**In production, I use:**
- **ExecutorService + Callable** for async tasks with results
- **@Async with CompletableFuture** in Spring Boot
- **Thread pools** instead of creating threads manually"

---

### Q2: "Explain synchronized keyword and different synchronization techniques"

**Answer:**
"Synchronization ensures thread-safe access to shared resources:

**1. Method-Level Synchronization**
```java
// âŒ BAD: Locks entire object, blocks all synchronized methods
public class Counter {
    private int count = 0;
    
    public synchronized void increment() {
        count++; // Atomic operation guaranteed
    }
    
    public synchronized int getCount() {
        return count; // Unnecessary lock here
    }
}
```

**2. Block-Level Synchronization (Better)**
```java
//  GOOD: Fine-grained locking, better concurrency
public class Counter {
    private int count = 0;
    private final Object lock = new Object();
    
    public void increment() {
        synchronized(lock) {
            count++; // Only critical section locked
        }
    }
    
    public int getCount() {
        return count; // No lock needed for read (if volatile)
    }
}
```

**3. Using Locks (Most Flexible)**
```java
//  BEST: Explicit control, tryLock, interruptible
import java.util.concurrent.locks.ReentrantLock;

public class Counter {
    private int count = 0;
    private final ReentrantLock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock(); // Always unlock in finally
        }
    }
    
    public boolean tryIncrementWithTimeout() {
        try {
            if (lock.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    count++;
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            return false; // Couldn't acquire lock
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

**4. Atomic Classes (Best for Simple Operations)**
```java
//  BEST: Lock-free, CAS-based, highest performance
import java.util.concurrent.atomic.AtomicInteger;

public class Counter {
    private AtomicInteger count = new AtomicInteger(0);
    
    public void increment() {
        count.incrementAndGet(); // Atomic, no lock needed
    }
    
    public int getCount() {
        return count.get();
    }
}
```

**Real Example from My Project:**
```java
// Concurrent cache with fine-grained locking
public class UserCache {
    private final ConcurrentHashMap<Long, User> cache = new ConcurrentHashMap<>();
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();
    
    public User getOrLoad(Long userId) {
        // Fast path: check cache first
        User user = cache.get(userId);
        if (user != null) {
            return user;
        }
        
        // Slow path: load from DB with per-user locking
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();
        try {
            // Double-check after acquiring lock
            user = cache.get(userId);
            if (user != null) {
                return user;
            }
            
            // Load from database
            user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                cache.put(userId, user);
            }
            return user;
        } finally {
            lock.unlock();
        }
    }
}
```

**Do's:**
 Always unlock in `finally` block
 Use `AtomicInteger` for simple counters
 Use `ConcurrentHashMap` instead of `synchronized(map)`
 Keep synchronized blocks small
 Use `ReadWriteLock` for read-heavy workloads

**Don'ts:**
âŒ Don't synchronize on String literals or boxed primitives
âŒ Don't nest locks (can cause deadlock)
âŒ Don't call external methods inside synchronized blocks
âŒ Don't use `synchronized` for long-running operations"

---

### Q3: "What are Thread Pools and why use ExecutorService?"

**Answer:**
"Thread pools reuse threads instead of creating new ones for each task:

**âŒ BAD: Creating Threads Manually**
```java
// Problem: Thread creation overhead, no limit, memory exhaustion
public class BadThreadExample {
    public void processRequests(List<Request> requests) {
        for (Request req : requests) {
            new Thread(() -> {
                processRequest(req); // 1000 requests = 1000 threads!
            }).start();
        }
    }
}
```

** GOOD: Using ExecutorService**
```java
import java.util.concurrent.*;

public class GoodThreadExample {
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public void processRequests(List<Request> requests) {
        List<Future<Result>> futures = new ArrayList<>();
        
        for (Request req : requests) {
            Future<Result> future = executor.submit(() -> {
                return processRequest(req); // Max 10 threads, reused
            });
            futures.add(future);
        }
        
        // Wait for all to complete
        for (Future<Result> future : futures) {
            try {
                Result result = future.get(30, TimeUnit.SECONDS);
                // Handle result
            } catch (TimeoutException e) {
                future.cancel(true); // Cancel slow tasks
            }
        }
    }
    
    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
```

**Types of Thread Pools:**
```java
// 1. Fixed Thread Pool - Best for bounded workload
ExecutorService fixed = Executors.newFixedThreadPool(10);
// Use: Web server handling requests

// 2. Cached Thread Pool - Creates threads as needed
ExecutorService cached = Executors.newCachedThreadPool();
// Use: Short-lived async tasks

// 3. Single Thread Executor - Sequential execution
ExecutorService single = Executors.newSingleThreadExecutor();
// Use: Event processing, logging

// 4. Scheduled Thread Pool - Periodic tasks
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(5);
scheduled.scheduleAtFixedRate(() -> {
    cleanupCache();
}, 0, 1, TimeUnit.HOURS);

// 5. Custom Thread Pool - Full control
ExecutorService custom = new ThreadPoolExecutor(
    10,                      // core pool size
    50,                      // max pool size
    60L, TimeUnit.SECONDS,   // keep alive time
    new LinkedBlockingQueue<>(1000), // queue capacity
    new ThreadPoolExecutor.CallerRunsPolicy() // rejection policy
);
```

**Real Example - Processing User Uploads:**
```java
@Service
public class FileProcessingService {
    
    @Bean
    public ExecutorService fileProcessorPool() {
        return new ThreadPoolExecutor(
            5,  // Core: always 5 threads
            20, // Max: scale up to 20
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100), // Queue max 100 tasks
            new ThreadFactory() {
                private AtomicInteger count = new AtomicInteger(0);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("file-processor-" + count.incrementAndGet());
                    t.setDaemon(false); // Prevent JVM exit
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure
        );
    }
    
    @Autowired
    private ExecutorService fileProcessorPool;
    
    public CompletableFuture<ProcessResult> processFile(MultipartFile file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Validate file
                validateFile(file);
                
                // Process file (CPU-intensive)
                byte[] data = file.getBytes();
                ProcessResult result = processData(data);
                
                // Save to S3
                s3Client.upload(result);
                
                return result;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, fileProcessorPool);
    }
}
```

**Monitoring Thread Pool Health:**
```java
@Component
public class ThreadPoolMonitor {
    
    @Scheduled(fixedRate = 60000) // Every minute
    public void monitorThreadPools() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) fileProcessorPool;
        
        int activeThreads = executor.getActiveCount();
        int poolSize = executor.getPoolSize();
        int queueSize = executor.getQueue().size();
        long completedTasks = executor.getCompletedTaskCount();
        
        logger.info("ThreadPool Stats: active={}, pool={}, queue={}, completed={}",
            activeThreads, poolSize, queueSize, completedTasks);
        
        // Alert if queue is backing up
        if (queueSize > 80) {
            logger.warn("Thread pool queue is 80% full! Consider scaling.");
        }
    }
}
```

**Do's:**
 Use thread pools instead of creating threads
 Configure core/max size based on load testing
 Set queue capacity to prevent memory exhaustion
 Use `CallerRunsPolicy` for backpressure
 Always shutdown executors in `@PreDestroy`
 Monitor pool metrics

**Don'ts:**
âŒ Don't use `Executors.newCachedThreadPool()` for unbounded tasks
âŒ Don't forget to shutdown - causes thread leaks
âŒ Don't submit blocking I/O tasks to CPU-bound pools
âŒ Don't ignore `RejectedExecutionException`"

---

### Q4: "Explain volatile, wait/notify, and deadlock prevention"

**Answer:**
"**1. Volatile Keyword**
```java
// âŒ BAD: Race condition, threads may cache value
public class StopFlag {
    private boolean stopped = false;
    
    public void run() {
        while (!stopped) { // May never see update!
            doWork();
        }
    }
    
    public void stop() {
        stopped = true; // Update may not be visible
    }
}

//  GOOD: volatile ensures visibility across threads
public class StopFlag {
    private volatile boolean stopped = false;
    
    public void run() {
        while (!stopped) { // Always sees latest value
            doWork();
        }
    }
    
    public void stop() {
        stopped = true; // Immediately visible to all threads
    }
}
```

**2. Wait/Notify for Thread Communication**
```java
//  Producer-Consumer Pattern
public class BlockingQueue<T> {
    private Queue<T> queue = new LinkedList<>();
    private int capacity;
    
    public BlockingQueue(int capacity) {
        this.capacity = capacity;
    }
    
    public synchronized void put(T item) throws InterruptedException {
        while (queue.size() == capacity) {
            wait(); // Release lock and wait
        }
        queue.add(item);
        notifyAll(); // Wake up waiting consumers
    }
    
    public synchronized T take() throws InterruptedException {
        while (queue.isEmpty()) {
            wait(); // Release lock and wait
        }
        T item = queue.poll();
        notifyAll(); // Wake up waiting producers
        return item;
    }
}
```

**3. Deadlock Prevention**
```java
// âŒ BAD: Deadlock - Thread 1 locks A then B, Thread 2 locks B then A
public class DeadlockExample {
    private final Object lockA = new Object();
    private final Object lockB = new Object();
    
    public void method1() {
        synchronized(lockA) {
            synchronized(lockB) { // Deadlock!
                // Do something
            }
        }
    }
    
    public void method2() {
        synchronized(lockB) {
            synchronized(lockA) { // Deadlock!
                // Do something
            }
        }
    }
}

//  GOOD: Lock ordering - always acquire locks in same order
public class DeadlockFree {
    private final Object lockA = new Object();
    private final Object lockB = new Object();
    
    public void method1() {
        synchronized(lockA) { // Always A first
            synchronized(lockB) { // Then B
                // Do something
            }
        }
    }
    
    public void method2() {
        synchronized(lockA) { // Always A first
            synchronized(lockB) { // Then B
                // Do something
            }
        }
    }
}

//  BEST: Use tryLock with timeout
public class DeadlockFreeWithTimeout {
    private final Lock lockA = new ReentrantLock();
    private final Lock lockB = new ReentrantLock();
    
    public boolean transfer() {
        try {
            if (lockA.tryLock(1, TimeUnit.SECONDS)) {
                try {
                    if (lockB.tryLock(1, TimeUnit.SECONDS)) {
                        try {
                            // Do transfer
                            return true;
                        } finally {
                            lockB.unlock();
                        }
                    }
                } finally {
                    lockA.unlock();
                }
            }
            return false; // Couldn't acquire locks
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
```

**Real Example - Bank Transfer:**
```java
@Service
public class BankTransferService {
    
    //  Lock accounts in consistent order to prevent deadlock
    public boolean transfer(Account from, Account to, BigDecimal amount) {
        // Always lock account with lower ID first
        Account first = from.getId() < to.getId() ? from : to;
        Account second = from.getId() < to.getId() ? to : from;
        
        synchronized(first) {
            synchronized(second) {
                if (from.getBalance().compareTo(amount) < 0) {
                    return false; // Insufficient funds
                }
                from.debit(amount);
                to.credit(amount);
                return true;
            }
        }
    }
}
```"

---

## ðŸ“¦ Java Collections Framework

### Q5: "Explain the difference between ArrayList vs LinkedList vs Vector"

**Answer:**
"**ArrayList - Best for Random Access**
```java
//  BEST: Fast random access O(1), fast iteration
List<String> arrayList = new ArrayList<>();
arrayList.add("A");        // O(1) amortized
arrayList.get(1000);       // O(1) - direct array access
arrayList.remove(0);       // O(n) - shifts elements

// Use when:
// - Frequent random access by index
// - Iteration > insertion/deletion
// - Most common choice
```

**LinkedList - Best for Insertions/Deletions**
```java
//  GOOD: Fast insertions O(1), but slow random access O(n)
List<String> linkedList = new LinkedList<>();
linkedList.add("A");       // O(1) at end
linkedList.addFirst("B");  // O(1) at start
linkedList.get(1000);      // O(n) - must traverse

// Use when:
// - Frequent insertions/deletions in middle
// - Queue/Deque operations
// - Don't need random access
```

**Vector - Legacy Synchronized ArrayList**
```java
// âŒ BAD: Synchronized overhead, legacy class
List<String> vector = new Vector<>();
// Don't use unless maintaining legacy code
// Use Collections.synchronizedList(new ArrayList<>()) instead
```

**Performance Comparison:**
```java
@Test
public void compareListPerformance() {
    int size = 100000;
    
    // ArrayList wins for random access
    List<Integer> arrayList = new ArrayList<>();
    long start = System.nanoTime();
    for (int i = 0; i < size; i++) {
        arrayList.add(i);
    }
    for (int i = 0; i < size; i++) {
        arrayList.get(i); // FAST: ~5ms
    }
    long arrayTime = System.nanoTime() - start;
    
    // LinkedList wins for frequent insertions at start
    List<Integer> linkedList = new LinkedList<>();
    start = System.nanoTime();
    for (int i = 0; i < size; i++) {
        linkedList.add(0, i); // FAST: inserts at start
    }
    long linkedTime = System.nanoTime() - start;
}
```

**Real Example - Request Queue:**
```java
//  Use LinkedList for queue operations
public class RequestQueue {
    private final Deque<Request> queue = new LinkedList<>();
    
    public void enqueue(Request req) {
        queue.addLast(req);  // O(1)
    }
    
    public Request dequeue() {
        return queue.pollFirst();  // O(1)
    }
    
    public void priorityInsert(Request urgentReq) {
        queue.addFirst(urgentReq);  // O(1) - ArrayList would be O(n)
    }
}

//  Use ArrayList for indexed access
public class UserCache {
    private final List<User> users = new ArrayList<>();
    
    public User getUserByIndex(int index) {
        return users.get(index);  // O(1)
    }
    
    public void bulkLoad(List<User> newUsers) {
        users.addAll(newUsers);  // O(n) but efficient
    }
}
```"

---

### Q6: "HashMap vs ConcurrentHashMap vs TreeMap - when to use what?"

**Answer:**
"**HashMap - Default Choice (Not Thread-Safe)**
```java
//  BEST for single-threaded or externally synchronized
Map<String, User> userMap = new HashMap<>();
userMap.put("john", new User("John"));  // O(1) average
userMap.get("john");                    // O(1) average

// âŒ BAD in multi-threaded environment
// Thread 1 puts, Thread 2 gets  ConcurrentModificationException
```

**ConcurrentHashMap - Thread-Safe HashMap**
```java
//  BEST for multi-threaded environments
Map<String, User> concurrentMap = new ConcurrentHashMap<>();

// Safe concurrent operations
concurrentMap.put("john", new User("John"));
concurrentMap.computeIfAbsent("jane", k -> loadFromDB(k));
concurrentMap.merge("count", 1, Integer::sum); // Atomic increment

// Real example - Request Counter
public class RequestCounter {
    private final ConcurrentHashMap<String, AtomicLong> counters = 
        new ConcurrentHashMap<>();
    
    public void recordRequest(String endpoint) {
        counters.computeIfAbsent(endpoint, k -> new AtomicLong())
                .incrementAndGet();
    }
    
    public long getCount(String endpoint) {
        return counters.getOrDefault(endpoint, new AtomicLong()).get();
    }
}
```

**TreeMap - Sorted Keys**
```java
//  GOOD when you need sorted order
Map<String, Integer> treeMap = new TreeMap<>();
treeMap.put("Charlie", 3);
treeMap.put("Alice", 1);
treeMap.put("Bob", 2);

// Iteration is sorted: Alice, Bob, Charlie
for (String key : treeMap.keySet()) {
    System.out.println(key); // Alphabetical order
}

// Real example - Leaderboard
public class Leaderboard {
    // Descending order by score
    private final TreeMap<Integer, String> scores = 
        new TreeMap<>(Collections.reverseOrder());
    
    public void addScore(String player, int score) {
        scores.put(score, player);
    }
    
    public List<String> getTopTen() {
        return scores.values().stream()
            .limit(10)
            .collect(Collectors.toList());
    }
}
```"

---

## ¨ Clean Code Principles in Java

### Q8: "What are SOLID principles? Explain with examples"

**Answer:**
"**S - Single Responsibility Principle**
```java
// âŒ BAD: Class has multiple responsibilities
public class UserService {
    public void registerUser(User user) {
        // Validate
        if (user.getEmail() == null) throw new Exception();
        
        // Save to DB
        database.save(user);
        
        // Send email
        emailService.send(user.getEmail(), "Welcome!");
        
        // Log
        logger.info("User registered: " + user.getId());
    }
}

//  GOOD: Each class has one responsibility
public class UserService {
    private UserValidator validator;
    private UserRepository repository;
    private EmailService emailService;
    private AuditLogger auditLogger;
    
    public void registerUser(User user) {
        validator.validate(user);
        repository.save(user);
        emailService.sendWelcomeEmail(user);
        auditLogger.logRegistration(user);
    }
}

public class UserValidator {
    public void validate(User user) {
        if (user.getEmail() == null) {
            throw new ValidationException("Email required");
        }
    }
}
```

**O - Open/Closed Principle**
```java
// âŒ BAD: Must modify class to add new payment types
public class PaymentProcessor {
    public void processPayment(Payment payment) {
        if (payment.getType().equals("CREDIT_CARD")) {
            // Process credit card
        } else if (payment.getType().equals("PAYPAL")) {
            // Process PayPal
        } else if (payment.getType().equals("CRYPTO")) {
            // Add new else-if every time!
        }
    }
}

//  GOOD: Open for extension, closed for modification
public interface PaymentStrategy {
    void process(Payment payment);
}

public class CreditCardStrategy implements PaymentStrategy {
    @Override
    public void process(Payment payment) {
        // Credit card processing
    }
}

public class PayPalStrategy implements PaymentStrategy {
    @Override
    public void process(Payment payment) {
        // PayPal processing
    }
}

public class PaymentProcessor {
    private Map<String, PaymentStrategy> strategies;
    
    public void processPayment(Payment payment) {
        PaymentStrategy strategy = strategies.get(payment.getType());
        strategy.process(payment);
    }
}
```

**L - Liskov Substitution Principle**
```java
// âŒ BAD: Square violates LSP
public class Rectangle {
    protected int width;
    protected int height;
    
    public void setWidth(int width) { this.width = width; }
    public void setHeight(int height) { this.height = height; }
    public int getArea() { return width * height; }
}

public class Square extends Rectangle {
    @Override
    public void setWidth(int width) {
        this.width = width;
        this.height = width; // Violates LSP!
    }
}

//  GOOD: Separate interfaces
public interface Shape {
    int getArea();
}

public class Rectangle implements Shape {
    private int width;
    private int height;
    
    public Rectangle(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    @Override
    public int getArea() { return width * height; }
}

public class Square implements Shape {
    private int side;
    
    public Square(int side) {
        this.side = side;
    }
    
    @Override
    public int getArea() { return side * side; }
}
```

**I - Interface Segregation Principle**
```java
// âŒ BAD: Fat interface forces implementation of unused methods
public interface Worker {
    void work();
    void eat();
    void sleep();
}

public class Robot implements Worker {
    @Override public void work() { /* work */ }
    @Override public void eat() { /* Robots don't eat! */ }
    @Override public void sleep() { /* Robots don't sleep! */ }
}

//  GOOD: Segregated interfaces
public interface Workable {
    void work();
}

public interface Eatable {
    void eat();
}

public interface Sleepable {
    void sleep();
}

public class Human implements Workable, Eatable, Sleepable {
    @Override public void work() { /* work */ }
    @Override public void eat() { /* eat */ }
    @Override public void sleep() { /* sleep */ }
}

public class Robot implements Workable {
    @Override public void work() { /* work */ }
}
```

**D - Dependency Inversion Principle**
```java
// âŒ BAD: High-level module depends on low-level module
public class UserService {
    private MySQLDatabase database = new MySQLDatabase(); // Tight coupling
    
    public void saveUser(User user) {
        database.save(user);
    }
}

//  GOOD: Both depend on abstraction
public interface UserRepository {
    void save(User user);
    User findById(Long id);
}

public class MySQLUserRepository implements UserRepository {
    @Override public void save(User user) { /* MySQL */ }
    @Override public User findById(Long id) { /* MySQL */ }
}

public class MongoDBUserRepository implements UserRepository {
    @Override public void save(User user) { /* MongoDB */ }
    @Override public User findById(Long id) { /* MongoDB */ }
}

@Service
public class UserService {
    private final UserRepository repository;
    
    @Autowired // Spring injects implementation
    public UserService(UserRepository repository) {
        this.repository = repository;
    }
    
    public void saveUser(User user) {
        repository.save(user);
    }
}
```"

---

## ðŸš« Common Code Smells & Anti-Patterns

### Q9: "Show examples of bad code vs clean code in Java"

**Answer:**
"**1. Long Methods**
```java
// âŒ BAD: 100+ line method
public void processOrder(Order order) {
    // Validate order (20 lines)
    if (order == null) throw new NullPointerException();
    if (order.getItems().isEmpty()) throw new IllegalArgumentException();
    // ... 18 more validation lines
    
    // Calculate price (30 lines)
    BigDecimal total = BigDecimal.ZERO;
    for (OrderItem item : order.getItems()) {
        BigDecimal itemPrice = item.getPrice();
        if (item.hasDiscount()) {
            itemPrice = itemPrice.multiply(BigDecimal.valueOf(0.9));
        }
        total = total.add(itemPrice);
    }
    // ... 25 more calculation lines
    
    // Save to database (20 lines)
    // Send notifications (20 lines)
    // Update inventory (20 lines)
}

//  GOOD: Extract methods, single responsibility
public void processOrder(Order order) {
    validateOrder(order);
    BigDecimal total = calculateTotal(order);
    Order savedOrder = saveOrder(order, total);
    sendNotifications(savedOrder);
    updateInventory(savedOrder);
}

private void validateOrder(Order order) {
    Objects.requireNonNull(order, "Order cannot be null");
    if (order.getItems().isEmpty()) {
        throw new IllegalArgumentException("Order must have items");
    }
}

private BigDecimal calculateTotal(Order order) {
    return order.getItems().stream()
        .map(this::calculateItemPrice)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
}
```

**2. Magic Numbers and Strings**
```java
// âŒ BAD: Magic numbers everywhere
public boolean canWithdraw(Account account, BigDecimal amount) {
    if (account.getBalance().compareTo(amount) < 0) {
        return false;
    }
    if (amount.compareTo(BigDecimal.valueOf(10000)) > 0) {
        return false; // What is 10000?
    }
    if (account.getType().equals("PREMIUM") && 
        amount.compareTo(BigDecimal.valueOf(50000)) > 0) {
        return false; // What is 50000?
    }
    return true;
}

//  GOOD: Named constants
public class WithdrawalLimits {
    private static final BigDecimal DAILY_LIMIT_STANDARD = BigDecimal.valueOf(10_000);
    private static final BigDecimal DAILY_LIMIT_PREMIUM = BigDecimal.valueOf(50_000);
    private static final String ACCOUNT_TYPE_PREMIUM = "PREMIUM";
}

public boolean canWithdraw(Account account, BigDecimal amount) {
    if (account.getBalance().compareTo(amount) < 0) {
        return false;
    }
    
    BigDecimal dailyLimit = account.getType().equals(ACCOUNT_TYPE_PREMIUM) 
        ? DAILY_LIMIT_PREMIUM 
        : DAILY_LIMIT_STANDARD;
    
    return amount.compareTo(dailyLimit) <= 0;
}
```

**3. Nested If Statements**
```java
// âŒ BAD: Deep nesting
public String processRequest(Request request) {
    if (request != null) {
        if (request.isValid()) {
            if (request.getUser() != null) {
                if (request.getUser().isActive()) {
                    if (request.getUser().hasPermission()) {
                        return "Success";
                    } else {
                        return "No permission";
                    }
                } else {
                    return "User inactive";
                }
            } else {
                return "No user";
            }
        } else {
            return "Invalid request";
        }
    } else {
        return "Null request";
    }
}

//  GOOD: Guard clauses, early return
public String processRequest(Request request) {
    if (request == null) {
        return "Null request";
    }
    if (!request.isValid()) {
        return "Invalid request";
    }
    if (request.getUser() == null) {
        return "No user";
    }
    if (!request.getUser().isActive()) {
        return "User inactive";
    }
    if (!request.getUser().hasPermission()) {
        return "No permission";
    }
    return "Success";
}
```

**4. God Classes**
```java
// âŒ BAD: Class does everything
public class UserManager {
    public void createUser() { }
    public void deleteUser() { }
    public void sendEmail() { }
    public void validateEmail() { }
    public void hashPassword() { }
    public void checkPassword() { }
    public void saveToDatabase() { }
    public void loadFromDatabase() { }
    public void generateReport() { }
    public void exportToPDF() { }
    // 50 more methods...
}

//  GOOD: Separation of concerns
public class UserService {
    private UserRepository repository;
    private EmailService emailService;
    private PasswordEncoder passwordEncoder;
    
    public void createUser(UserDTO dto) {
        User user = new User(dto);
        repository.save(user);
        emailService.sendWelcomeEmail(user);
    }
}

public class EmailService {
    public void sendWelcome(User user) { }
}

public class PasswordEncoder {
    public String hash(String password) { }
    public boolean verify(String raw, String hash) { }
}
```

**5. Null Pointer Nightmare**
```java
// âŒ BAD: Null checks everywhere
public String getUserCity(User user) {
    if (user != null) {
        Address address = user.getAddress();
        if (address != null) {
            City city = address.getCity();
            if (city != null) {
                return city.getName();
            }
        }
    }
    return "Unknown";
}

//  GOOD: Optional usage
public String getUserCity(User user) {
    return Optional.ofNullable(user)
        .map(User::getAddress)
        .map(Address::getCity)
        .map(City::getName)
        .orElse("Unknown");
}

//  BEST: Null Object Pattern
public class Address {
    public static final Address EMPTY = new Address("Unknown", "Unknown");
    
    private String street;
    private String city;
}

public class User {
    private Address address = Address.EMPTY; // Never null
}
```

**6. Exception Swallowing**
```java
// âŒ BAD: Silent failure
public User getUser(Long id) {
    try {
        return database.findById(id);
    } catch (Exception e) {
        return null; // ERROR LOST!
    }
}

// âŒ WORSE: Print stack trace
public User getUser(Long id) {
    try {
        return database.findById(id);
    } catch (Exception e) {
        e.printStackTrace(); // Don't do this!
        return null;
    }
}

//  GOOD: Log and rethrow or handle
public User getUser(Long id) {
    try {
        return database.findById(id);
    } catch (DataAccessException e) {
        logger.error("Failed to load user {}: {}", id, e.getMessage(), e);
        throw new UserNotFoundException("User not found: " + id, e);
    }
}
```

**7. String Concatenation in Loops**
```java
// âŒ BAD: Creates many String objects
public String buildReport(List<String> items) {
    String report = "";
    for (String item : items) {
        report = report + item + "\n"; // Creates new String each time!
    }
    return report;
}

//  GOOD: Use StringBuilder
public String buildReport(List<String> items) {
    StringBuilder report = new StringBuilder();
    for (String item : items) {
        report.append(item).append("\n");
    }
    return report.toString();
}

//  BEST: Use Streams
public String buildReport(List<String> items) {
    return items.stream()
        .collect(Collectors.joining("\n"));
}
```

**8. Not Closing Resources**
```java
// âŒ BAD: Resource leak
public String readFile(String path) {
    FileReader reader = new FileReader(path);
    BufferedReader br = new BufferedReader(reader);
    // If exception occurs, reader never closes!
    return br.readLine();
}

//  GOOD: Try-with-resources
public String readFile(String path) throws IOException {
    try (BufferedReader br = new BufferedReader(new FileReader(path))) {
        return br.readLine();
    } // Automatically closed
}
```"

---

**Do's Summary:**
 Keep methods short (<20 lines)
 Use meaningful variable names
 Follow SOLID principles
 Use Optional to avoid null checks
 Use try-with-resources
 Extract magic numbers to constants
 Use early returns/guard clauses
 Write unit tests

**Don'ts Summary:**
âŒ Don't create God classes
âŒ Don't nest ifs more than 2 levels
âŒ Don't swallow exceptions
âŒ Don't use magic numbers
âŒ Don't ignore compiler warnings
âŒ Don't concatenate strings in loops
âŒ Don't return null - use Optional
âŒ Don't use raw types (List vs List<String>)

---

## ðŸŒ± Spring Framework Core Concepts

### Q10: "Explain Dependency Injection and Inversion of Control in Spring"

**Answer:**
"**Inversion of Control (IoC)** means the framework controls object creation, not the application.
**Dependency Injection (DI)** is how IoC is implemented - Spring injects dependencies instead of objects creating them.

**âŒ BAD: Manual Dependency Creation (Tight Coupling)**
```java
public class UserService {
    private UserRepository repository = new UserRepository(); // Tightly coupled
    private EmailService emailService = new EmailService();
    
    public void createUser(User user) {
        repository.save(user);
        emailService.send(user.getEmail(), "Welcome!");
    }
}
```

** GOOD: Constructor Injection (Recommended)**
```java
@Service
public class UserService {
    private final UserRepository repository;
    private final EmailService emailService;
    
    // Spring automatically injects dependencies
    @Autowired // Optional since Spring 4.3 for single constructor
    public UserService(UserRepository repository, EmailService emailService) {
        this.repository = repository;
        this.emailService = emailService;
    }
    
    public void createUser(User user) {
        repository.save(user);
        emailService.sendWelcomeEmail(user);
    }
}
```

**Types of Dependency Injection:**

**1. Constructor Injection (Best Practice)**
```java
//  BEST: Immutable, required dependencies, easy to test
@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    
    public OrderService(OrderRepository orderRepository, 
                       PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.paymentService = paymentService;
    }
}

// Easy to test
@Test
public void testOrderService() {
    OrderRepository mockRepo = Mockito.mock(OrderRepository.class);
    PaymentService mockPayment = Mockito.mock(PaymentService.class);
    OrderService service = new OrderService(mockRepo, mockPayment);
    // Test without Spring container
}
```

**2. Setter Injection (Optional Dependencies)**
```java
//  GOOD for optional dependencies
@Service
public class NotificationService {
    private EmailService emailService;
    private SmsService smsService; // Optional
    
    @Autowired
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }
    
    @Autowired(required = false) // Optional dependency
    public void setSmsService(SmsService smsService) {
        this.smsService = smsService;
    }
}
```

**3. Field Injection (Not Recommended)**
```java
// âŒ BAD: Can't test without Spring, mutable fields
@Service
public class UserService {
    @Autowired
    private UserRepository repository; // Avoid field injection
    
    @Autowired
    private EmailService emailService;
}
// Hard to test, can't enforce immutability
```

**Real Example from My Project:**
```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}
```

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
       bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// application.properties
spring.datasource.url=jdbc:mysql://localhost:3306/mydb
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.hibernate.ddl-auto=update

// That's it! Spring Boot auto-configures everything
```

**Spring Boot Auto-Configuration Magic:**
```java
// If spring-boot-starter-web on classpath:
// - Configures DispatcherServlet
// - Configures embedded Tomcat
// - Configures Jackson for JSON
// - Configures error handling

// If spring-boot-starter-data-jpa on classpath:
// - Configures DataSource
// - Configures EntityManagerFactory
// - Configures TransactionManager
// - Enables @Transactional

// If spring-boot-starter-security on classpath:
// - Configures security filters
// - Generates default password
// - Secures all endpoints
```

**Spring Boot Starters:**
```xml
<!-- Single dependency includes everything needed -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- Includes: spring-web, spring-webmvc, tomcat, jackson, validation -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<!-- Includes: spring-data-jpa, hibernate, jdbc, transaction -->

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- Includes: spring-security-core, spring-security-web, spring-security-config -->
```

**Real Example - My Microservice:**
```java
@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableCaching
public class EvaluationServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(EvaluationServiceApplication.class, args);
    }
    
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(10))
            .build();
    }
}

// application.yml - all configuration in one place
spring:
  application:
    name: evaluation-service
  datasource:
    url: jdbc:postgresql://localhost:5432/evaluationdb
    username: ${DB_USERNAME:admin}
    password: ${DB_PASSWORD:password}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  kafka:
    bootstrap-servers: localhost:9092
    consumer:
      group-id: evaluation-group
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer

server:
  port: 8081
  
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```"

---

### Q14: "Explain @SpringBootApplication annotation"

**Answer:**
"**@SpringBootApplication** is a convenience annotation that combines three annotations:

```java
@SpringBootApplication
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
    }
}

// Equivalent to:
@Configuration        // Marks class as source of bean definitions
@EnableAutoConfiguration  // Enable Spring Boot's auto-configuration
@ComponentScan        // Scan for components in this package and sub-packages
public class MyApplication {
    // ...
}
```

**1. @Configuration - Java-based Configuration**
```java
@Configuration
public class AppConfig {
    
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
        config.setUsername("admin");
        config.setMaximumPoolSize(20);
        return new HikariDataSource(config);
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

**2. @EnableAutoConfiguration - Automatic Configuration**
```java
// Spring Boot automatically configures beans based on:
// - Dependencies on classpath
// - Existing bean definitions
// - Properties in application.properties

// Example: If you have H2 database on classpath:
// - Automatically configures DataSource
// - Automatically configures EntityManagerFactory
// - No manual configuration needed!

// Disable specific auto-configurations:
@SpringBootApplication(exclude = {
    DataSourceAutoConfiguration.class,
    SecurityAutoConfiguration.class
})
public class MyApplication { }
```

**3. @ComponentScan - Component Discovery**
```java
// Scans package and sub-packages for:
// @Component, @Service, @Repository, @Controller

// Default: scans package of @SpringBootApplication class
@SpringBootApplication
public class MyApplication { } // Scans com.example.* if class is in com.example

// Custom scan:
@SpringBootApplication(scanBasePackages = {
    "com.example.myapp",
    "com.example.common"
})
public class MyApplication { }

// Exclude specific components:
@SpringBootApplication(
    scanBasePackageClasses = {MyApplication.class},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = LegacyService.class
    )
)
```

**Real Example - Multi-Module Project:**
```java
// Parent module: demo
// Sub-modules: common, evaluation-service, sampling-service

// evaluation-service/src/main/java/com/example/evaluation/EvaluationServiceApplication.java
@SpringBootApplication(scanBasePackages = {
    "com.example.evaluation",  // Scan this module
    "com.example.common"       // Scan common module
})
@EnableJpaRepositories("com.example.evaluation.repository")
@EntityScan("com.example.evaluation.entity")
public class EvaluationServiceApplication {
    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(EvaluationServiceApplication.class);
        
        // Set default properties
        app.setDefaultProperties(Map.of(
            "spring.application.name", "evaluation-service",
            "server.port", "8081"
        ));
        
        app.run(args);
    }
}
```"

---

##  Spring Boot Essentials

### Q13: "What is Spring Boot and how is it different from Spring?"

**Answer:**
"**Spring Framework:**
- Core framework for DI, IoC, AOP
- Requires extensive XML or Java configuration
- Manual configuration for every component
- Complex setup for web applications

**Spring Boot:**
- Opinionated framework built on top of Spring
- Auto-configuration based on classpath
- Embedded servers (Tomcat, Jetty)
- Production-ready features (Actuator, Metrics)

**âŒ Traditional Spring Configuration:**
```xml
<!-- web.xml -->
<servlet>
    <servlet-name>dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
</servlet>

<!-- applicationContext.xml -->
<beans>
    <bean id="dataSource" class="org.apache.commons.dbcp.BasicDataSource">
        <property name="driverClassName" value="com.mysql.jdbc.Driver"/>
        <property name="url" value="jdbc:mysql://localhost:3306/mydb"/>
        <property name="username" value="root"/>
        <property name="password" value="password"/>
    </bean>
    
    <bean id="sessionFactory" class="org.springframework.orm.hibernate5.LocalSessionFactoryBean">
        <property name="dataSource" ref="dataSource"/>
    </bean>
    
    <!-- 50+ more bean definitions... -->
</beans>
```

** Spring Boot - Zero Configuration:**
```java
@SpringBootApplication // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class MyApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args);
   