# CompletableFuture Implementation - IO vs CPU Task Separation

## Overview
This implementation demonstrates proper separation of **IO-bound** and **CPU-bound** tasks using `CompletableFuture` with dedicated thread pools.

## Architecture

### Thread Pool Configuration

Located in: `common/src/main/java/com/example/common/config/AsyncExecutorConfig.java`

#### 1. IO Task Executor (`ioTaskExecutor`)
- **Purpose**: Handles I/O-bound operations
- **Thread Configuration**:
  - Core Pool Size: 20 threads
  - Max Pool Size: 50 threads
  - Queue Capacity: 1000 tasks
- **Use Cases**:
  - Database operations (SELECT, INSERT, UPDATE, DELETE)
  - Network calls (REST API, WebClient)
  - Kafka message publishing
  - File I/O operations
  - External service calls

#### 2. CPU Task Executor (`cpuTaskExecutor`)
- **Purpose**: Handles CPU-intensive operations
- **Thread Configuration**:
  - Core Pool Size: Number of available processors
  - Max Pool Size: 2× available processors
  - Queue Capacity: 500 tasks
- **Use Cases**:
  - Data transformation (Entity → DTO)
  - Business logic calculations
  - Data aggregation and processing
  - Complex validations
  - Algorithm computations

#### 3. Default Task Executor (`taskExecutor`)
- **Purpose**: General-purpose async operations
- **Thread Configuration**:
  - Core Pool Size: 10 threads
  - Max Pool Size: 20 threads
  - Queue Capacity: 500 tasks

---

## Implementation Details

### 1. Async User Service
**File**: `common/src/main/java/com/example/common/service/AsyncUserService.java`

#### Key Methods:

##### IO Tasks (Database Operations)
```java
// Fetch all users (IO)
CompletableFuture<List<User>> getAllUsersAsync()

// Get user by ID (IO)
CompletableFuture<User> getUserByIdAsync(Long id)

// Get user by username (IO)
CompletableFuture<User> getUserByUsernameAsync(String username)
```

##### CPU Tasks (Data Processing)
```java
// Convert users to DTOs (CPU)
CompletableFuture<List<UserDTO>> processUsersToDTO(List<User> users)
```

##### Combined Operations (IO + CPU)
```java
// Fetch and process users
CompletableFuture<List<UserDTO>> getAllUsersWithProcessing()

// Get user by ID and convert to DTO
CompletableFuture<UserDTO> getUserByIdWithProcessing(Long id)
```

##### Complex Multi-Step Operation
```java
CompletableFuture<UserDTO> createUserAsync(UserCreateRequest request)
```
**Pipeline**:
1. **IO (Parallel)**: Validate username and email existence
2. **CPU**: Create entity from request
3. **IO**: Save to database
4. **IO**: Publish Kafka event (fire-and-forget)
5. **CPU**: Convert entity to DTO

---

### 2. Async Controller
**File**: `common/src/main/java/com/example/common/controller/AsyncUserController.java`

**Base URL**: `/api/async/users`

#### Endpoints:

| Method | Endpoint | Description | Tasks |
|--------|----------|-------------|-------|
| GET | `/api/async/users` | Get all users | IO + CPU |
| GET | `/api/async/users/{id}` | Get user by ID | IO + CPU |
| GET | `/api/async/users/username/{username}` | Get user by username | IO + CPU |
| POST | `/api/async/users` | Create user | IO (parallel) + CPU + IO |
| PUT | `/api/async/users/{id}` | Update user | IO + CPU + IO |
| DELETE | `/api/async/users/{id}` | Delete user | IO |
| POST | `/api/async/users/batch` | Batch process users | Parallel IO + CPU |
| GET | `/api/async/users/{id}/processed` | Get with CPU processing | IO + CPU |

---

### 3. Task Separation Demo Service
**File**: `common/src/main/java/com/example/common/service/TaskSeparationDemoService.java`

Demonstrates various patterns of IO and CPU task combinations.

#### Demo Methods:

##### Pure IO Tasks
- `fetchDataFromDatabase()` - Simulates DB read
- `saveDataToDatabase(List<String>)` - Simulates DB write
- `callExternalAPI(String)` - Simulates external API call

##### Pure CPU Tasks
- `processDataWithCPU(List<String>)` - Data processing
- `aggregateData(List<String>)` - Data aggregation

##### Workflow Patterns

1. **Complete Workflow** (IO → CPU → IO)
```java
CompletableFuture<String> completeWorkflow()
```
- Fetch from DB (IO)
- Process data (CPU)
- Save to DB (IO)

2. **Parallel IO with CPU Aggregation**
```java
CompletableFuture<String> parallelIOWithCPU()
```
- Multiple parallel IO operations
- CPU aggregation of results

3. **Fan-Out Pattern** (1 IO → Multiple CPU)
```java
CompletableFuture<List<String>> fanOutPattern()
```
- Single IO fetch
- Parallel CPU processing of chunks

4. **Complex Pipeline**
```java
CompletableFuture<String> complexPipeline()
```
- Multiple IO and CPU tasks in sequence
- Demonstrates task chaining

---

### 4. Demo Controller
**File**: `common/src/main/java/com/example/common/controller/TaskDemoController.java`

**Base URL**: `/api/demo`

#### Test Endpoints:

| Endpoint | Description | Pattern |
|----------|-------------|---------|
| `/api/demo/io-task` | Test pure IO task | IO only |
| `/api/demo/cpu-task` | Test pure CPU task | IO + CPU |
| `/api/demo/workflow` | Complete workflow | IO → CPU → IO |
| `/api/demo/parallel-io` | Parallel IO operations | Multiple IO + CPU |
| `/api/demo/fan-out` | Fan-out pattern | 1 IO → Multiple CPU |
| `/api/demo/pipeline` | Complex pipeline | IO + CPU + IO + CPU + IO |
| `/api/demo/error-handling` | Error handling test | With exception handling |
| `/api/demo/api-call` | External API call | IO task |
| `/api/demo/aggregate` | Data aggregation | IO + CPU |
| `/api/demo/info` | Demo information | Endpoint documentation |
| `/api/demo/health` | Health check | Service status |

---

## Usage Examples

### Example 1: Simple IO + CPU Operation
```java
// Fetch user (IO) then convert to DTO (CPU)
asyncUserService.getUserByIdAsync(1L)
    .thenApplyAsync(EntityMapper::toDTO, cpuExecutor)
    .thenAccept(dto -> System.out.println("User: " + dto));
```

### Example 2: Parallel Operations
```java
// Fetch multiple users in parallel
List<CompletableFuture<User>> futures = userIds.stream()
    .map(id -> asyncUserService.getUserByIdAsync(id))
    .collect(Collectors.toList());

// Wait for all and process
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
    .thenApplyAsync(v -> {
        return futures.stream()
            .map(CompletableFuture::join)
            .map(EntityMapper::toDTO)
            .collect(Collectors.toList());
    }, cpuExecutor);
```

### Example 3: Complex Pipeline
```java
asyncUserService.createUserAsync(request)  // IO + CPU + IO
    .thenApply(user -> {
        // Additional CPU processing
        return processUser(user);
    })
    .thenCompose(processed -> {
        // Additional IO operation
        return saveAuditLog(processed);
    })
    .exceptionally(ex -> {
        // Error handling
        return handleError(ex);
    });
```

---

## Testing the Implementation

### 1. Test Async User Endpoints

#### Get all users (async)
```bash
curl -X GET http://localhost:8081/api/async/users
```

#### Create user (async with parallel validations)
```bash
curl -X POST http://localhost:8081/api/async/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "async_user",
    "email": "async@example.com",
    "firstName": "Async",
    "lastName": "User",
    "phoneNumber": "+1234567890",
    "department": "IT",
    "salary": 75000
  }'
```

#### Batch processing
```bash
curl -X POST http://localhost:8081/api/async/users/batch \
  -H "Content-Type: application/json" \
  -d '[1, 2, 3, 4, 5]'
```

### 2. Test Demo Endpoints

#### Complete workflow (IO → CPU → IO)
```bash
curl -X GET http://localhost:8081/api/demo/workflow
```

#### Parallel IO operations
```bash
curl -X GET http://localhost:8081/api/demo/parallel-io
```

#### Fan-out pattern
```bash
curl -X GET http://localhost:8081/api/demo/fan-out
```

#### Complex pipeline
```bash
curl -X GET http://localhost:8081/api/demo/pipeline
```

#### Get demo info
```bash
curl -X GET http://localhost:8081/api/demo/info
```

---

## Performance Benefits

### 1. Thread Pool Separation
- **IO tasks** don't block CPU-intensive operations
- **CPU tasks** don't wait for I/O operations
- Better resource utilization

### 2. Parallel Execution
- Multiple IO operations execute concurrently
- Database queries run in parallel
- API calls don't block each other

### 3. Non-Blocking APIs
- Spring automatically unwraps CompletableFuture
- HTTP threads released immediately
- Higher throughput under load

### 4. Scalability
- IO pool scales based on concurrent connections
- CPU pool scales based on available processors
- Independent scaling of different workload types

---

## Best Practices

### 1. Task Classification
- **IO Tasks**: Database, Network, File I/O, Kafka, Redis
- **CPU Tasks**: Calculations, Transformations, Validations, Business Logic

### 2. Thread Pool Selection
```java
// Use ioExecutor for I/O operations
CompletableFuture.supplyAsync(() -> repository.findAll(), ioExecutor)

// Use cpuExecutor for processing
CompletableFuture.supplyAsync(() -> processData(data), cpuExecutor)
```

### 3. Error Handling
```java
future
    .exceptionally(ex -> {
        logger.error("Operation failed", ex);
        return defaultValue;
    })
    .thenApply(result -> processResult(result));
```

### 4. Avoid Blocking
```java
// ❌ Bad - blocks thread
future.get()

// ✅ Good - non-blocking callback
future.thenApply(result -> processResult(result))
```

### 5. Resource Cleanup
```java
// Thread pools configured with graceful shutdown
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(60);
```

---

## Configuration Tuning

### For High I/O Workloads
```java
// Increase IO pool size
executor.setCorePoolSize(50);
executor.setMaxPoolSize(100);
```

### For CPU-Intensive Workloads
```java
// Adjust based on processors
int processors = Runtime.getRuntime().availableProcessors();
executor.setCorePoolSize(processors);
executor.setMaxPoolSize(processors * 2);
```

### For Mixed Workloads
- Monitor thread pool metrics
- Adjust queue capacities
- Balance between IO and CPU pools

---

## Monitoring

### Thread Pool Metrics
- Active threads count
- Queue size
- Task completion rate
- Task rejection rate

### Application Metrics
- Request latency
- Throughput (requests/sec)
- Error rate
- Resource utilization

---

## Available in Services

The async functionality is available in the **common** module and can be used by all services:

- ✅ evaluation-service (port 8081)
- ✅ sampling-service (port 8082)
- ✅ evidence-service (port 8083)
- ✅ remediation-service (port 8084)
- ✅ jira-service (port 8085)

---

## Swagger Documentation

Access API documentation at:
- http://localhost:8081/swagger-ui.html (evaluation-service)
- http://localhost:8082/swagger-ui.html (sampling-service)
- http://localhost:8083/swagger-ui.html (evidence-service)
- http://localhost:8084/swagger-ui.html (remediation-service)
- http://localhost:8085/swagger-ui.html (jira-service)

---

## Conclusion

This implementation provides:
- ✅ Clear separation of IO and CPU tasks
- ✅ Dedicated thread pools for different workload types
- ✅ Non-blocking async APIs
- ✅ Parallel execution support
- ✅ Comprehensive error handling
- ✅ Production-ready configuration
- ✅ Easy to test and monitor
