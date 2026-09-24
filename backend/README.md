# NexusFiber - Spring WebFlux Backend

This repository contains the reimplementation of the NexusFiber backend using **Spring Boot 3.3.x**, **Spring WebFlux**, **R2DBC**, and **Reactive Redis**.

## Architecture Highlights
- **100% Non-Blocking:** Replaced Django/Celery synchronous/hybrid processes with fully reactive Project Reactor streams (`Mono` / `Flux`).
- **Background Execution:** Diagnostic tasks run asynchronously on `Schedulers.boundedElastic()`, completely eliminating the need for a separate Celery worker pool while keeping web threads unblocked.
- **Reactive WebSockets:** Uses native Spring WebSockets directly piping Redis Pub/Sub events down to the connected clients via `session.send()`.
- **R2DBC & PostgreSQL:** Fully reactive database interaction; initialization handled via custom `ConnectionFactoryInitializer` reading `schema.sql`.

## Getting Started

You can run the entire backend stack (PostgreSQL, Redis, and the Spring Boot application) instantly using Docker Compose.

```bash
docker compose up --build
```

The API and WebSocket server will be available at `http://localhost:8080`.

*(Note: The initial run will pull the Maven Docker image and compile the Java application before launching the final JRE container.)*
