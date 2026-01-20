# Testing Guide: Multi-Role Authentication & Resilience4j

## Summary

Successfully implemented and built:

### ✅ Completed Features

1. **Multi-Role Authentication (4 levels)**
   - ADMIN (full access)
   - OPERATOR (operations management)
   - REVIEWER (review tasks)
   - USER (basic access)

2. **Resilience4j Patterns**
   - Circuit Breaker with fallback
   - Retry with exponential backoff
   - Timeout (Time Limiter)
   - Bulkhead (concurrent call limiting)

3. **Configuration**
   - Fixed YAML configuration
   - JJWT 0.12.3 API compatibility
   - RestTemplate with timeouts
   - Actuator endpoints for monitoring

### ⚠️ Current Status

- **Build**: ✅ SUCCESS (common & evaluation-service compiled)
- **Application**: Started successfully on port 8081
- **Issue**: Application requires Kafka to run fully
- **Resolution**: Need to either:
  1. Start Kafka broker on localhost:9092, OR
  2. Run with Docker (docker-compose.yml), OR
  3. Use existing startup scripts (start-all.ps1)

---

## Testing Steps (Once Kafka is Running)

### 1. Start the Application

**Option A: With Docker**
```powershell
docker-compose up -d
```

**Option B: With existing scripts**
```powershell
.\start-all.ps1
```

**Option C: Standalone evaluation-service**
```powershell
cd evaluation-service\target
java -jar evaluation-service-0.0.1-SNAPSHOT.jar --server.port=8081
```

### 2. Test Multi-Role Authentication

#### Test Admin User (All Roles)
```powershell
# Login as admin
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
    -Method Post `
    -Body (@{username='admin';password='*******'}|ConvertTo-Json) `
    -ContentType 'application/json'

$token = $response.token
Write-Host "Admin Token: $token"
Write-Host "Roles: $($response.roles -join ', ')"

# Test admin endpoint
$headers = @{Authorization="Bearer $token"}
Invoke-RestMethod -Uri "http://localhost:8081/api/admin/dashboard" -Headers $headers
```

#### Test Operator User
```powershell
# Login as operator
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
    -Method Post `
    -Body (@{username='operator';password='operator123'}|ConvertTo-Json) `
    -ContentType 'application/json'

$token = $response.token
Write-Host "Operator Roles: $($response.roles -join ', ')"

# Test operator endpoint (should succeed)
$headers = @{Authorization="Bearer $token"}
Invoke-RestMethod -Uri "http://localhost:8081/api/operator/dashboard" -Headers $headers

# Test admin endpoint (should fail with 403)
try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/admin/dashboard" -Headers $headers
} catch {
    Write-Host "Expected 403 Forbidden: $_"
}
```

#### Test Reviewer User
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
    -Method Post `
    -Body (@{username='reviewer';password='reviewer123'}|ConvertTo-Json) `
    -ContentType 'application/json'

$token = $response.token
$headers = @{Authorization="Bearer $token"}

# Should succeed
Invoke-RestMethod -Uri "http://localhost:8081/api/reviewer/dashboard" -Headers $headers

# Should fail (403)
try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/operator/dashboard" -Headers $headers
} catch {
    Write-Host "Expected 403: Reviewer cannot access operator endpoints"
}
```

#### Test Regular User
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
    -Method Post `
    -Body (@{username='user';password='user123'}|ConvertTo-Json) `
    -ContentType 'application/json'

$token = $response.token
$headers = @{Authorization="Bearer $token"}

# Should succeed
Invoke-RestMethod -Uri "http://localhost:8081/api/users/profile" -Headers $headers

# Should fail (403)
try {
    Invoke-RestMethod -Uri "http://localhost:8081/api/reviewer/dashboard" -Headers $headers
} catch {
    Write-Host "Expected 403: User cannot access reviewer endpoints"
}
```

### 3. Test Resilience4j Patterns

#### Get Authentication Token First
```powershell
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/auth/login" `
    -Method Post `
    -Body (@{username='admin';password='*******'}|ConvertTo-Json) `
    -ContentType 'application/json'

$headers = @{Authorization="Bearer $($response.token)"}
```

#### Test Circuit Breaker
```powershell
# Should succeed (ID < 100)
Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/circuit-breaker/sampling/50" `
    -Headers $headers

# Should trigger circuit breaker (ID > 100)
# Call 5+ times to open circuit
1..6 | ForEach-Object {
    try {
        $result = Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/circuit-breaker/sampling/101" `
            -Headers $headers
        Write-Host "Attempt $_: $($result.message)"
    } catch {
        Write-Host "Attempt $_: Circuit might be open - $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 1
}

# Check circuit breaker state
Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakers" -Headers $headers
```

#### Test Retry Pattern
```powershell
# This will retry multiple times before succeeding/failing
$result = Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/retry/evaluation/123" `
    -Method Post `
    -Headers $headers
Write-Host "Retry Result: $($result.message)"
```

#### Test Timeout Pattern
```powershell
# Odd ID - should succeed quickly
$result = Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/timeout/data/1" `
    -Headers $headers
Write-Host "Quick request: $($result.message)"

# Even ID - will timeout after 2 seconds
try {
    $result = Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/timeout/data/2" `
        -Headers $headers
    Write-Host "Slow request: $($result.message)"
} catch {
    Write-Host "Expected timeout: $($_.Exception.Message)"
}
```

#### Test Bulkhead Pattern
```powershell
# Send 10 concurrent requests (max 5 allowed)
$jobs = 1..10 | ForEach-Object {
    Start-Job -ScriptBlock {
        param($id, $token)
        $headers = @{Authorization="Bearer $token"}
        try {
            Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/bulkhead/jira" `
                -Method Post `
                -Headers $headers
        } catch {
            $_.Exception.Message
        }
    } -ArgumentList $_, $response.token
}

# Wait and collect results
$results = $jobs | Wait-Job | Receive-Job
$results | ForEach-Object { Write-Host $_ }
$jobs | Remove-Job
```

#### Test Combined Pattern
```powershell
# Tests all patterns together
$result = Invoke-RestMethod -Uri "http://localhost:8081/api/resilience/combined/123" `
    -Method Post `
    -Headers $headers
Write-Host "Combined patterns: $($result.message)"
```

### 4. Monitor Resilience4j Metrics

#### Check Circuit Breaker Status
```powershell
$headers = @{Authorization="Bearer $token"}
$cb = Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakers" -Headers $headers
$cb.circuitBreakers | ForEach-Object {
    Write-Host "$($_.name): State=$($_.state), FailureRate=$($_.failureRate)%, BufferedCalls=$($_.bufferedCalls)"
}
```

#### Check Recent Circuit Breaker Events
```powershell
$events = Invoke-RestMethod -Uri "http://localhost:8081/actuator/circuitbreakerevents" -Headers $headers
$events.circuitBreakerEvents | Select-Object -First 10 | ForEach-Object {
    Write-Host "$($_.circuitBreakerName): $($_.type) at $($_.creationTime)"
}
```

#### Check Retry Statistics
```powershell
$retries = Invoke-RestMethod -Uri "http://localhost:8081/actuator/retries" -Headers $headers
$retries.retries | ForEach-Object {
    Write-Host "$($_.name): SuccessfulCalls=$($_.successfulCallsWithRetryAttempt), FailedCalls=$($_.failedCallsWithRetryAttempt)"
}
```

#### Check Retry Events
```powershell
$retryEvents = Invoke-RestMethod -Uri "http://localhost:8081/actuator/retryevents" -Headers $headers
$retryEvents.retryEvents | Select-Object -First 10 | ForEach-Object {
    Write-Host "$($_.retryName): $($_.type) - Attempts: $($_.numberOfRetryAttempts)"
}
```

### 5. Check Application Health
```powershell
$health = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health"
Write-Host "Application Status: $($health.status)"

# Detailed health (requires authentication)
$headers = @{Authorization="Bearer $token"}
$detailedHealth = Invoke-RestMethod -Uri "http://localhost:8081/actuator/health" -Headers $headers
$detailedHealth.components | ConvertTo-Json -Depth 5
```

---

## Implementation Details

### Demo Users (Configured in AuthController)

| Username | Password | Roles |
|----------|----------|-------|
| admin | ******* | ADMIN, OPERATOR, REVIEWER, USER |
| operator | operator123 | OPERATOR, REVIEWER, USER |
| reviewer | reviewer123 | REVIEWER, USER |
| user | user123 | USER |

### Role Hierarchy

```
ADMIN (highest)
  └─ Can access: /api/admin/**, /api/operator/**, /api/reviewer/**, /api/users/**
     
OPERATOR
  └─ Can access: /api/operator/**, /api/reviewer/**, /api/users/**
     
REVIEWER
  └─ Can access: /api/reviewer/**, /api/users/**
     
USER (lowest)
  └─ Can access: /api/users/** only
```

### Resilience4j Configuration

**Circuit Breaker (samplingService)**
- Window: 10 calls (COUNT_BASED)
- Failure threshold: 50%
- Open state duration: 5 seconds
- Half-open calls: 3

**Retry (evaluationService)**
- Max attempts: 4
- Wait duration: 1 second
- Ignores: IllegalArgumentException, BusinessException

**Timeout (samplingService)**
- Timeout duration: 2 seconds
- Cancels running futures: true

**Bulkhead (jiraService)**
- Max concurrent calls: 5
- Max wait duration: 1 second

### JWT Configuration

- Algorithm: HS256
- Expiration: 24 hours (86400000 ms)
- Secret key: Configured in application.yml (change in production)

---

## Troubleshooting

### Application Won't Start

**Issue**: "Connection to node -1 (localhost/127.0.0.1:9092) could not be established"

**Solution**: Start Kafka broker
```powershell
# Option 1: Docker Compose
docker-compose up -d kafka zookeeper

# Option 2: Check if services are defined
cat docker-compose.yml | grep -A 10 "kafka:"
```

### 401 Unauthorized

**Issue**: API calls return 401

**Solution**: 
1. Login first to get JWT token
2. Include token in Authorization header: `Bearer <token>`
3. Check token hasn't expired (24 hours)

### 403 Forbidden

**Issue**: User cannot access endpoint

**Solution**: 
- Check user has required role
- Verify role hierarchy (REVIEWER cannot access OPERATOR endpoints)
- Confirm endpoint security configuration in SecurityConfig.java

### Circuit Breaker Not Opening

**Issue**: Circuit stays closed despite failures

**Solution**:
- Need minimum 5 calls (minimumNumberOfCalls)
- Need >50% failures (failureRateThreshold)
- Check ID parameter: >100 triggers failure in demo

### Metrics Not Showing

**Issue**: Actuator endpoints return 404

**Solution**:
1. Check application.yml has metrics enabled
2. Verify management.endpoints.web.exposure.include has required endpoints
3. May need authentication for some actuator endpoints

---

## Files Modified in This Session

### Core Security
- [common/src/main/java/com/example/common/security/SecurityConfig.java](common/src/main/java/com/example/common/security/SecurityConfig.java) - Multi-role authorization
- [common/src/main/java/com/example/common/security/JwtTokenProvider.java](common/src/main/java/com/example/common/security/JwtTokenProvider.java) - JJWT 0.12.3 compatibility
- [common/src/main/java/com/example/common/controller/AuthController.java](common/src/main/java/com/example/common/controller/AuthController.java) - Multi-user support

### Resilience4j
- [common/src/main/java/com/example/common/service/ResilientService.java](common/src/main/java/com/example/common/service/ResilientService.java) - Pattern implementations
- [common/src/main/java/com/example/common/controller/ResilienceController.java](common/src/main/java/com/example/common/controller/ResilienceController.java) - Test endpoints
- [common/src/main/java/com/example/common/config/RestClientConfig.java](common/src/main/java/com/example/common/config/RestClientConfig.java) - Timeout configuration

### Controllers
- [common/src/main/java/com/example/common/controller/RoleBasedController.java](common/src/main/java/com/example/common/controller/RoleBasedController.java) - Role demonstration

### Configuration
- [common/src/main/resources/application.yml](common/src/main/resources/application.yml) - JWT + Resilience4j config
- [common/pom.xml](common/pom.xml) - Resilience4j dependencies

### Exceptions
- [common/src/main/java/com/example/common/exception/BusinessException.java](common/src/main/java/com/example/common/exception/BusinessException.java) - Business logic exception

### Documentation
- [MULTI-ROLE-GUIDE.md](MULTI-ROLE-GUIDE.md) - Multi-role testing guide
- [RESILIENCE4J-GUIDE.md](RESILIENCE4J-GUIDE.md) - Resilience4j comprehensive guide
- [TEST-IMPLEMENTATION.md](TEST-IMPLEMENTATION.md) - This file

---

## Next Steps

1. **Start Kafka**: Use docker-compose or start-all.ps1 script
2. **Run Tests**: Execute the PowerShell commands above
3. **Monitor Metrics**: Check actuator endpoints for circuit breaker states
4. **Production Hardening**:
   - Change JWT secret key
   - Configure LDAP/AD for real user authentication
   - Adjust Resilience4j thresholds based on load testing
   - Enable HTTPS
   - Configure proper logging levels
   - Set up Prometheus/Grafana for metrics visualization

---

## Build Information

- **Last Build**: Successful
- **Modules**: common-0.0.1-SNAPSHOT.jar, evaluation-service-0.0.1-SNAPSHOT.jar
- **Java Version**: 17.0.17
- **Spring Boot**: 3.2.1
- **JJWT**: 0.12.3
- **Resilience4j**: 2.1.0
