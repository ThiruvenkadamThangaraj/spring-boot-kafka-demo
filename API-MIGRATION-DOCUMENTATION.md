# Project API & Migration Documentation

## Overview
This project is a demo microservices architecture using Spring Boot, Docker, Kafka, JWT authentication, and MongoDB (migrated from H2). It demonstrates:
- Centralized audit logging
- JWT-secured REST APIs
- Kafka event streaming
- MongoDB for persistent storage (all services)

## Key Architectural Changes
- **Database Migration:** All services migrated from H2 (file-based) to MongoDB (Docker container, free Community Edition).
- **ID Type Change:** All entity IDs (e.g., User, Evaluation) changed from `Long` to `String` for MongoDB compatibility.
- **Spring Data:** Switched from Spring Data JPA to Spring Data MongoDB in all services.
- **Repository Update:** All repositories now extend `MongoRepository<Entity, String>`.
- **Entity Update:** All entities use `@Document` and MongoDB annotations instead of JPA.
- **Docker Compose:** Added MongoDB service to `docker-compose.yml`.
- **Configuration:** All `application.yaml`/`application.properties` files updated to use MongoDB URI.
- **No Data Migration:** Existing H2 data is not preserved (demo only).

## API Documentation

### Authentication
- **POST /api/auth/login**
  - Request: `{ "username": "admin", "password": "admin123" }`
  - Response: `{ "token": "...", "username": "admin", ... }`
  - Use the token in the `Authorization: Bearer <token>` header for all protected endpoints.

### User Management (evaluation-service)
- **GET /api/users** — List all users
- **GET /api/users/{id}** — Get user by ID (String)
- **GET /api/users/username/{username}** — Get user by username
- **POST /api/users** — Create user
- **PUT /api/users/{id}** — Update user
- **DELETE /api/users/{id}** — Delete user

### Evaluation Management (evaluation-service)
- **GET /api/evaluations** — List all evaluations
- **GET /api/evaluations/{id}** — Get evaluation by ID (String)
- **POST /api/evaluations** — Create evaluation

### Audit Logging (audit-service)
- **GET /api/audit** — List audit logs (paginated)
- **GET /api/audit/service/{serviceName}** — Logs by service
- **GET /api/audit/user/{username}** — Logs by user
- **GET /api/audit/trace/{correlationId}** — Distributed trace logs
- **GET /api/audit/search** — Filtered search
- **GET /api/audit/failures** — Failed requests
- **GET /api/audit/slow** — Slow requests
- **GET /api/audit/statistics** — Service statistics
- **GET /api/audit/health** — Health check

### Other Services
- Each service (sampling, evidence, remediation, jira) exposes similar REST APIs for their domain objects, all using MongoDB for persistence.

## How to Run
1. Ensure Docker is running.
2. Start all services: `docker compose up -d`
3. Access APIs via Swagger UI (if enabled) or directly via REST tools (Postman, curl).
4. MongoDB is available at `localhost:27017` (default, no auth for demo).

## Security
- All APIs (except `/api/auth/login`, `/h2-console`, `/swagger-ui/**`) require JWT authentication.
- Use the `/api/auth/login` endpoint to obtain a token.

## Notes
- This is a demo project. Data is not preserved between DB migrations.
- All IDs are now String (MongoDB ObjectId or custom string).
- For further details, see the code and comments in each service.

---

_Last updated: 2026-01-19_
