# NexusFiber — Reactive Service Request Management System

**[LIVE DEMO: https://test.anchordown.app](https://test.anchordown.app)**  
*(Fully deployed and operational. Login credentials below)*

> **Enterprise-grade, fully reactive real-time diagnostic and service request management system designed for Internet Service Providers (ISPs).**  
> Built strictly with non-blocking I/O using Spring WebFlux, R2DBC, and Redis Pub/Sub to handle high-throughput asynchronous task execution, WebSocket streaming, and role-based access control.

---

## Architecture Overview

The system abandons traditional thread-per-request blocking architectures (like Spring WebMVC + Hibernate) in favor of an entirely reactive, event-loop driven architecture utilizing Project Reactor and Netty. 

```text
[Next.js Client] <==== (WebSockets /ws/requests) =====> [Netty Web Server]
       |                                                         |
  (HTTP REST)                                               (R2DBC / SQL)
       v                                                         v
[Spring WebFlux] ======= (Reactive Flux Streams) =====> [PostgreSQL DB]
       |
  (Redis Pub/Sub)
       v
  [Redis Broker]
```

1. **Gateway & API Layer (Spring WebFlux + Netty)**:
   - Unified non-blocking HTTP REST and WebSocket termination.
   - Built on Project Reactor (`Mono` and `Flux`) to handle thousands of concurrent connections on a single thread.
   - JWT stateless authentication enforced via `SecurityWebFilterChain`.
2. **Event Broker & Pub/Sub (Redis)**:
   - Utilizes Redis Pub/Sub (`ReactiveRedisTemplate`) to broadcast progress updates from background tasks to all horizontally scaled backend instances, which then push to connected WebSocket clients.
3. **Asynchronous Task Execution (Project Reactor)**:
   - Replaces traditional external worker pools (like Celery or RabbitMQ) by utilizing `Flux.interval` streams. Background tasks execute entirely within the reactive context, yielding the thread when waiting for simulated network I/O.
   - Tasks can be instantly cancelled by safely disposing of the reactive stream subscription (`Disposable.dispose()`).
4. **Persistence Layer (PostgreSQL + R2DBC)**:
   - Fully reactive database drivers (`spring-boot-starter-data-r2dbc`) ensure that database reads/writes never block the Netty event loop.

---

## Technology Stack

| Layer | Technologies |
|---|---|
| **Frontend** | React 19, Next.js 15 (App Router), Tailwind CSS v3, Zustand, Lucide Icons |
| **Backend** | Java 21, Spring Boot 4, Spring WebFlux, Project Reactor |
| **Authentication** | Spring Security Reactive, Stateless JWT (Bearer tokens) |
| **Concurrency & WebSockets** | Spring WebFlux WebSocket API, Reactive Redis Pub/Sub |
| **Database** | PostgreSQL 15, Spring Data R2DBC |
| **Monitoring & Logging** | Dozzle (Real-time Container Log Tailer) |
| **DevOps & CI/CD** | Docker Compose, GitHub Actions (CI + Automated CD), `.env` Secrets |
| **Testing** | JUnit 5, Mockito, StepVerifier, Spring Boot Test |

---

## Assumptions & Design Decisions
1. **Fully Reactive Stack**: Every layer, from the HTTP controller to the database driver, is non-blocking. This allows the backend to handle massive scale with minimal memory overhead.
2. **In-Memory Reactive Tasks**: Instead of deploying a complex external message broker for background jobs, diagnostic routines simulate real-world hardware latencies using `Flux.interval()`. This simplifies deployment while maintaining strict non-blocking guarantees.
3. **Stateless JWT Security**: REST endpoints use `Authorization: Bearer <token>` headers. WebSockets authenticate by parsing the JWT directly during the initial connection handshake.
4. **Role Boundaries**: Operators only view, submit, and cancel their own requests. Supervisors possess administrative visibility and global cancellation rights.

---

## Quick Start (Docker Compose)

### Prerequisites
- Docker and Docker Compose installed.

### 1. Launch All Services
From the project root directory, run:
```bash
docker compose up --build -d
```
This automatically initializes:
- PostgreSQL (`localhost:5432`)
- Redis Broker (`localhost:6379`)
- Spring Boot Backend (`http://localhost:8080`)
- Next.js Frontend Dashboard (`http://localhost:3000`)
- Dozzle Log Aggregator (`http://localhost:8888`)

### 2. Access the Application
Open your browser at **`http://localhost:3000`**.

### 3. Demo Credentials

| Role | Username | Password | Capabilities |
|---|---|---|---|
| **Operator** | `operator1` | `password123` | Submit diagnostics, monitor personal tasks, cancel active jobs |
| **Supervisor** | `supervisor1` | `password123` | Fleet-wide visibility, live real-time monitoring, administrative cancellation |

---

## API Reference

All requests must supply `Authorization: Bearer <access_token>` in the header (except `/api/token/`).

### Authentication
#### `POST /api/token/`
Exchange credentials for JWT access token.
```json
{
  "username": "operator1",
  "password": "password123"
}
```

#### `POST /api/register/`
Create a new user account with role assignment (`OPERATOR` or `SUPERVISOR`).

### Service Requests
#### `GET /api/requests`
Returns paginated service requests. Automatically filtered by role (Supervisors see all; Operators see only their own).

#### `POST /api/requests`
Dispatches a new diagnostic background task.
```json
{
  "customerAccount": "ACC-9402",
  "requestType": "LINE_DIAGNOSTIC"
}
```
*Response:* `201 Created` with initial status `PENDING`.

#### `POST /api/requests/{id}/cancel`
Revokes the running background reactive stream and transitions database status to `CANCELLED`.
*Response:* `200 OK`

#### `DELETE /api/requests/{id}`
Guarded deletion of finished requests. Active tasks return `400 Bad Request`.
*Response:* `204 No Content`

#### `POST /api/requests/bulk-delete`
Bulk deletes finished requests by array of IDs. Automatically skips active tasks and broadcasts real-time deletion events.
```json
{
  "ids": ["uuid1", "uuid2"]
}
```

---

## Real-Time WebSocket Events

- **Endpoint:** `ws://localhost:8080/ws/requests`
- **Authentication:** Token query parameter verified during the WebSocket handshake (`?token=<jwt>`).
- **Event Distribution:** Pushed directly from the reactive event loop via Redis pub/sub. Zero client polling.

### Event Payload Schema:
```json
{
  "type": "update",
  "data": {
    "id": "b98c8a17-fcff-45cf-9639-dfcf2033dfb1",
    "customerAccount": "ACC-9402",
    "requestType": "LINE_DIAGNOSTIC",
    "status": "PROCESSING",
    "progress": 40,
    "operatorUsername": "operator1",
    "logMessage": "[INFO] Analyzing packet loss and latency metrics..."
  }
}
```

---

## Automated Testing

The backend includes a comprehensive reactive unit test suite covering controllers, services, role-based authorization, and cancellation state machines.

To run tests:
```bash
./mvnw clean test
```

### Test Coverage Highlights:
- **Reactive Assertions**: Uses `StepVerifier` to assert the behavior of complex asynchronous `Mono` and `Flux` chains.
- **Service Isolation**: Mockito is used to perfectly isolate domain logic from the R2DBC persistence layer.
- **100% Core Business Logic Coverage**: Every critical state transition (canceling an already completed task, deleting an active task) is verified against edge cases.

---

## Observability & Monitoring

### Dozzle (Live Container Log Aggregation)
Instead of manually tailing docker logs, we have deployed an independent **Dozzle** container. Dozzle binds to the Docker socket and provides a real-time web interface for tailing logs across all microservices.

- **Access the Dashboard:** `http://localhost:8888/` (or port 8002 on the VPS)
- **Why it matters:** It allows you to instantly see Spring Boot processing HTTP requests, WebSockets broadcasting, and Postgres queries side-by-side in real-time. Includes regex filtering and fuzzy search.

---


## DevOps & CI/CD Pipeline

The project features a complete **Continuous Integration and Continuous Deployment (CI/CD)** pipeline utilizing **GitHub Actions**, **Docker Compose**, and remote VPS management.

### Deployment Workflow:
1. **GitHub Actions Trigger:** Pushing to the `main` branch automatically triggers the `deploy.yml` workflow.
2. **Secure SSH Connect:** The runner authenticates to the production VPS over SSH using strictly scoped GitHub Secrets (`VPS_HOST`, `VPS_USERNAME`, `VPS_PASSWORD`).
3. **Automated Sync:** The script executes `git fetch` and `git reset --hard` to synchronize the live server with the latest repository state.
4. **Zero-Downtime Container Rebuilds:** 
   - Uses `docker compose build --no-cache` to ensure the absolute latest dependencies and application code are baked into the new Docker images.
   - Executes `docker compose up -d` to intelligently recreate only the containers that have changed.
5. **Database Preservation:** The PostgreSQL database utilizes Docker named volumes (`postgres_data`) to perfectly persist customer data across all automated rebuilds and deployments.

*This pipeline demonstrates a modern, hands-off approach to application deployment, ensuring that code merged to `main` is instantly and safely deployed to production.*

## API Documentation (Swagger UI)

The backend provides a fully interactive OpenAPI 3 schema and documentation interfaces natively.

1. **Swagger UI**: `https://test.anchordown.app/webjars/swagger-ui/index.html` (Interactive API explorer)
2. **Raw Schema**: `https://test.anchordown.app/v3/api-docs` (JSON OpenAPI schema for codegen)

*Note: Ensure you authenticate via the "Authorize" button using your JWT token to interact with secured endpoints.*
