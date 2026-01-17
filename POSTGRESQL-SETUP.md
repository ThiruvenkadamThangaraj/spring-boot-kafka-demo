# ========================================
# PostgreSQL Configuration Guide
# Complete setup for Spring Boot + Terraform + Docker
# ========================================

## 📋 Table of Contents

1. [Local Development Setup](#local-development-setup)
2. [Spring Boot Configuration](#spring-boot-configuration)
3. [Docker Compose Setup](#docker-compose-setup)
4. [AWS RDS PostgreSQL (Terraform)](#aws-rds-postgresql-terraform)
5. [Connection Examples](#connection-examples)
6. [Common Issues & Solutions](#common-issues--solutions)

---

## 🔧 Local Development Setup

### **Step 1: Install PostgreSQL**

**Windows (Using Chocolatey):**
```powershell
# Install PostgreSQL
choco install postgresql

# Or download installer from:
# https://www.postgresql.org/download/windows/

# Start PostgreSQL service
Start-Service postgresql-x64-15

# Verify installation
psql --version
# Output: psql (PostgreSQL) 15.4
```

**Using Docker (Recommended):**
```powershell
# Run PostgreSQL in Docker
docker run --name postgres-dev `
  -e POSTGRES_USER=dbadmin `
  -e POSTGRES_PASSWORD=dbpassword `
  -e POSTGRES_DB=microservices_db `
  -p 5432:5432 `
  -v postgres-data:/var/lib/postgresql/data `
  -d postgres:15-alpine

# Verify it's running
docker ps

# Connect to database
docker exec -it postgres-dev psql -U dbadmin -d microservices_db
```

### **Step 2: Create Databases for Each Service**

```sql
-- Connect to PostgreSQL
psql -U postgres

-- Create main database
CREATE DATABASE microservices_db;

-- Create user with password
CREATE USER dbadmin WITH ENCRYPTED PASSWORD 'dbpassword';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE microservices_db TO dbadmin;

-- Create separate schemas for each service (optional)
\c microservices_db
CREATE SCHEMA evaluation;
CREATE SCHEMA sampling;
CREATE SCHEMA evidence;
CREATE SCHEMA remediation;
CREATE SCHEMA jira_service;

GRANT ALL ON SCHEMA evaluation TO dbadmin;
GRANT ALL ON SCHEMA sampling TO dbadmin;
GRANT ALL ON SCHEMA evidence TO dbadmin;
GRANT ALL ON SCHEMA remediation TO dbadmin;
GRANT ALL ON SCHEMA jira_service TO dbadmin;
```

---

## 🍃 Spring Boot Configuration

### **Step 1: Update Maven Dependencies**

Add to `common/pom.xml`:

```xml
<dependencies>
    <!-- Remove H2 if present -->
    <!-- <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency> -->
    
    <!-- Add PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Spring Data JPA (should already exist) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
</dependencies>
```

### **Step 2: Application Properties - Development**

**Create/Update:** `evaluation-service/src/main/resources/application-dev.yml`

```yaml
spring:
  application:
    name: evaluation-service
  
  # PostgreSQL Configuration
  datasource:
    url: jdbc:postgresql://localhost:5432/microservices_db
    username: dbadmin
    password: dbpassword
    driver-class-name: org.postgresql.Driver
    
    # Connection Pool Settings (HikariCP)
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      pool-name: EvaluationServicePool
  
  # JPA/Hibernate Configuration
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update  # Options: none, validate, update, create, create-drop
    properties:
      hibernate:
        format_sql: true
        show_sql: false
        use_sql_comments: true
        jdbc:
          batch_size: 20
        order_inserts: true
        order_updates: true
    show-sql: false  # Set to true for debugging
  
  # Schema configuration (if using separate schemas)
  jpa:
    properties:
      hibernate:
        default_schema: evaluation

server:
  port: 8081

# Logging
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    org.springframework.jdbc.core: DEBUG
```

### **Step 3: Application Properties - Production**

**Create/Update:** `evaluation-service/src/main/resources/application-prod.yml`

```yaml
spring:
  application:
    name: evaluation-service
  
  # Production PostgreSQL (from AWS RDS)
  datasource:
    url: ${DB_URL:jdbc:postgresql://your-rds-endpoint.amazonaws.com:5432/microservices_db}
    username: ${DB_USERNAME:dbadmin}
    password: ${DB_PASSWORD}  # From environment variable or AWS Secrets Manager
    driver-class-name: org.postgresql.Driver
    
    hikari:
      minimum-idle: 10
      maximum-pool-size: 50
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-timeout: 30000
      leak-detection-threshold: 60000
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: validate  # NEVER use 'update' in production!
    properties:
      hibernate:
        format_sql: false
        show_sql: false
    show-sql: false

server:
  port: 8081

logging:
  level:
    root: INFO
    org.hibernate.SQL: WARN
```

### **Step 4: Common Application Properties**

**Update:** `common/src/main/resources/application.yml`

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  
  # Default PostgreSQL settings (inherited by all services)
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/microservices_db}
    username: ${DB_USERNAME:dbadmin}
    password: ${DB_PASSWORD:dbpassword}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQLDialect
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        jdbc:
          lob.non_contextual_creation: true  # Fix for PostgreSQL LOB issue

# Common logging
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
  level:
    com.example: DEBUG
```

---

## 🐳 Docker Compose Setup

**Create:** `docker-compose-postgres.yml`

```yaml
version: '3.8'

services:
  # PostgreSQL Database
  postgres:
    image: postgres:15-alpine
    container_name: postgres-microservices
    environment:
      POSTGRES_USER: dbadmin
      POSTGRES_PASSWORD: dbpassword
      POSTGRES_DB: microservices_db
      POSTGRES_INITDB_ARGS: "-E UTF8"
      PGDATA: /var/lib/postgresql/data/pgdata
    ports:
      - "5432:5432"
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    networks:
      - microservices-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U dbadmin -d microservices_db"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  # pgAdmin (Database Management UI)
  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: pgadmin-microservices
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@example.com
      PGADMIN_DEFAULT_PASSWORD: admin
      PGADMIN_LISTEN_PORT: 80
    ports:
      - "5050:80"
    volumes:
      - pgadmin-data:/var/lib/pgadmin
    networks:
      - microservices-network
    depends_on:
      - postgres
  
  # Evaluation Service
  evaluation-service:
    build:
      context: ./evaluation-service
      dockerfile: Dockerfile
    container_name: evaluation-service
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://postgres:5432/microservices_db
      DB_USERNAME: dbadmin
      DB_PASSWORD: dbpassword
    ports:
      - "8081:8081"
    networks:
      - microservices-network
    depends_on:
      postgres:
        condition: service_healthy
  
  # Sampling Service
  sampling-service:
    build:
      context: ./sampling-service
      dockerfile: Dockerfile
    container_name: sampling-service
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://postgres:5432/microservices_db
      DB_USERNAME: dbadmin
      DB_PASSWORD: dbpassword
    ports:
      - "8082:8082"
    networks:
      - microservices-network
    depends_on:
      postgres:
        condition: service_healthy
  
  # Evidence Service
  evidence-service:
    build:
      context: ./evidence-service
      dockerfile: Dockerfile
    container_name: evidence-service
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_URL: jdbc:postgresql://postgres:5432/microservices_db
      DB_USERNAME: dbadmin
      DB_PASSWORD: dbpassword
    ports:
      - "8083:8083"
    networks:
      - microservices-network
    depends_on:
      postgres:
        condition: service_healthy

volumes:
  postgres-data:
    driver: local
  pgadmin-data:
    driver: local

networks:
  microservices-network:
    driver: bridge
```

### **Database Initialization Script**

**Create:** `init-db.sql`

```sql
-- ========================================
-- Database Initialization Script
-- Creates schemas and tables for all services
-- ========================================

-- Create schemas
CREATE SCHEMA IF NOT EXISTS evaluation;
CREATE SCHEMA IF NOT EXISTS sampling;
CREATE SCHEMA IF NOT EXISTS evidence;
CREATE SCHEMA IF NOT EXISTS remediation;
CREATE SCHEMA IF NOT EXISTS jira_service;

-- Grant privileges
GRANT ALL ON SCHEMA evaluation TO dbadmin;
GRANT ALL ON SCHEMA sampling TO dbadmin;
GRANT ALL ON SCHEMA evidence TO dbadmin;
GRANT ALL ON SCHEMA remediation TO dbadmin;
GRANT ALL ON SCHEMA jira_service TO dbadmin;

-- Example: Create users table in evaluation schema
CREATE TABLE IF NOT EXISTS evaluation.users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for better performance
CREATE INDEX IF NOT EXISTS idx_users_username ON evaluation.users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON evaluation.users(email);

-- Insert sample data
INSERT INTO evaluation.users (username, email, first_name, last_name) 
VALUES 
    ('john.doe', 'john.doe@example.com', 'John', 'Doe'),
    ('jane.smith', 'jane.smith@example.com', 'Jane', 'Smith')
ON CONFLICT (username) DO NOTHING;

-- Print confirmation
SELECT 'Database initialized successfully!' AS message;
```

---

## ☁️ AWS RDS PostgreSQL (Terraform)

Your `terraform/rds.tf` already has PostgreSQL configured! Here's what's included:

### **Key Configuration Explained:**

```hcl
resource "aws_db_instance" "main" {
  identifier     = "microservices-db-dev"
  engine         = "postgres"
  engine_version = "15.4"  # Latest stable version
  
  # Instance size
  instance_class = "db.t3.micro"  # Free tier: db.t3.micro
  allocated_storage = 20  # GB
  storage_type = "gp3"  # General Purpose SSD v3 (faster, cheaper)
  
  # Database credentials
  db_name  = "microservices_db"
  username = "dbadmin"
  password = var.db_password  # From environment variable
  
  # High Availability
  multi_az = false  # Set to true for production
  
  # Backups
  backup_retention_period = 7  # Keep backups for 7 days
  backup_window = "03:00-04:00"  # UTC time
  
  # Maintenance
  maintenance_window = "mon:04:00-mon:05:00"
  
  # Security
  storage_encrypted = true
  publicly_accessible = false  # Only accessible from VPC
  
  # Logging
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]
}
```

### **Connection String for Services:**

```yaml
# In ECS task definition (automatically injected)
environment:
  - name: DB_URL
    value: jdbc:postgresql://${aws_db_instance.main.endpoint}/microservices_db
  - name: DB_USERNAME
    value: dbadmin
  - name: DB_PASSWORD
    valueFrom: ${aws_secretsmanager_secret.db_password.arn}
```

---

## 🔌 Connection Examples

### **1. JDBC URL Formats**

```properties
# Local development
jdbc:postgresql://localhost:5432/microservices_db

# Docker Compose (from service container)
jdbc:postgresql://postgres:5432/microservices_db

# AWS RDS
jdbc:postgresql://microservices-db-dev.c9akg8xample.us-east-1.rds.amazonaws.com:5432/microservices_db

# With SSL (production)
jdbc:postgresql://your-rds.amazonaws.com:5432/microservices_db?ssl=true&sslmode=require

# With specific schema
jdbc:postgresql://localhost:5432/microservices_db?currentSchema=evaluation
```

### **2. Environment Variables**

```powershell
# Windows PowerShell
$env:DB_URL="jdbc:postgresql://localhost:5432/microservices_db"
$env:DB_USERNAME="dbadmin"
$env:DB_PASSWORD="dbpassword"
$env:SPRING_PROFILES_ACTIVE="dev"

# Then run service
cd evaluation-service
mvn spring-boot:run
```

### **3. Test Connection from Java**

```java
import java.sql.Connection;
import java.sql.DriverManager;

public class PostgreSQLConnectionTest {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/microservices_db";
        String username = "dbadmin";
        String password = "dbpassword";
        
        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            System.out.println("✓ Connected to PostgreSQL successfully!");
            System.out.println("Database: " + conn.getCatalog());
            System.out.println("User: " + conn.getMetaData().getUserName());
        } catch (Exception e) {
            System.err.println("✗ Connection failed: " + e.getMessage());
        }
    }
}
```

---

## 🐛 Common Issues & Solutions

### **Issue 1: "Connection refused" Error**

```
org.postgresql.util.PSQLException: Connection refused
```

**Solutions:**
```powershell
# Check if PostgreSQL is running
docker ps | Select-String postgres
# Or for local install:
Get-Service postgresql*

# Check port 5432 is open
netstat -an | Select-String "5432"

# Verify firewall
Test-NetConnection -ComputerName localhost -Port 5432
```

### **Issue 2: "password authentication failed"**

```
FATAL: password authentication failed for user "dbadmin"
```

**Solutions:**
```sql
-- Reset password
ALTER USER dbadmin WITH PASSWORD 'newpassword';

-- Check pg_hba.conf authentication method
-- Should have: host all all 0.0.0.0/0 md5
```

### **Issue 3: "database does not exist"**

```
org.postgresql.util.PSQLException: FATAL: database "microservices_db" does not exist
```

**Solutions:**
```sql
-- Create database
CREATE DATABASE microservices_db;

-- Or recreate from scratch
DROP DATABASE IF EXISTS microservices_db;
CREATE DATABASE microservices_db;
GRANT ALL PRIVILEGES ON DATABASE microservices_db TO dbadmin;
```

### **Issue 4: "Relation does not exist" (Table not found)**

```
ERROR: relation "users" does not exist
```

**Solutions:**
```yaml
# Option 1: Let Hibernate create tables
spring:
  jpa:
    hibernate:
      ddl-auto: create  # Or 'update'

# Option 2: Use Flyway/Liquibase for migrations
# Option 3: Manually create tables with init-db.sql
```

### **Issue 5: Connection Pool Exhausted**

```
HikariPool: Connection is not available
```

**Solutions:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # Increase pool size
      connection-timeout: 30000  # Increase timeout
      leak-detection-threshold: 60000  # Detect leaks
```

---

## 🚀 Quick Start Commands

```powershell
# 1. Start PostgreSQL with Docker Compose
docker-compose -f docker-compose-postgres.yml up -d postgres

# 2. Wait for database to be ready (check logs)
docker logs postgres-microservices -f

# 3. Run database initialization
docker exec -i postgres-microservices psql -U dbadmin -d microservices_db < init-db.sql

# 4. Start pgAdmin (optional - UI for database)
docker-compose -f docker-compose-postgres.yml up -d pgadmin
# Access at: http://localhost:5050 (admin@example.com / admin)

# 5. Build and run services
cd evaluation-service
mvn clean install -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 6. Test connection
curl http://localhost:8081/actuator/health
curl http://localhost:8081/api/async/users
```

---

## 📊 PostgreSQL Performance Tips

### **1. Connection Pooling**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Rule of thumb: (cores * 2) + effective_spindle_count
      minimum-idle: 5
      connection-timeout: 20000
      idle-timeout: 300000
```

### **2. Query Optimization**
```java
// Use projections instead of fetching entire entities
@Query("SELECT new com.example.UserDTO(u.id, u.username) FROM User u")
List<UserDTO> findAllUsernames();

// Use pagination for large datasets
Page<User> findAll(Pageable pageable);

// Enable query caching
@QueryHints(@QueryHint(name = "org.hibernate.cacheable", value = "true"))
```

### **3. Index Your Tables**
```sql
-- Create indexes on frequently queried columns
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_orders_created_at ON orders(created_at);

-- Composite index for multiple columns
CREATE INDEX idx_users_name_email ON users(last_name, first_name, email);
```

---

## 🔐 Security Best Practices

1. **Never commit passwords** to version control
2. **Use environment variables** or AWS Secrets Manager
3. **Enable SSL** for production connections
4. **Limit database user permissions** (principle of least privilege)
5. **Regularly update PostgreSQL** to latest patch version
6. **Enable audit logging** for compliance

---

## 📚 Additional Resources

- [PostgreSQL Official Docs](https://www.postgresql.org/docs/)
- [Spring Boot Data JPA Guide](https://spring.io/guides/gs/accessing-data-jpa/)
- [HikariCP Configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby)
- [PostgreSQL Performance Tuning](https://wiki.postgresql.org/wiki/Performance_Optimization)

---

**Your PostgreSQL setup is now production-ready! 🎉**
