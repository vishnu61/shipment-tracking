# Shipment Tracking API

Real-Time Shipment Event Tracking platform built with Java 17, Spring Boot 2.7, PostgreSQL 15.

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (for running with containers)
- PostgreSQL 12+ (for running locally)

### Option 1 — Docker Compose (recommended)

```bash
# Clone the repository
git clone https://github.com/your-org/shipment-tracking.git
cd shipment-tracking

# Set a secure JWT secret
export JWT_SECRET="your-secure-secret-at-least-32-characters-long"

# Start PostgreSQL + API
docker compose up --build

# API available at http://localhost:8080
```

### Option 2 — Local Development

**1. Start PostgreSQL**
```bash
docker run -d \
  --name shipment_pg \
  -e POSTGRES_DB=shipment_tracking \
  -e POSTGRES_USER=tracking_user \
  -e POSTGRES_PASSWORD=tracking_pass \
  -p 5432:5432 \
  postgres:15-alpine
```

**2. Run schema migrations**
```bash
psql -h localhost -U tracking_user -d shipment_tracking -f db/schema.sql
```

**3. Configure and run the application**
```bash
# Set environment variables
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shipment_tracking
export SPRING_DATASOURCE_USERNAME=tracking_user
export SPRING_DATASOURCE_PASSWORD=tracking_pass
export JWT_SECRET=your-secret-key-at-least-32-characters

# Run
mvn spring-boot:run
```

---

## API Usage Examples

### Authenticate
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@company.com","password":"password123"}'

# Response: { "accessToken": "eyJ...", "refreshToken": "eyJ...", "expiresIn": 3600 }
```

### Record a Shipment Event
```bash
curl -X POST http://localhost:8080/api/v1/shipments/SHP-12345/events \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "IN_TRANSIT",
    "timestamp": "2026-04-17T14:30:00Z",
    "location": {
      "latitude": 40.7128,
      "longitude": -74.0060,
      "address": "New York, NY"
    },
    "metadata": {"carrier": "FastFreight", "vehicle": "TRUCK-789"}
  }'
```

### Get Shipment Status
```bash
curl http://localhost:8080/api/v1/shipments/SHP-12345/status \
  -H "Authorization: Bearer <accessToken>"
```

### Get Event History (paginated)
```bash
curl "http://localhost:8080/api/v1/shipments/SHP-12345/events?page=0&size=20" \
  -H "Authorization: Bearer <accessToken>"
```

### Register a Webhook
```bash
curl -X POST http://localhost:8080/api/v1/webhooks \
  -H "Authorization: Bearer <accessToken>" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://yourapp.com/webhooks/shipments",
    "eventTypes": ["DELIVERED", "EXCEPTION"]
  }'
```

---

## Running Tests

```bash
# Unit tests only (fast, no DB needed)
mvn test -Dtest="*ServiceTest,*ControllerTest,*SecurityTest"

# All tests including integration (requires Docker for TestContainers)
mvn verify

# With coverage report
mvn clean verify
open target/site/jacoco/index.html
```

---

## Configuration Reference

| Property | Env Var | Default | Description |
|----------|---------|---------|-------------|
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | localhost:5432 | PostgreSQL JDBC URL |
| `app.jwt.secret` | `JWT_SECRET` | *(required)* | HMAC-SHA256 signing secret (min 32 chars) |
| `app.jwt.expiration-ms` | — | `3600000` | Access token TTL (1 hour) |
| `app.jwt.refresh-expiration-ms` | — | `86400000` | Refresh token TTL (24 hours) |
| `app.rate-limit.requests-per-minute` | — | `1000` | Rate limit per company per minute |

---

## API Documentation

Interactive Swagger UI is available at:
```
http://localhost:8080/swagger-ui/index.html
```

OpenAPI JSON spec:
```
http://localhost:8080/v3/api-docs
```

See [API_DESIGN.md](./API_DESIGN.md) for the full OpenAPI 3.0 specification in YAML format.

See [ARCHITECTURE.md](./ARCHITECTURE.md) for system design and architecture decisions.

---

## Project Structure

```
shipment-tracking/
├── src/
│   ├── main/java/com/logistics/tracking/
│   │   ├── controller/        # REST controllers
│   │   ├── service/           # Business logic (interfaces + impl/)
│   │   ├── repository/        # Spring Data JPA repositories
│   │   ├── model/             # JPA entities
│   │   ├── dto/               # Request/response objects
│   │   ├── security/          # JWT filter + token provider
│   │   ├── exception/         # Custom exceptions + global handler
│   │   └── config/            # Spring Security config
│   └── test/java/
│       ├── service/           # Unit tests (Mockito)
│       ├── controller/        # MockMvc tests
│       ├── security/          # JWT tests
│       └── integration/       # TestContainers full-stack tests
├── db/
│   └── schema.sql             # PostgreSQL schema with partitioning
├── .github/workflows/
│   └── ci.yml                 # GitHub Actions CI/CD
├── Dockerfile                 # Multi-stage build
├── docker-compose.yml
├── API_DESIGN.md              # OpenAPI 3.0 spec + auth flow
├── ARCHITECTURE.md            # System design decisions
└── pom.xml
```
