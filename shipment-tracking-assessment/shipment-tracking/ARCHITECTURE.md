# Architecture — Real-Time Shipment Tracking Platform

## System Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        API Layer                              │
│   AuthController  ShipmentEventController  WebhookController │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                      Security Layer                           │
│        JwtAuthenticationFilter  →  SecurityContextHolder      │
└───────────────────────────┬──────────────────────────────────┘
                            │
┌───────────────────────────▼──────────────────────────────────┐
│                      Service Layer                            │
│  ShipmentEventService  WebhookService  RateLimitService       │
│                   WebhookDispatchService (Async)              │
└──────────┬──────────────────────────────┬────────────────────┘
           │                              │
┌──────────▼──────────┐      ┌────────────▼───────────────────┐
│  Repository Layer   │      │   Async Webhook Dispatch        │
│  JPA + PostgreSQL   │      │   WebClient + Retry (3 attempts)│
└─────────────────────┘      └─────────────────────────────────┘
```

## Package Structure

```
com.logistics.tracking/
├── ShipmentTrackingApplication.java
├── config/
│   └── SecurityConfig.java          # Spring Security + JWT filter
├── controller/
│   ├── AuthController.java
│   ├── ShipmentEventController.java
│   └── WebhookController.java
├── dto/
│   ├── request/
│   │   ├── CreateEventRequest.java
│   │   ├── CreateWebhookRequest.java
│   │   └── LoginRequest.java
│   └── response/
│       ├── AuthResponse.java
│       ├── ShipmentEventResponse.java
│       ├── ShipmentStatusResponse.java
│       └── WebhookResponse.java
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ResourceNotFoundException.java
│   └── RateLimitExceededException.java
├── model/
│   ├── Company.java
│   ├── EventType.java
│   ├── Shipment.java
│   ├── ShipmentEvent.java
│   ├── User.java
│   └── Webhook.java
├── repository/
│   ├── ShipmentEventRepository.java
│   ├── ShipmentRepository.java
│   ├── UserRepository.java
│   └── WebhookRepository.java
├── security/
│   ├── JwtAuthenticationFilter.java
│   └── JwtTokenProvider.java
└── service/
    ├── RateLimitService.java
    ├── ShipmentEventService.java        # interface
    ├── WebhookDispatchService.java      # async dispatch
    ├── WebhookService.java              # interface
    └── impl/
        ├── AuthService.java
        ├── ShipmentEventServiceImpl.java
        ├── UserDetailsServiceImpl.java
        └── WebhookServiceImpl.java
```

## Key Design Decisions

### 1. Multi-Tenancy via `company_id` + Row-Level Security

Every data entity carries a `company_id`. The service layer always passes `companyId` extracted from the JWT into every repository query. PostgreSQL Row-Level Security policies provide a second layer of defence — even if application code had a bug, queries would still be filtered.

### 2. Immutable Event Log

`shipment_events` records are never updated or deleted. New events are appended only. This supports complete audit trails, event sourcing patterns, and prevents data loss. Archiving is done by detaching old partitions rather than DELETE.

### 3. Table Partitioning for Scale

`shipment_events` is partitioned monthly and `shipments` quarterly. At 10,000 events/minute that is ~14.4 billion events/year. Partitioning ensures:
- Query planner only scans relevant partitions
- Old partitions can be archived (detached → S3) without locking
- Indexes remain small and cache-friendly per partition

### 4. Async Webhook Delivery

Webhook dispatch uses Spring `@Async` with a dedicated thread pool. This means the HTTP response to the event producer is returned immediately — webhook failures never block or slow down the event recording path. Delivery is retried up to 3 times with exponential back-off using Project Reactor's `WebClient`.

### 5. Rate Limiting via Atomic DB Upsert

The rate limiter uses a PostgreSQL `INSERT ... ON CONFLICT DO UPDATE ... WHERE count < max` — this is atomic and prevents race conditions without requiring Redis. For extreme scale (>10k req/s), migrate to Redis `INCR` + `EXPIRE`.

### 6. JWT with Company Claim

The access token embeds `companyId` as a claim. This means every authenticated request has the tenant ID available without a DB lookup, reducing latency on the hot path.

## Performance Considerations

| Concern | Strategy |
|---------|----------|
| 10,000 events/min write throughput | JPA batch inserts (`hibernate.jdbc.batch_size=50`) |
| Fast event reads by shipment | Composite index `(shipment_id, company_id, created_at DESC)` |
| JSONB location queries | GIN index on `location` column |
| Old data archival | Detach + pg_dump old monthly partitions to cold storage |
| Webhook fan-out latency | Spring `@Async` with dedicated thread pool (10 core, 50 max) |

## Security Architecture

- **Authentication:** JWT HS256 (access: 1h, refresh: 24h)
- **Authorization:** Spring Security + `@AuthenticationPrincipal` extracts companyId per request
- **Multi-tenancy:** All queries scoped to `companyId` from JWT; backed by PostgreSQL RLS
- **Webhook signing:** HMAC-SHA256 of payload with per-webhook secret
- **Password storage:** BCrypt with strength 12
- **SQL injection:** JPA parameterised queries throughout; no string concatenation
- **Input validation:** Bean Validation (`@Valid`) on all request bodies

## Testing Strategy

| Layer | Framework | Coverage Target |
|-------|-----------|----------------|
| Unit — services | JUnit 5 + Mockito | 80%+ line coverage |
| Unit — JWT/security | JUnit 5 | Key security paths |
| Controller (web) | MockMvc + `@WebMvcTest` | All endpoints + error cases |
| Integration | TestContainers (real PostgreSQL) | Full request lifecycle |
| Multi-tenancy | Integration tests | Cross-tenant isolation verified |
