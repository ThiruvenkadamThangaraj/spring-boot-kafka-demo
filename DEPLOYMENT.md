# README: Local Deployment Guide

## Prerequisites

1. **Install Docker Desktop for Windows**
   - Download from: https://www.docker.com/products/docker-desktop
   - Ensure Docker is running (check system tray)

2. **Install Java 17** (already have it)
   - Verify: `java -version`

3. **Install Maven** (already have it)
   - Verify: `mvn -version`

## Quick Start (Easiest Way)

### Option 1: Using PowerShell Script
```powershell
# Run this single command to build and start everything
.\start-all.ps1
```

### Option 2: Manual Steps
```powershell
# 1. Build all services
mvn clean install -DskipTests

# 2. Start with Docker Compose
docker-compose up -d

# 3. View logs
docker-compose logs -f
```

## Access Your Services

Once started, access these URLs in your browser:

- **Evaluation Service**: http://localhost:8081/swagger-ui.html
- **Sampling Service**: http://localhost:8082/swagger-ui.html
- **Evidence Service**: http://localhost:8083/swagger-ui.html
- **Remediation Service**: http://localhost:8084/swagger-ui.html
- **Jira Service**: http://localhost:8085/swagger-ui.html

Health Check Endpoints:
- http://localhost:8081/api/evaluations/health
- http://localhost:8082/api/sampling/health
- http://localhost:8083/api/evidence/health
- http://localhost:8084/api/remediation/health
- http://localhost:8085/api/jira/health

## Common Commands

```powershell
# Check service status
docker-compose ps

# View logs of all services
docker-compose logs -f

# View logs of specific service
docker-compose logs -f evaluation-service

# Stop all services
.\stop-all.ps1
# OR
docker-compose down

# Restart a specific service
docker-compose restart evaluation-service

# Rebuild and restart after code changes
mvn clean install -DskipTests
docker-compose up -d --build
```

## Running Without Docker (Alternative)

If you prefer running services directly without Docker:

```powershell
# Terminal 1 - Evaluation Service
cd evaluation-service
mvn spring-boot:run

# Terminal 2 - Sampling Service
cd sampling-service
mvn spring-boot:run

# Terminal 3 - Evidence Service
cd evidence-service
mvn spring-boot:run

# Terminal 4 - Remediation Service
cd remediation-service
mvn spring-boot:run

# Terminal 5 - Jira Service
cd jira-service
mvn spring-boot:run
```

## Troubleshooting

### Docker not running
```powershell
# Check Docker status
docker ps
# If error, start Docker Desktop from Start menu
```

### Port already in use
```powershell
# Find process using port (example: 8081)
netstat -ano | findstr :8081

# Kill the process (replace PID)
taskkill /PID <PID> /F
```

### Services not starting
```powershell
# Check logs for errors
docker-compose logs

# Rebuild from scratch
docker-compose down
mvn clean install -DskipTests
docker-compose up -d --build
```

### Out of memory
Edit docker-compose.yml and add memory limits:
```yaml
services:
  evaluation-service:
    mem_limit: 512m
```

## Next Steps

1. Add your business logic to each service
2. Configure database connections (currently using H2)
3. Set up inter-service communication
4. Add authentication/authorization
5. Configure production settings
