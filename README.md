# 3D Virtual Campus Management Software

Full-stack web application skeleton (frontend + backend in one repo, containerized with Docker).

## Tech Stack

- **Frontend**: React (Vite), SCSS, Jest + Testing Library
- **Backend**: Spring Boot (Java 21, Maven), Spring Data JPA, Spring Data Redis, springdoc-openapi (Swagger UI), ModelMapper, Lombok
- **Database**: PostgreSQL
- **Cache**: Redis
- **DevOps**: Docker & Docker Compose (frontend, backend, db, redis)

## Project Structure

```
.
├── backend/    # Spring Boot REST API (Controller-Service-Repository)
├── frontend/   # React SPA
└── docker-compose.yml
```

## Run with Docker

```bash
docker compose up --build
```

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api/health
- Swagger UI: http://localhost:8080/swagger-ui.html

## Local Development

### Backend

```bash
cd backend
./mvnw spring-boot:run
```

Requires a local PostgreSQL (`campus` db, `postgres`/`postgres`) and Redis, or override via env vars
(`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `SPRING_DATA_REDIS_HOST`).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server proxies `/api` requests to `http://localhost:8080`.

### Tests

```bash
cd backend && ./mvnw test
cd frontend && npm test
```
