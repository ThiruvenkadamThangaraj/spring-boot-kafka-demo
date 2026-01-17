# Kafka vs Batch Processing: Comprehensive Comparison Guide

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Architecture Overview](#architecture-overview)
3. [Batch Processing Approach](#batch-processing-approach)
4. [Kafka Streaming Approach](#kafka-streaming-approach)
5. [Detailed Comparison](#detailed-comparison)
6. [Decision Framework](#decision-framework)
7. [Implementation Guides](#implementation-guides)
8. [Cost Analysis](#cost-analysis)
9. [Migration Path](#migration-path)

---

## Executive Summary

### Quick Comparison

| Factor | **Batch Processing** | **Kafka Streaming** |
|--------|---------------------|---------------------|
| **Latency** | 24 hours (daily schedule) | 2-5 minutes (near real-time) |
| **Complexity** | Low ⭐ | High ⭐⭐⭐⭐ |
| **Initial Setup** | 2-3 days | 2-3 weeks |
| **Infrastructure** | 1 application server | Kafka cluster (3+ nodes) + consumers |
| **Monthly Cost** | $50-100 | $500-1,500 |
| **Team Skills** | Java, Spring Boot, SQL | + Kafka, CDC, monitoring |
| **Scalability** | Vertical (add threads) | Horizontal (add consumers) |
| **Failure Recovery** | Manual restart | Automatic replay |
| **Best For** | Nightly ETL, reporting | Real-time analytics, live dashboards |

### When to Choose Batch Processing
✅ **Choose Batch if:**
- Data freshness requirement: Once per day is acceptable
- Simple use case: Move data from A to B
- Small team: 1-2 developers maintaining the system
- Budget constrained: Need minimal infrastructure
- Predictable load: Data volume consistent

### When to Choose Kafka
✅ **Choose Kafka if:**
- Real-time requirement: Need data within minutes
- Multiple consumers: 3+ systems need the same data
- Event sourcing: Need audit trail of all changes
- High availability: System must survive failures automatically
- Scalability: Expect 10x growth in next 2 years

---

## Architecture Overview

### Batch Processing Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    BATCH PROCESSING                         │
└─────────────────────────────────────────────────────────────┘

   ┌──────────────┐           ┌──────────────────┐           ┌──────────────┐
   │              │           │                  │           │              │
   │  ITM Source  │◄──────────│  Spring Boot App │──────────►│  CO Target   │
   │   Database   │  SELECT   │  (10 Workers)    │  MERGE    │   Database   │
   │ (SQL Server) │           │                  │           │ (SQL Server) │
   │              │           │ @Scheduled       │           │              │
   └──────────────┘           │ (Cron: 2 AM)     │           └──────────────┘
                              └──────────────────┘
                                      │
                                      ▼
                              ┌──────────────────┐
                              │  Coordination DB │
                              │  (Partitions,    │
                              │   Job History)   │
                              └──────────────────┘

Components:
- 1 Spring Boot application (JAR)
- 3 Database connections (HikariCP pools)
- 10 parallel worker threads (CompletableFuture)
- 1 scheduler (Spring @Scheduled)

Data Flow:
1. Cron triggers at 2 AM daily
2. Coordinator partitions 1B records into 10 segments
3. Each worker claims a partition (thread-safe SQL UPDLOCK)
4. Worker fetches 50,000 records per batch from ITM
5. Worker executes MERGE into CO database
6. Process repeats until all partitions complete
7. Total time: 8-12 minutes for 1 billion records

Failure Handling:
- If app crashes: Restart manually, unclaimed partitions will be picked up
- If database down: Job fails, retry next scheduled run (24 hours)
- If partition fails: Mark as FAILED, manual investigation needed
```

### Kafka Streaming Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    KAFKA STREAMING                          │
└─────────────────────────────────────────────────────────────┘

   ┌──────────────┐           ┌──────────────────────────────┐
   │              │           │   KAFKA CLUSTER (3 nodes)    │
   │  ITM Source  │           │ ┌────────────────────────┐   │
   │   Database   │           │ │  Topic: billing-events │   │
   │ (SQL Server) │           │ │  Partitions: 10        │   │
   │              │           │ │  Replication: 3        │   │
   └──────┬───────┘           │ │  Retention: 7 days     │   │
          │                   │ └────────────────────────┘   │
          │ CDC               └──────────────┬───────────────┘
          │ (Debezium)                       │ Poll
          ▼                                  │ (Batch 1000)
   ┌──────────────┐                         ▼
   │   Debezium   │              ┌────────────────────────┐
   │   Connector  │─────Publish──►│   Consumer Group      │
   │   (CDC)      │   Events     │   (10 consumers)      │
   └──────────────┘              │                        │
          │                      │  @KafkaListener        │
          │                      │  Spring Boot Apps      │
          │                      └───────────┬────────────┘
          │                                  │ Write
          │                                  ▼
          │                          ┌──────────────┐
          │                          │  CO Target   │
          │                          │   Database   │
          └─────Monitor──────────────│ (SQL Server) │
            (Schema Registry)        └──────────────┘

Additional Components:
- Zookeeper (3 nodes) - Kafka coordination
- Schema Registry - Avro schema management
- Kafka Connect - Debezium CDC connectors
- Consumer Applications - 10 Spring Boot instances
- Monitoring Stack - Prometheus + Grafana

Data Flow (Real-Time):
1. Application inserts/updates record in ITM database
2. Debezium CDC captures change from transaction log
3. Change event published to Kafka topic (partitioned by ID)
4. Consumer polls events (1,000 per batch)
5. Consumer writes to CO database (idempotent MERGE)
6. Consumer commits Kafka offset
7. Latency: 2-3 minutes end-to-end

Data Flow (Bulk Load):
1. Batch producer reads 1B historical records
2. Publishes to Kafka at 50,000 records/sec
3. Consumers process in parallel
4. Total time: 5-6 hours for 1 billion records

**Why 5-6 hours?**
- Calculation: 1,000,000,000 records ÷ 50,000 records/sec = 20,000 seconds = 5.56 hours
- Rate-limited to prevent overwhelming consumers and maintain system stability
- Each record must be: serialized (Avro) → compressed (LZ4) → written to 3 Kafka replicas → acknowledged
- Network overhead: Producer → Kafka → Consumer (vs direct database in batch: 8-12 min)
- **Trade-off**: Kafka optimized for real-time streaming (2-3 min latency), not bulk loads
- **Recommendation**: Use direct batch processing for initial 1B load, then switch to Kafka for live records

Failure Handling:
- Consumer crash: Kafka rebalances, another consumer takes over
- Database down: Events buffered in Kafka (7-day retention)
- Network partition: Kafka leader election, transparent failover
- Schema change: Consumers auto-fetch new schema from registry
```

---

## Batch Processing Approach

### Architecture Details

#### Components
1. **Transfer Service Application**
   - Spring Boot 3.2.1 application
   - Embedded Tomcat server (port 8080)
   - RESTful APIs for manual trigger and monitoring

2. **Coordinator Service**
   - `@Scheduled(cron = "0 0 2 * * *")` - Runs daily at 2 AM
   - Creates 10 worker threads using `CompletableFuture`
   - Manages partition lifecycle (PENDING → IN_PROGRESS → COMPLETED)

3. **Worker Threads**
   - Each worker processes 100 million records (1/10th of total)
   - Fetches data in chunks of 50,000 records
   - Uses JDBC batch operations for efficiency

4. **Database Connections**
   - ITM DataSource: 20 connections (read-only)
   - CO DataSource: 20 connections (write)
   - Coordination DataSource: 10 connections (partition tracking)

#### Code Structure
```
transfer-service/
├── src/main/java/com/example/transfer/
│   ├── TransferApplication.java              # Main entry point
│   ├── config/
│   │   └── TransferDataSourceConfig.java     # 3 DataSource beans
│   ├── controller/
│   │   ├── TransferController.java           # Manual trigger API
│   │   └── MonitorController.java            # Monitoring APIs
│   └── service/
│       └── CoordinatorService.java           # Core orchestration logic
└── src/main/resources/
    └── application.properties                # Database configs
```

#### Key Code Snippets

**CoordinatorService.java**
```java
@Scheduled(cron = "0 0 2 * * *")  // Every day at 2 AM
public void scheduledTransfer() {
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (int i = 0; i < 10; i++) {
        final int workerId = i;
        CompletableFuture<Void> future = CompletableFuture.supplyAsync(() -> {
            processPartitionsForWorker(workerId);
            return null;
        }, executor);
        futures.add(future);
    }
    
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    executor.shutdown();
}
```

#### Performance Characteristics
- **Throughput**: 1.5-2 million records/minute
- **Total Time**: 8-12 minutes for 1 billion records
- **CPU Usage**: 60-70% during transfer
- **Memory**: 2-4 GB heap
- **Network**: 100-200 Mbps sustained

#### Monitoring & Observability
- **Health Check**: `GET /api/transfer/health`
- **Progress**: `GET /api/monitor/progress` (returns percentage complete)
- **Partition Status**: `GET /api/monitor/partitions` (JSON array)
- **Worker Status**: `GET /api/monitor/workers` (active thread count)
- **Job History**: `GET /api/monitor/jobs` (last 10 runs)

#### Advantages
✅ **Simple to understand**: Single application, straightforward logic  
✅ **Easy deployment**: One JAR file, one server  
✅ **Low operational cost**: $50-100/month  
✅ **Quick setup**: 2-3 days from zero to production  
✅ **Predictable**: Runs same time every day, known duration  
✅ **Debuggable**: Logs in one place, easy to trace issues  

#### Limitations
❌ **High latency**: 24-hour delay for new data  
❌ **Single point of failure**: If app crashes, manual restart needed  
❌ **Limited scalability**: Can't easily scale beyond 10 workers  
❌ **Resource spike**: High CPU/memory at 2 AM, idle rest of day  
❌ **No replay capability**: Can't reprocess data without manual intervention  

---

## Kafka Streaming Approach

### Architecture Details

#### Components
1. **Kafka Cluster (3 nodes)**
   - Topic: `billing-events` with 10 partitions
   - Replication factor: 3 (fault tolerance)
   - Retention: 7 days (604,800 seconds)
   - Compression: LZ4 (3:1 ratio)

2. **Debezium CDC Connector**
   - Captures INSERT/UPDATE/DELETE from SQL Server transaction log
   - Publishes to Kafka in Avro format
   - Schema managed by Confluent Schema Registry
   - Throughput: 50,000-100,000 events/second

3. **Consumer Applications (10 instances)**
   - Spring Boot with Spring Kafka
   - Consumer group: `billing-consumer-group`
   - Each consumer handles 1 partition
   - Batch size: 1,000 records per poll

4. **Schema Registry**
   - Stores Avro schemas
   - Supports forward/backward compatibility
   - Enables schema evolution

5. **Monitoring Stack**
   - Prometheus: Metrics collection
   - Grafana: Dashboards (lag, throughput, errors)
   - Kafka Manager: Cluster administration
   - PagerDuty: Alerting

#### Code Structure
```
kafka-streaming-project/
├── debezium-connector/
│   ├── connector-config.json          # CDC configuration
│   └── deploy-connector.sh            # Deployment script
├── consumer-service/
│   ├── src/main/java/com/example/consumer/
│   │   ├── ConsumerApplication.java
│   │   ├── config/
│   │   │   └── KafkaConsumerConfig.java    # Consumer configs
│   │   ├── listener/
│   │   │   └── BillingEventListener.java   # @KafkaListener
│   │   └── service/
│   │       └── BillingProcessor.java       # Business logic
│   └── src/main/resources/
│       └── application.yml                  # Kafka bootstrap servers
└── monitoring/
    ├── prometheus.yml
    ├── grafana-dashboards/
    │   └── kafka-consumer-lag.json
    └── alerts/
        └── high-lag-alert.yml
```

#### Key Code Snippets

**BillingEventListener.java**
```java
@KafkaListener(
    topics = "billing-events",
    groupId = "billing-consumer-group",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(@Payload List<BillingEvent> events,
                    Acknowledgment acknowledgment) {
    try {
        // Process batch of 1,000 events
        billingProcessor.processBatch(events);
        
        // Commit offset only after successful processing
        acknowledgment.acknowledge();
        
    } catch (Exception e) {
        log.error("Failed to process events: {}", e.getMessage());
        // Don't acknowledge - Kafka will redeliver
    }
}
```

**KafkaConsumerConfig.java**
```java
@Bean
public ConsumerFactory<String, BillingEvent> consumerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka1:9092,kafka2:9092,kafka3:9092");
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "billing-consumer-group");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
    props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"); // Exactly-once
    props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1000);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    return new DefaultKafkaConsumerFactory<>(props);
}
```

#### Performance Characteristics
- **Latency**: 2-3 minutes (producer → consumer → database)
- **Throughput**: 100,000 events/second (live streaming)
- **Bulk Load**: 50,000 records/second (5-6 hours for 1 billion)
- **Consumer Lag**: < 100,000 messages (normal), alert if > 1 million
- **CPU Usage**: 40-50% steady state
- **Memory**: Kafka brokers: 8 GB each, Consumers: 1 GB each

#### Monitoring Metrics

**Key Metrics to Track:**
1. **Consumer Lag**: `kafka_consumer_records_lag_max`
   - Normal: < 100,000 messages
   - Warning: 100,000 - 1,000,000
   - Critical: > 1,000,000

2. **Throughput**: `kafka_consumer_records_consumed_total`
   - Target: 100,000 records/second

3. **Error Rate**: `kafka_consumer_failed_total`
   - Target: < 0.1%

4. **Offset Commit Time**: `kafka_consumer_commit_latency_avg`
   - Target: < 500ms

#### Advantages
✅ **Real-time**: 2-3 minute latency for live data  
✅ **Fault tolerant**: Automatic failover, no data loss  
✅ **Scalable**: Add consumers to increase throughput  
✅ **Replay capability**: Reprocess last 7 days from Kafka  
✅ **Decoupling**: Source and target systems independent  
✅ **Multiple consumers**: Same data to different systems  
✅ **Audit trail**: Complete history of changes  

#### Limitations
❌ **High complexity**: Many moving parts (Kafka, Zookeeper, CDC, consumers)  
❌ **Operational overhead**: Need 24/7 monitoring and on-call  
❌ **Higher cost**: $500-1,500/month for infrastructure  
❌ **Skill requirement**: Team needs Kafka expertise  
❌ **Longer setup**: 2-3 weeks to production  
❌ **Debugging difficulty**: Distributed tracing required  

---

## Cross-Database Support (Different Database Types)

### Scenario: Source and Target are Different Database Vendors

#### Common Scenarios
1. **SQL Server → PostgreSQL**: Windows legacy system → Cloud migration
2. **Oracle → MySQL**: Enterprise → Open source
3. **MySQL → MongoDB**: Relational → NoSQL
4. **PostgreSQL → Snowflake**: OLTP → OLAP/Analytics

### Batch Processing Approach

#### Configuration for Multiple Database Types

**Example: SQL Server (Source) → PostgreSQL (Target)**

```properties
# application.properties

# ITM Source Database (SQL Server)
itm.datasource.url=jdbc:sqlserver://itm-server:1433;databaseName=ITM
itm.datasource.username=itm_reader
itm.datasource.password=<PASSWORD>
itm.datasource.driver-class-name=com.microsoft.sqlserver.jdbc.SQLServerDriver
itm.datasource.hikari.maximum-pool-size=20

# CO Target Database (PostgreSQL)
co.datasource.url=jdbc:postgresql://co-server:5432/co_db
co.datasource.username=co_writer
co.datasource.password=<PASSWORD>
co.datasource.driver-class-name=org.postgresql.Driver
co.datasource.hikari.maximum-pool-size=20

# Coordination Database (can be either, typically same as target)
coord.datasource.url=jdbc:postgresql://coord-server:5432/coordination
coord.datasource.username=coord_admin
coord.datasource.password=<PASSWORD>
coord.datasource.driver-class-name=org.postgresql.Driver
```

**Maven Dependencies (pom.xml)**

```xml
<dependencies>
    <!-- SQL Server Driver -->
    <dependency>
        <groupId>com.microsoft.sqlserver</groupId>
        <artifactId>mssql-jdbc</artifactId>
        <version>12.4.2.jre11</version>
    </dependency>
    
    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.1</version>
    </dependency>
    
    <!-- MySQL Driver (if needed) -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.2.0</version>
    </dependency>
    
    <!-- Oracle Driver (if needed) -->
    <dependency>
        <groupId>com.oracle.database.jdbc</groupId>
        <artifactId>ojdbc11</artifactId>
        <version>23.3.0.23.09</version>
    </dependency>
</dependencies>
```

#### Code Adjustments for SQL Dialect Differences

**CoordinatorService.java - Database-Agnostic Queries**

```java
@Service
public class CoordinatorService {
    
    @Autowired
    @Qualifier("itmJdbcTemplate")
    private JdbcTemplate itmJdbc;  // SQL Server
    
    @Autowired
    @Qualifier("coJdbcTemplate")
    private JdbcTemplate coJdbc;   // PostgreSQL
    
    // SQL Server source query (uses TOP)
    private List<BillingRecord> fetchFromSqlServer(long startId, long endId, int batchSize) {
        String sql = "SELECT TOP ? * FROM billing_records " +
                     "WHERE id >= ? AND id <= ? ORDER BY id";
        return itmJdbc.query(sql, rowMapper, batchSize, startId, endId);
    }
    
    // PostgreSQL target upsert (uses ON CONFLICT)
    private void upsertToPostgres(List<BillingRecord> records) {
        String sql = "INSERT INTO billing_records (id, customer_id, amount, created_at) " +
                     "VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (id) DO UPDATE SET " +
                     "customer_id = EXCLUDED.customer_id, " +
                     "amount = EXCLUDED.amount, " +
                     "created_at = EXCLUDED.created_at";
        
        coJdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                BillingRecord record = records.get(i);
                ps.setLong(1, record.getId());
                ps.setString(2, record.getCustomerId());
                ps.setBigDecimal(3, record.getAmount());
                ps.setTimestamp(4, Timestamp.valueOf(record.getCreatedAt()));
            }
            
            @Override
            public int getBatchSize() {
                return records.size();
            }
        });
    }
}
```

**Database-Specific SQL Strategies**

| Database | **SELECT with Limit** | **UPSERT Syntax** |
|----------|----------------------|-------------------|
| **SQL Server** | `SELECT TOP 50000 * FROM table` | `MERGE INTO target USING source ON ... WHEN MATCHED THEN UPDATE WHEN NOT MATCHED THEN INSERT` |
| **PostgreSQL** | `SELECT * FROM table LIMIT 50000` | `INSERT ... ON CONFLICT (id) DO UPDATE SET ...` |
| **MySQL** | `SELECT * FROM table LIMIT 50000` | `INSERT ... ON DUPLICATE KEY UPDATE ...` |
| **Oracle** | `SELECT * FROM table WHERE ROWNUM <= 50000` | `MERGE INTO target USING source ON ... WHEN MATCHED THEN UPDATE WHEN NOT MATCHED THEN INSERT` |

#### Handling Data Type Differences

**Type Mapping Configuration**

```java
@Configuration
public class DatabaseTypeMapper {
    
    // SQL Server DATETIME → PostgreSQL TIMESTAMP
    public Timestamp convertDateTime(Object sqlServerDateTime) {
        if (sqlServerDateTime instanceof java.sql.Timestamp) {
            return (Timestamp) sqlServerDateTime;
        }
        // Handle SQL Server specific types
        return Timestamp.valueOf(sqlServerDateTime.toString());
    }
    
    // SQL Server MONEY → PostgreSQL NUMERIC
    public BigDecimal convertMoney(Object sqlServerMoney) {
        return new BigDecimal(sqlServerMoney.toString());
    }
    
    // SQL Server NVARCHAR → PostgreSQL TEXT
    public String convertNVarchar(Object sqlServerString) {
        return sqlServerString != null ? sqlServerString.toString() : null;
    }
}
```

**Common Type Mappings**

| SQL Server | PostgreSQL | MySQL | Oracle |
|------------|------------|-------|--------|
| `INT` | `INTEGER` | `INT` | `NUMBER(10)` |
| `BIGINT` | `BIGINT` | `BIGINT` | `NUMBER(19)` |
| `NVARCHAR(MAX)` | `TEXT` | `TEXT` | `CLOB` |
| `DATETIME` | `TIMESTAMP` | `DATETIME` | `TIMESTAMP` |
| `MONEY` | `NUMERIC(19,4)` | `DECIMAL(19,4)` | `NUMBER(19,4)` |
| `BIT` | `BOOLEAN` | `TINYINT(1)` | `NUMBER(1)` |

### Kafka Streaming Approach for Different Databases

#### Debezium CDC Connectors for Different Sources

**SQL Server Source**
```json
{
  "name": "sqlserver-source-connector",
  "config": {
    "connector.class": "io.debezium.connector.sqlserver.SqlServerConnector",
    "database.hostname": "sqlserver-host",
    "database.port": "1433",
    "database.user": "debezium",
    "database.password": "<PASSWORD>",
    "database.dbname": "ITM",
    "database.server.name": "sqlserver-source",
    "table.include.list": "dbo.billing_records"
  }
}
```

**PostgreSQL Target Consumer**
```java
@KafkaListener(topics = "sqlserver-source.dbo.billing_records")
public void consumeFromSqlServer(@Payload DebeziumMessage message) {
    // Extract change event
    ChangeEvent event = message.getPayload();
    
    // Convert SQL Server types to PostgreSQL types
    BillingRecord record = new BillingRecord();
    record.setId(event.getLong("id"));
    record.setCustomerId(event.getString("customer_id"));
    record.setAmount(event.getBigDecimal("amount"));
    
    // Handle SQL Server DATETIME → PostgreSQL TIMESTAMP
    record.setCreatedAt(convertDateTime(event.get("created_at")));
    
    // Upsert to PostgreSQL
    String sql = "INSERT INTO billing_records VALUES (?, ?, ?, ?) " +
                 "ON CONFLICT (id) DO UPDATE SET ...";
    postgresJdbc.update(sql, record.getId(), record.getCustomerId(), 
                        record.getAmount(), record.getCreatedAt());
}
```

#### Multi-Database Kafka Architecture

```
┌────────────────────────────────────────────────────────────┐
│           MULTI-DATABASE KAFKA STREAMING                   │
└────────────────────────────────────────────────────────────┘

  ┌─────────────┐         ┌─────────────┐         ┌─────────────┐
  │ SQL Server  │         │   Oracle    │         │    MySQL    │
  │  (Source 1) │         │  (Source 2) │         │  (Source 3) │
  └──────┬──────┘         └──────┬──────┘         └──────┬──────┘
         │ CDC                   │ CDC                   │ CDC
         │ (Debezium)            │ (Debezium)            │ (Debezium)
         ▼                       ▼                       ▼
  ┌──────────────────────────────────────────────────────────┐
  │              KAFKA CLUSTER (3 nodes)                     │
  │  Topics:                                                 │
  │  - sqlserver.billing_records                             │
  │  - oracle.customer_data                                  │
  │  - mysql.order_history                                   │
  └────────────────────┬─────────────────────────────────────┘
                       │ Poll
                       ▼
          ┌────────────────────────┐
          │  Consumer Application  │
          │  (Database Adapters)   │
          │  - SQL Server Adapter  │
          │  - Oracle Adapter      │
          │  - MySQL Adapter       │
          └────────────┬───────────┘
                       │ Write
                       ▼
              ┌─────────────────┐
              │   PostgreSQL    │
              │  (Target DB)    │
              │  - Unified      │
              │    Schema       │
              └─────────────────┘
```

### Best Practices for Cross-Database Transfers

#### 1. Schema Mapping Strategy
```java
@Component
public class SchemaMapper {
    
    private Map<String, String> typeMapping = Map.of(
        "sqlserver.datetime", "postgresql.timestamp",
        "oracle.number", "postgresql.numeric",
        "mysql.tinyint", "postgresql.boolean"
    );
    
    public Object convertValue(String sourceType, Object value, String targetType) {
        // Implement conversion logic based on source and target types
        if (sourceType.equals("sqlserver.datetime") && 
            targetType.equals("postgresql.timestamp")) {
            return convertSqlServerDateTimeToPostgres(value);
        }
        // ... more conversions
        return value;
    }
}
```

#### 2. Character Encoding
```properties
# Source (SQL Server) - typically UTF-16
itm.datasource.url=jdbc:sqlserver://...;characterEncoding=UTF-16

# Target (PostgreSQL) - UTF-8
co.datasource.url=jdbc:postgresql://...?characterEncoding=UTF-8
```

#### 3. Transaction Isolation
```java
// SQL Server uses READ_COMMITTED_SNAPSHOT by default
@Transactional(isolation = Isolation.READ_COMMITTED)
public void transferBatch() {
    // Read from SQL Server
    List<Record> records = itmJdbc.query("SELECT ...");
    
    // Write to PostgreSQL
    coJdbc.batchUpdate("INSERT ...", records);
}
```

#### 4. Performance Optimization
```java
// Use native bulk load APIs when available
public void bulkLoadToPostgres(List<BillingRecord> records) {
    // PostgreSQL COPY command (fastest)
    CopyManager copyManager = ((PGConnection) connection).getCopyAPI();
    
    String sql = "COPY billing_records FROM STDIN WITH CSV";
    copyManager.copyIn(sql, csvInputStream);
}

public void bulkLoadToOracle(List<BillingRecord> records) {
    // Oracle SQL*Loader or External Tables
    OraclePreparedStatement ops = (OraclePreparedStatement) ps;
    ops.setExecuteBatch(1000);
}
```

### Real-World Example: SQL Server → Snowflake (Analytics)

```java
@Service
public class SqlServerToSnowflakeTransfer {
    
    @Autowired
    @Qualifier("sqlServerJdbc")
    private JdbcTemplate sqlServerJdbc;
    
    @Autowired
    @Qualifier("snowflakeJdbc")
    private JdbcTemplate snowflakeJdbc;
    
    public void transferBatch(long startId, long endId) {
        // Read from SQL Server
        String sqlServerQuery = 
            "SELECT id, customer_id, amount, created_at " +
            "FROM billing_records WITH (NOLOCK) " +
            "WHERE id >= ? AND id <= ?";
        
        List<BillingRecord> records = sqlServerJdbc.query(
            sqlServerQuery, rowMapper, startId, endId);
        
        // Write to Snowflake using MERGE
        String snowflakeMerge = 
            "MERGE INTO billing_records target " +
            "USING (SELECT ? as id, ? as customer_id, ? as amount, ? as created_at) source " +
            "ON target.id = source.id " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  customer_id = source.customer_id, " +
            "  amount = source.amount, " +
            "  created_at = source.created_at " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (id, customer_id, amount, created_at) " +
            "  VALUES (source.id, source.customer_id, source.amount, source.created_at)";
        
        snowflakeJdbc.batchUpdate(snowflakeMerge, 
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    BillingRecord record = records.get(i);
                    ps.setLong(1, record.getId());
                    ps.setString(2, record.getCustomerId());
                    ps.setBigDecimal(3, record.getAmount());
                    ps.setTimestamp(4, Timestamp.valueOf(record.getCreatedAt()));
                }
                
                @Override
                public int getBatchSize() {
                    return records.size();
                }
            });
    }
}
```

### Cross-Database Performance Comparison

| Transfer Type | **Throughput** | **Complexity** | **Best For** |
|--------------|----------------|----------------|--------------|
| **Same DB Type** (SQL Server → SQL Server) | 2M records/min | Low | Internal data consolidation |
| **Same Vendor Family** (MySQL → MariaDB) | 1.8M records/min | Low | Easy migrations |
| **Different RDBMS** (SQL Server → PostgreSQL) | 1.5M records/min | Medium | Cloud migrations |
| **RDBMS → NoSQL** (PostgreSQL → MongoDB) | 1M records/min | High | Schema transformation needed |
| **RDBMS → Cloud DW** (Oracle → Snowflake) | 1.2M records/min | Medium | Analytics pipelines |

---

## Detailed Comparison

### 1. Latency Comparison

| Data Freshness Requirement | **Batch** | **Kafka** |
|----------------------------|-----------|-----------|
| Within 5 minutes | ❌ | ✅ |
| Within 1 hour | ❌ | ✅ |
| Within 24 hours | ✅ | ✅ |
| Once per day | ✅ | ✅ |

**Winner**: Kafka (if real-time needed), Batch (if daily is sufficient)

### 2. Complexity Comparison

| Complexity Factor | **Batch** | **Kafka** |
|-------------------|-----------|-----------|
| Infrastructure Components | 1 app + 2 databases | Kafka (3 nodes) + Zookeeper + CDC + Consumers + Monitoring |
| Lines of Code | ~500 | ~2,000 |
| Configuration Files | 1 (application.properties) | 10+ (Kafka configs, connector, consumers) |
| Deployment Steps | 3 (build, copy JAR, start) | 20+ (setup cluster, deploy CDC, deploy consumers) |
| Team Size Needed | 1-2 developers | 3-5 engineers (dev + ops) |

**Winner**: Batch (much simpler)

### 3. Cost Comparison (Monthly)

#### Batch Processing Costs
```
Infrastructure:
- VM (4 vCPU, 8 GB RAM):                    $50
- Database licenses (included):             $0
Total:                                      $50/month

One-time setup: $2,000 (40 hours @ $50/hr)
Annual cost: $600 + $2,000 = $2,600
```

#### Kafka Streaming Costs
```
Infrastructure:
- Kafka cluster (3 nodes, 8 GB each):       $300
- Zookeeper (3 nodes):                      $100
- Consumer VMs (10 instances, 2 GB each):   $200
- Monitoring (Prometheus + Grafana):        $50
- Schema Registry:                          $50
Total:                                      $700/month

One-time setup: $15,000 (300 hours @ $50/hr)
Annual cost: $8,400 + $15,000 = $23,400
```

**Winner**: Batch (10x cheaper)

### 4. Failure Recovery Comparison

| Failure Scenario | **Batch** | **Kafka** |
|------------------|-----------|-----------|
| Application crash | Manual restart, resume from last partition | Auto-rebalance, another consumer takes over |
| Database down (10 min) | Job fails, retry tomorrow (24h delay) | Messages buffered in Kafka, auto-resume |
| Database down (2 hours) | Job fails, manual investigation | Messages buffered, process backlog when up |
| Network partition | Job fails, retry tomorrow | Kafka leader election, transparent failover |
| Corrupt data | Process all data, fix errors later | Skip corrupt message, send to DLQ |
| Code bug in processor | All 1B records processed incorrectly | Stop consumers, fix code, replay from offset |

**Winner**: Kafka (automatic recovery, replay capability)

### 5. Scalability Comparison

| Growth Scenario | **Batch** | **Kafka** |
|-----------------|-----------|-----------|
| 1B → 2B records | Add more worker threads (limited by CPU) | Add more Kafka partitions and consumers |
| 2x peak load | Increase VM size (vertical scaling) | Add consumer instances (horizontal scaling) |
| 10x daily load | May exceed 24-hour window | Increase consumer count, same latency |
| New consumer system | Build separate batch job | Add new consumer group, same Kafka topic |

**Winner**: Kafka (horizontal scalability, unlimited growth)

### 6. Operational Metrics

| Metric | **Batch** | **Kafka** |
|--------|-----------|-----------|
| MTBF (Mean Time Between Failures) | 30 days | 90 days (Kafka built for HA) |
| MTTR (Mean Time To Recovery) | 15-30 minutes (manual) | 2-5 minutes (automatic) |
| Planned Downtime | 1 hour/month (patching) | Zero (rolling restarts) |
| On-call burden | Low (failures during business hours) | Medium (24/7 monitoring) |
| Incident response time | Can wait until morning | Need immediate response |

**Winner**: Batch (lower operational burden), Kafka (higher availability)

---

## Decision Framework

### Use This Decision Tree

```
START: Data Transfer Requirement

Question 1: How fresh must the data be?
├─ Answer: "Once per day is fine"
│  └─ Go to Question 2
└─ Answer: "Need within 1 hour" or "Need real-time"
   └─ **Choose KAFKA**

Question 2: Will you have multiple systems consuming this data?
├─ Answer: "Yes, 3+ systems need the same data"
│  └─ **Choose KAFKA**
└─ Answer: "No, just point-to-point transfer"
   └─ Go to Question 3

Question 3: Is your team experienced with distributed systems?
├─ Answer: "Yes, we manage Kafka/microservices"
│  └─ Go to Question 4
└─ Answer: "No, we're a small team with basic Java skills"
   └─ **Choose BATCH**

Question 4: What's your budget for infrastructure?
├─ Answer: "< $200/month"
│  └─ **Choose BATCH**
└─ Answer: "> $500/month is OK"
   └─ Go to Question 5

Question 5: Do you need to replay/reprocess data?
├─ Answer: "Yes, we may need to reprocess for corrections"
│  └─ **Choose KAFKA**
└─ Answer: "No, one-time processing is sufficient"
   └─ **Choose BATCH**
```

### Recommendation Matrix

| Your Situation | Recommendation | Reasoning |
|----------------|----------------|-----------|
| **Startup with 2-3 developers** | ✅ Batch | Low complexity, fast to market |
| **Enterprise with dedicated platform team** | ✅ Kafka | Leverage existing Kafka infrastructure |
| **Daily ETL for reporting** | ✅ Batch | Perfect fit for scheduled jobs |
| **Real-time dashboard** | ✅ Kafka | Only option for real-time data |
| **Budget < $5,000/year** | ✅ Batch | Kafka too expensive |
| **Need 99.9% uptime** | ✅ Kafka | Built-in fault tolerance |
| **Regulatory audit requirements** | ✅ Kafka | Complete audit trail |
| **Proof of concept** | ✅ Batch | Ship quickly, migrate to Kafka later |

---

## Implementation Guides

### Batch Processing Setup (30 minutes)

#### Step 1: Create Database Tables
```sql
-- Coordination Database
CREATE TABLE transfer_partitions (
    partition_id INT PRIMARY KEY,
    status VARCHAR(20) DEFAULT 'PENDING',
    worker_id INT NULL,
    start_id BIGINT NOT NULL,
    end_id BIGINT NOT NULL,
    records_processed BIGINT DEFAULT 0,
    started_at DATETIME NULL,
    completed_at DATETIME NULL
);

-- Insert 10 partitions (100M records each)
DECLARE @i INT = 0;
WHILE @i < 10
BEGIN
    INSERT INTO transfer_partitions (partition_id, start_id, end_id)
    VALUES (@i, @i * 100000000, (@i + 1) * 100000000 - 1);
    SET @i = @i + 1;
END

CREATE TABLE transfer_jobs (
    job_id INT IDENTITY(1,1) PRIMARY KEY,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    total_records BIGINT DEFAULT 0,
    status VARCHAR(20) DEFAULT 'RUNNING'
);
```

#### Step 2: Configure Application
```properties
# application.properties
spring.application.name=transfer-service

# ITM Source Database (Read-Only)
itm.datasource.url=jdbc:sqlserver://itm-db-server:1433;databaseName=ITM;encrypt=true
itm.datasource.username=itm_reader
itm.datasource.password=<PASSWORD>
itm.datasource.hikari.maximum-pool-size=20
itm.datasource.hikari.connection-timeout=30000

# CO Target Database (Write)
co.datasource.url=jdbc:sqlserver://co-db-server:1433;databaseName=CO;encrypt=true
co.datasource.username=co_writer
co.datasource.password=<PASSWORD>
co.datasource.hikari.maximum-pool-size=20

# Coordination Database
coord.datasource.url=jdbc:sqlserver://coord-db-server:1433;databaseName=Coordination
coord.datasource.username=coord_admin
coord.datasource.password=<PASSWORD>
coord.datasource.hikari.maximum-pool-size=10

# Scheduler
transfer.cron.schedule=0 0 2 * * *
transfer.worker.count=10
transfer.batch.size=50000
```

#### Step 3: Build and Deploy
```powershell
# Build JAR
mvn clean package -DskipTests -pl transfer-service -am

# Copy to server
scp transfer-service/target/transfer-service-0.0.1-SNAPSHOT.jar user@server:/opt/transfer/

# Run as service (Linux)
sudo systemctl start transfer-service

# Monitor
curl http://localhost:8080/api/monitor/progress
```

### Kafka Streaming Setup (1 week)

#### Step 1: Deploy Kafka Cluster
```bash
# docker-compose.yml for Kafka
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
    deploy:
      replicas: 3

  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_LOG_RETENTION_HOURS: 168  # 7 days

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    # ... similar config with BROKER_ID: 2

  kafka-3:
    image: confluentinc/cp-kafka:7.5.0
    # ... similar config with BROKER_ID: 3

  schema-registry:
    image: confluentinc/cp-schema-registry:7.5.0
    depends_on:
      - kafka-1
      - kafka-2
      - kafka-3
    environment:
      SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: kafka-1:9092,kafka-2:9092,kafka-3:9092

docker-compose up -d
```

#### Step 2: Create Kafka Topic
```bash
kafka-topics --create \
  --bootstrap-server kafka-1:9092 \
  --topic billing-events \
  --partitions 10 \
  --replication-factor 3 \
  --config retention.ms=604800000 \
  --config compression.type=lz4
```

#### Step 3: Deploy Debezium CDC Connector
```json
{
  "name": "itm-sqlserver-connector",
  "config": {
    "connector.class": "io.debezium.connector.sqlserver.SqlServerConnector",
    "database.hostname": "itm-db-server",
    "database.port": "1433",
    "database.user": "debezium_user",
    "database.password": "<PASSWORD>",
    "database.dbname": "ITM",
    "database.server.name": "itm-server",
    "table.include.list": "dbo.billing_records",
    "database.history.kafka.bootstrap.servers": "kafka-1:9092",
    "database.history.kafka.topic": "schema-changes.itm",
    "key.converter": "io.confluent.connect.avro.AvroConverter",
    "key.converter.schema.registry.url": "http://schema-registry:8081",
    "value.converter": "io.confluent.connect.avro.AvroConverter",
    "value.converter.schema.registry.url": "http://schema-registry:8081"
  }
}
```

Deploy:
```bash
curl -X POST http://kafka-connect:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium-connector-config.json
```

#### Step 4: Deploy Consumer Application
```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092,kafka-3:9092
    consumer:
      group-id: billing-consumer-group
      auto-offset-reset: earliest
      enable-auto-commit: false
      properties:
        isolation.level: read_committed
        max.poll.records: 1000
    listener:
      ack-mode: manual

# Database connection
datasource:
  url: jdbc:sqlserver://co-db-server:1433;databaseName=CO
  username: co_writer
  password: <PASSWORD>
```

Build and deploy:
```bash
mvn clean package
docker build -t billing-consumer:latest .
kubectl apply -f consumer-deployment.yaml  # Deploy 10 replicas
```

#### Step 5: Setup Monitoring
```yaml
# Prometheus scrape config
scrape_configs:
  - job_name: 'kafka-consumers'
    static_configs:
      - targets: ['consumer-1:8080', 'consumer-2:8080', ...]
    metrics_path: '/actuator/prometheus'

# Grafana dashboard: Import kafka-consumer-lag.json
# PagerDuty alerts: Configure for lag > 1M messages
```

---

## Cost Analysis

### Total Cost of Ownership (3 Years)

#### Batch Processing
```
Year 1:
- Setup: $2,000 (40 hours development)
- Infrastructure: $50/month × 12 = $600
- Operations: $1,000 (20 hours/year maintenance)
Total Year 1: $3,600

Year 2-3:
- Infrastructure: $600/year
- Operations: $1,000/year
Total Year 2: $1,600
Total Year 3: $1,600

3-Year TCO: $6,800
```

#### Kafka Streaming
```
Year 1:
- Setup: $15,000 (300 hours: Kafka cluster, CDC, consumers, monitoring)
- Infrastructure: $700/month × 12 = $8,400
- Operations: $5,000 (monitoring, on-call, incident response)
Total Year 1: $28,400

Year 2-3:
- Infrastructure: $8,400/year
- Operations: $5,000/year
Total Year 2: $13,400
Total Year 3: $13,400

3-Year TCO: $55,200
```

**Cost Difference**: Kafka is **8x more expensive** over 3 years

### ROI Analysis

**When does Kafka pay for itself?**

If real-time data enables:
- Faster billing reconciliation saving **$5,000/month** in manual work
- Reduced billing errors saving **$10,000/month** in disputes
- Business intelligence driving **$20,000/month** in additional revenue

**Total benefit**: $35,000/month = $420,000/year

**Kafka ROI**: $420,000 - $28,400 = **$391,600 profit in Year 1**

**Conclusion**: Kafka expensive but justified if **business value > $30,000/month**

---

## Migration Path

### Scenario: Start with Batch, Migrate to Kafka Later

#### Phase 1: Batch (Months 0-6)
✅ Quick to production (2 weeks)  
✅ Validate data quality  
✅ Build team confidence  
✅ Low risk, low cost  

#### Phase 2: Kafka Pilot (Months 7-9)
- Set up Kafka cluster for non-critical data
- Deploy 1 consumer for single table
- Learn Kafka operations
- Measure latency improvement

#### Phase 3: Hybrid (Months 10-12)
- Kafka for new/updated records (real-time)
- Batch for historical backfill
- Both systems running in parallel
- Compare data consistency

#### Phase 4: Full Kafka (Month 13+)
- Migrate all tables to Kafka
- Deprecate batch job
- Decommission old infrastructure

### Migration Checklist

**Technical Preparation**
- [ ] Kafka cluster setup (3 nodes minimum)
- [ ] Zookeeper ensemble (3 nodes)
- [ ] Schema Registry deployment
- [ ] Debezium CDC connector configuration
- [ ] Consumer application development
- [ ] Monitoring stack (Prometheus + Grafana)

**Team Readiness**
- [ ] Kafka training for team (2-day workshop)
- [ ] Runbook for common incidents
- [ ] On-call rotation established
- [ ] Escalation paths defined

**Risk Mitigation**
- [ ] Pilot with non-critical data first
- [ ] Data validation: Compare Kafka vs Batch outputs
- [ ] Rollback plan if issues arise
- [ ] Performance testing (load test with 10x data)

**Go-Live Criteria**
- [ ] Consumer lag < 100,000 messages for 1 week
- [ ] Zero data loss in pilot phase
- [ ] Incident response time < 15 minutes
- [ ] Team confident in operations

---

## Conclusion

### Final Recommendations

#### Choose Batch Processing if:
1. **Latency requirement**: Once per day is acceptable
2. **Team size**: 1-3 developers, basic Java skills
3. **Budget**: < $200/month for infrastructure
4. **Timeline**: Need production system in 1-2 weeks
5. **Simplicity**: Want minimal moving parts

**Best use cases**:
- Nightly ETL for data warehouse
- Daily reports generation
- Monthly billing cycle processing
- Proof of concept / MVP

#### Choose Kafka Streaming if:
1. **Latency requirement**: Real-time (< 5 minutes)
2. **Multiple consumers**: 3+ systems need same data
3. **Event sourcing**: Need complete audit trail
4. **High availability**: 99.9% uptime requirement
5. **Scalability**: Expect 10x growth in 2 years

**Best use cases**:
- Real-time dashboards and analytics
- Fraud detection systems
- Live billing updates
- Event-driven microservices

### Hybrid Approach (Best of Both Worlds)

**Recommendation for large organizations**:
```
Use BOTH systems for different purposes:

1. Kafka for LIVE data:
   - New records streamed in real-time
   - Enables live dashboards
   - Feeds event-driven systems

2. Batch for HISTORICAL data:
   - Initial bulk load of 1B records
   - Backfill operations
   - Data corrections/reprocessing
   - Cost-effective for large volumes

Benefits:
✅ Real-time capability where needed
✅ Cost-effective bulk operations
✅ Flexibility for different use cases
```

---

## Quick Reference

### Commands Cheat Sheet

**Batch Processing**
```bash
# Build
mvn clean package -DskipTests -pl transfer-service -am

# Run locally
java -jar target/transfer-service-0.0.1-SNAPSHOT.jar

# Trigger manually
curl -X POST http://localhost:8080/api/transfer/start

# Check progress
curl http://localhost:8080/api/monitor/progress
```

**Kafka Streaming**
```bash
# Create topic
kafka-topics --create --topic billing-events --partitions 10 --replication-factor 3

# Check consumer lag
kafka-consumer-groups --bootstrap-server kafka-1:9092 --group billing-consumer-group --describe

# Reset offsets (reprocess data)
kafka-consumer-groups --bootstrap-server kafka-1:9092 --group billing-consumer-group --reset-offsets --to-earliest --execute --topic billing-events

# View messages
kafka-console-consumer --bootstrap-server kafka-1:9092 --topic billing-events --from-beginning --max-messages 10
```

### Troubleshooting Guide

| Problem | **Batch Solution** | **Kafka Solution** |
|---------|-------------------|-------------------|
| High latency | Increase worker threads, batch size | Add more consumers, check lag |
| Out of memory | Reduce batch size, increase heap | Reduce max.poll.records |
| Database deadlock | Add NOLOCK hint, reduce concurrency | Enable idempotent consumer |
| Data inconsistency | Rerun entire job | Reset offset, reprocess partition |

---

**Document Version**: 1.0  
**Last Updated**: January 15, 2026  
**Author**: Technical Architecture Team  
**For questions**: Contact platform-team@company.com
