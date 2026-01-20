# Audit Service Quick Start Guide

## Overview
The audit service has been successfully integrated into all microservices. This guide will help you get started quickly.

## What Was Done

### 1. Created Audit Service (Port 8086)
A new microservice that:
- Listens to Kafka topic `audit-events`
- Stores audit logs in H2/PostgreSQL database
- Provides REST API for querying audit logs
- Captures 22 fields including request/response bodies, execution time, correlation IDs

### 2. Enabled Audit Logging in All Services
Added `audit.enabled: true` to:
- evaluation-service (8081)
- sampling-service (8082)
- evidence-service (8083)
- remediation-service (8084)
- jira-service (8085)

### 3. Created AOP Interceptor in Common Module
- `AuditInterceptor.java` - Automatically captures all API requests/responses
- `ContentCachingFilter.java` - Enables request/response body capture
- `AuditWebConfig.java` - Conditional configuration based on `audit.enabled` property

### 4. Updated Deployment Configuration
- Added audit-service to `docker-compose.yml`
- Added Kafka and Zookeeper to `docker-compose.yml`
- Updated `start-all.ps1` script

## Quick Start

### Option 1: Docker (Recommended)

```powershell
# Build and start all services including Kafka and audit-service
.\start-all.ps1
```

This will:
1. Build all services with Maven
2. Build Docker images
3. Start Kafka, Zookeeper, and all microservices
4. Audit service will be available at http://localhost:8086

### Option 2: Manual Start

```powershell
# Step 1: Start Kafka (if not using Docker)
# Follow KAFKA-SETUP.md

# Step 2: Create audit topic
.\create-audit-topic.ps1

# Step 3: Start audit service
cd audit-service
mvn spring-boot:run

# Step 4: Start other services
# Each service in a separate terminal
cd evaluation-service
mvn spring-boot:run

cd sampling-service
mvn spring-boot:run

# ... repeat for other services
```

## Testing Audit Service

### Run Automated Test
```powershell
.\test-audit-service.ps1
```

This script will:
1. Check health of all services
2. Make test API calls with a correlation ID
3. Wait for Kafka processing
4. Query audit logs in various ways
5. Display distributed trace and statistics

### Manual Testing

#### 1. Make an API Call
```powershell
$headers = @{
    "Content-Type" = "application/json"
    "X-Correlation-ID" = "test-123"
    "X-User-Name" = "john.doe"
}

Invoke-RestMethod -Uri "http://localhost:8081/api/evaluations" -Method GET -Headers $headers
```

#### 2. View Audit Logs (wait 2-3 seconds for Kafka processing)
```powershell
# All logs
Invoke-RestMethod -Uri "http://localhost:8086/api/audit?page=0&size=10"

# By correlation ID (distributed tracing)
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/trace/test-123"

# By service
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/service/evaluation-service"

# Failed requests
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/failures"

# Slow requests (>1000ms)
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/slow?threshold=1000"

# Statistics
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/statistics"
```

## API Endpoints

### Audit Service (Port 8086)

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/audit` | GET | Get all audit logs (paginated) |
| `/api/audit/service/{name}` | GET | Get logs by service name |
| `/api/audit/user/{username}` | GET | Get logs by username |
| `/api/audit/trace/{correlationId}` | GET | Get distributed trace |
| `/api/audit/search` | GET | Advanced search with filters |
| `/api/audit/failures` | GET | Get failed requests |
| `/api/audit/slow?threshold={ms}` | GET | Get slow requests |
| `/api/audit/statistics` | GET | Get service statistics |
| `/api/audit/health` | GET | Health check |

### Query Parameters for Pagination
- `page` - Page number (default: 0)
- `size` - Page size (default: 20)

### Advanced Search Parameters
- `serviceName` - Filter by service
- `httpMethod` - Filter by HTTP method
- `endpoint` - Filter by endpoint
- `username` - Filter by username
- `startDate` - Start date (ISO 8601 format)
- `endDate` - End date (ISO 8601 format)
- `minDuration` - Minimum execution time (ms)
- `maxDuration` - Maximum execution time (ms)
- `success` - Filter by success status (true/false)

## Audit Log Fields

Each audit log contains:
1. **serviceName** - Name of the microservice
2. **httpMethod** - GET, POST, PUT, DELETE, etc.
3. **endpoint** - API endpoint
4. **requestBody** - Request payload
5. **responseBody** - Response payload
6. **httpStatusCode** - HTTP status code
7. **executionTimeMs** - Request duration in milliseconds
8. **username** - Authenticated user (from JWT or session)
9. **clientIp** - Client IP address
10. **correlationId** - Distributed tracing ID
11. **timestamp** - Request timestamp
12. **requestHeaders** - HTTP headers (sensitive data redacted)
13. **responseHeaders** - Response headers
14. **queryParams** - URL query parameters
15. **pathVariables** - Path variables
16. **userAgent** - User agent string
17. **sessionId** - Session ID
18. **referrer** - HTTP referer
19. **errorMessage** - Error message (if failed)
20. **stackTrace** - Exception stack trace (if failed)
21. **success** - Success flag
22. **id** - Unique audit log ID

## Security Features

### Sensitive Data Redaction
The following headers are automatically redacted:
- `Authorization` → `[REDACTED]`
- `Cookie` → `[REDACTED]`
- `X-API-Key` → `[REDACTED]`

### Distributed Tracing
- Send `X-Correlation-ID` header in requests
- Use same correlation ID across microservices
- Query by correlation ID to see complete request flow

### User Tracking
- Send `X-User-Name` header in requests
- Or use JWT token (username extracted automatically)
- Query by username to see all user activities

## Performance Characteristics

### Kafka Configuration
- **Topic**: audit-events
- **Partitions**: 12 (for parallel processing)
- **Retention**: 7 days
- **Compression**: Snappy
- **Replication Factor**: 1 (single broker)

### Consumer Configuration
- **Concurrency**: 3 consumer threads
- **Max Poll Records**: 500
- **Batch Processing**: Yes

### Expected Throughput
- **Event Publishing**: 10,000 - 50,000 events/sec
- **Database Inserts**: 5,000 inserts/sec (PostgreSQL)
- **Query Performance**: <100ms for indexed queries

## Troubleshooting

### Audit Logs Not Appearing

1. **Check Kafka is running**
   ```powershell
   docker ps | grep kafka
   # or
   jps | grep Kafka
   ```

2. **Check audit topic exists**
   ```powershell
   .\create-audit-topic.ps1
   ```

3. **Check audit-service is running**
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8086/api/audit/health"
   ```

4. **Check audit is enabled in microservice**
   ```yaml
   # application.yaml
   audit:
     enabled: true
   ```

5. **Check Kafka consumer logs**
   ```powershell
   docker logs audit-service
   # or check: audit-service console output
   ```

### Slow Audit Service

1. **Check database connection**
   - H2 (dev): file-based, slower than PostgreSQL
   - PostgreSQL (prod): recommended for production

2. **Check Kafka consumer lag**
   ```powershell
   kafka-consumer-groups.bat --bootstrap-server localhost:9092 --describe --group audit-service-group
   ```

3. **Increase consumer threads**
   ```yaml
   # audit-service/application.properties
   spring.kafka.listener.concurrency=5  # Increase from 3
   ```

4. **Enable batch processing**
   ```yaml
   spring.kafka.consumer.max-poll-records=1000  # Increase from 500
   ```

### Out of Memory

1. **Limit request/response body size** (not yet implemented)
   - Consider truncating large payloads
   - Store full payload in S3 and reference in audit log

2. **Reduce retention period**
   ```yaml
   # Kafka topic retention
   retention.ms=259200000  # 3 days instead of 7
   ```

3. **Archive old audit logs**
   - Create scheduled job to move old logs to archive database
   - Delete logs older than X days

## Use Cases

### 1. Security Investigation
```powershell
# Find all failed login attempts
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/search?endpoint=/api/auth/login&success=false"

# Track suspicious user activity
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/user/suspicious.user"
```

### 2. Performance Troubleshooting
```powershell
# Find slow requests
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/slow?threshold=2000"

# Get performance statistics by service
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/statistics"
```

### 3. Distributed Tracing
```powershell
# Track a request across multiple services
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/trace/your-correlation-id"
```

### 4. Compliance Auditing
```powershell
# Get all API calls in a date range
$startDate = "2024-01-01T00:00:00"
$endDate = "2024-01-31T23:59:59"
Invoke-RestMethod -Uri "http://localhost:8086/api/audit/search?startDate=$startDate&endDate=$endDate"

# Export to CSV for compliance report
$logs = Invoke-RestMethod -Uri "http://localhost:8086/api/audit?size=1000"
$logs.content | Export-Csv -Path "audit-report.csv" -NoTypeInformation
```

## Next Steps

1. **Enable in Production**: Update `application.properties` with PostgreSQL connection
2. **Configure Kafka**: Set up Kafka cluster for production
3. **Set Up Monitoring**: Add Prometheus/Grafana for audit service metrics
4. **Configure Archival**: Set up automated archival of old audit logs
5. **Add Alerting**: Configure alerts for failed requests, slow requests, etc.

## Configuration Files

### Enable Audit in a Microservice
```yaml
# src/main/resources/application.yaml
audit:
  enabled: true  # Set to false to disable
```

### Audit Service Configuration
```properties
# audit-service/src/main/resources/application.properties
server.port=8086

# Database (H2 for dev)
spring.datasource.url=jdbc:h2:file:./data/audit-db
spring.datasource.driver-class-name=org.h2.Driver

# Kafka Consumer
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=audit-service-group
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.max-poll-records=500
spring.kafka.listener.concurrency=3
```

### Production Configuration (PostgreSQL)
```properties
# PostgreSQL instead of H2
spring.datasource.url=jdbc:postgresql://localhost:5432/auditdb
spring.datasource.username=audit_user
spring.datasource.password=your_password
spring.datasource.driver-class-name=org.postgresql.Driver
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# Kafka cluster
spring.kafka.bootstrap-servers=kafka1:9092,kafka2:9092,kafka3:9092
```

## Scripts

- `start-all.ps1` - Build and start all services with Docker
- `create-audit-topic.ps1` - Create Kafka audit topic
- `test-audit-service.ps1` - Run automated tests

## Documentation

- `AUDIT-SERVICE-README.md` - Comprehensive audit service documentation
- `KAFKA-SETUP.md` - Kafka setup guide
- Docker Compose includes Kafka and Zookeeper

## Support

For issues or questions:
1. Check service logs: `docker logs audit-service`
2. Check Kafka logs: `docker logs kafka`
3. Verify topic exists: `.\create-audit-topic.ps1`
4. Run health checks: `Invoke-RestMethod -Uri "http://localhost:8086/api/audit/health"`
