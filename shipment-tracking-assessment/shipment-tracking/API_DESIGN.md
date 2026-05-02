# API Design — Real-Time Shipment Tracking

## OpenAPI 3.0 Specification

```yaml
openapi: 3.0.3
info:
  title: Shipment Tracking API
  description: Real-Time Shipment Event Tracking for logistics companies
  version: 1.0.0
  contact:
    name: API Support
    email: api@logistics.com

servers:
  - url: https://api.logistics.com/api/v1
    description: Production
  - url: http://localhost:8080/api/v1
    description: Local Development

security:
  - bearerAuth: []

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT

  schemas:
    EventType:
      type: string
      enum: [CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION, RETURNED]

    Location:
      type: object
      properties:
        latitude:  { type: number, format: double, example: 40.7128 }
        longitude: { type: number, format: double, example: -74.0060 }
        address:   { type: string, example: "New York, NY" }

    CreateEventRequest:
      type: object
      required: [eventType, timestamp]
      properties:
        eventType: { $ref: '#/components/schemas/EventType' }
        timestamp:
          type: string
          format: date-time
          example: "2026-04-17T14:30:00Z"
        location:  { $ref: '#/components/schemas/Location' }
        metadata:
          type: object
          additionalProperties: true
          example: { carrier: "FastFreight", vehicle: "TRUCK-789" }

    ShipmentEventResponse:
      type: object
      properties:
        eventId:    { type: string, example: "EVT-550e8400-e29b-41d4-a716-446655440000" }
        shipmentId: { type: string, example: "SHP-12345" }
        eventType:  { $ref: '#/components/schemas/EventType' }
        timestamp:  { type: string, format: date-time }
        location:   { $ref: '#/components/schemas/Location' }
        metadata:   { type: object, additionalProperties: true }
        createdAt:  { type: string, format: date-time }

    ShipmentStatusResponse:
      type: object
      properties:
        shipmentId:       { type: string }
        currentStatus:    { $ref: '#/components/schemas/EventType' }
        currentLocation:  { $ref: '#/components/schemas/Location' }
        eta:              { type: string, format: date-time, nullable: true }
        carrier:          { type: string, nullable: true }
        lastUpdated:      { type: string, format: date-time }
        origin:           { type: object, additionalProperties: true }
        destination:      { type: object, additionalProperties: true }

    PagedEventsResponse:
      type: object
      properties:
        content:
          type: array
          items: { $ref: '#/components/schemas/ShipmentEventResponse' }
        totalElements: { type: integer }
        totalPages:    { type: integer }
        size:          { type: integer }
        number:        { type: integer }

    CreateWebhookRequest:
      type: object
      required: [url]
      properties:
        url:
          type: string
          format: uri
          example: "https://yourapp.com/webhooks/shipments"
        secret:
          type: string
          description: HMAC secret for payload signing. Auto-generated if omitted.
        eventTypes:
          type: array
          items: { $ref: '#/components/schemas/EventType' }
          description: Subscribe to specific event types. Empty = all events.

    WebhookResponse:
      type: object
      properties:
        id:         { type: string, format: uuid }
        url:        { type: string, format: uri }
        eventTypes: { type: array, items: { type: string } }
        active:     { type: boolean }
        createdAt:  { type: string, format: date-time }

    LoginRequest:
      type: object
      required: [email, password]
      properties:
        email:    { type: string, format: email }
        password: { type: string, minLength: 8 }

    AuthResponse:
      type: object
      properties:
        accessToken:  { type: string }
        refreshToken: { type: string }
        tokenType:    { type: string, example: "Bearer" }
        expiresIn:    { type: integer, example: 3600 }

    ErrorResponse:
      type: object
      properties:
        status:      { type: integer }
        message:     { type: string }
        timestamp:   { type: string, format: date-time }
        fieldErrors:
          type: object
          additionalProperties: { type: string }
          nullable: true

paths:
  /auth/login:
    post:
      tags: [Authentication]
      summary: Authenticate and receive JWT tokens
      security: []
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/LoginRequest' }
      responses:
        '200':
          description: Authentication successful
          content:
            application/json:
              schema: { $ref: '#/components/schemas/AuthResponse' }
        '401':
          description: Invalid credentials
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }

  /shipments/{shipmentId}/events:
    post:
      tags: [Shipment Events]
      summary: Record a new shipment event
      parameters:
        - name: shipmentId
          in: path
          required: true
          schema: { type: string }
          example: SHP-12345
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateEventRequest' }
            example:
              eventType: IN_TRANSIT
              timestamp: "2026-04-17T14:30:00Z"
              location:
                latitude: 40.7128
                longitude: -74.0060
                address: "New York, NY"
              metadata:
                carrier: FastFreight
                vehicle: TRUCK-789
      responses:
        '201':
          description: Event created
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ShipmentEventResponse' }
        '400':
          description: Validation error
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ErrorResponse' }
        '401':
          description: Unauthorized
        '429':
          description: Rate limit exceeded (1000 req/min per company)
          headers:
            X-RateLimit-Limit:
              schema: { type: integer }
            X-RateLimit-Remaining:
              schema: { type: integer }
            X-RateLimit-Reset:
              schema: { type: string, format: date-time }
        '500':
          description: Internal server error

    get:
      tags: [Shipment Events]
      summary: Retrieve all events for a shipment (paginated)
      parameters:
        - name: shipmentId
          in: path
          required: true
          schema: { type: string }
        - name: page
          in: query
          schema: { type: integer, default: 0 }
        - name: size
          in: query
          schema: { type: integer, default: 20, maximum: 100 }
        - name: sort
          in: query
          schema: { type: string, default: "eventTimestamp,desc" }
      responses:
        '200':
          description: Paginated list of events
          content:
            application/json:
              schema: { $ref: '#/components/schemas/PagedEventsResponse' }
        '404':
          description: Shipment not found

  /shipments/{shipmentId}/status:
    get:
      tags: [Shipment Events]
      summary: Get current status of a shipment
      parameters:
        - name: shipmentId
          in: path
          required: true
          schema: { type: string }
      responses:
        '200':
          description: Current shipment status
          content:
            application/json:
              schema: { $ref: '#/components/schemas/ShipmentStatusResponse' }
        '404':
          description: Shipment not found

  /webhooks:
    post:
      tags: [Webhooks]
      summary: Register a webhook subscription
      requestBody:
        required: true
        content:
          application/json:
            schema: { $ref: '#/components/schemas/CreateWebhookRequest' }
      responses:
        '201':
          description: Webhook registered
          content:
            application/json:
              schema: { $ref: '#/components/schemas/WebhookResponse' }
        '400':
          description: Validation error

    get:
      tags: [Webhooks]
      summary: List all active webhooks
      responses:
        '200':
          description: List of webhooks
          content:
            application/json:
              schema:
                type: array
                items: { $ref: '#/components/schemas/WebhookResponse' }

  /webhooks/{webhookId}:
    delete:
      tags: [Webhooks]
      summary: Unregister a webhook
      parameters:
        - name: webhookId
          in: path
          required: true
          schema: { type: string, format: uuid }
      responses:
        '204':
          description: Webhook deactivated
        '404':
          description: Webhook not found
```

---

## Authentication Flow

```
Client                         API Server                    Database
  |                               |                              |
  |-- POST /auth/login ---------->|                              |
  |   { email, password }         |-- findByEmail() ----------->|
  |                               |<-- User record --------------|
  |                               |-- bcrypt.verify()            |
  |                               |-- generateJWT(user)          |
  |<-- 200 { accessToken,         |                              |
  |          refreshToken }       |                              |
  |                               |                              |
  |-- POST /shipments/.../events  |                              |
  |   Authorization: Bearer <JWT> |                              |
  |                               |-- JwtFilter.validate()       |
  |                               |-- extractCompanyId()         |
  |                               |-- createEvent(companyId)     |
  |<-- 201 { eventId, ... }       |                              |
```

---

## Rate Limiting Strategy

**Algorithm:** Fixed window (1-minute buckets) with atomic PostgreSQL upsert.

| Tier | Limit | Window |
|------|-------|--------|
| Default | 1,000 requests | per minute per company |
| Burst | Up to 1,000 within window | No per-second enforcement |

**Response headers on every request:**
```
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 2026-04-17T14:31:00Z
```

**429 response body:**
```json
{
  "status": 429,
  "message": "Rate limit exceeded. Max 1000 requests/minute.",
  "timestamp": "2026-04-17T14:30:45Z"
}
```

For production at extreme scale, migrate window counters to Redis with `INCR` + `EXPIRE` for sub-millisecond performance.

---

## Error Codes Reference

| Code | Meaning | Common Causes |
|------|---------|---------------|
| 400  | Bad Request | Missing required field, invalid enum value, malformed JSON |
| 401  | Unauthorized | Missing or expired JWT token |
| 403  | Forbidden | Valid token but insufficient permissions |
| 404  | Not Found | Shipment or webhook ID doesn't exist / belongs to different company |
| 429  | Too Many Requests | Rate limit exceeded |
| 500  | Internal Server Error | Unexpected server-side failure |

---

## Data Validation Rules

| Field | Rule |
|-------|------|
| `eventType` | Required. Must be one of the EventType enum values |
| `timestamp` | Required. ISO 8601 format. Cannot be in the future by more than 5 minutes |
| `location.latitude` | Optional. Range: -90.0 to 90.0 |
| `location.longitude` | Optional. Range: -180.0 to 180.0 |
| `metadata` | Optional. Max 64 keys. Values must be strings/numbers |
| `webhook.url` | Required. Must start with `http://` or `https://`. Max 2048 chars |
| `webhook.secret` | Optional. Max 255 chars. Auto-generated UUID if omitted |

---

## Webhook Payload & Signature Verification

Outbound webhook POST body:
```json
{
  "eventId": "EVT-550e8400-e29b-41d4-a716-446655440000",
  "shipmentId": "SHP-12345",
  "eventType": "IN_TRANSIT",
  "timestamp": "2026-04-17T14:30:00Z",
  "location": { "latitude": 40.7128, "longitude": -74.006, "address": "New York, NY" },
  "metadata": { "carrier": "FastFreight" }
}
```

Signature header:
```
X-Signature-SHA256: sha256=<base64(HMAC-SHA256(secret, body))>
```

Verify in your receiver:
```python
import hmac, hashlib, base64
expected = base64.b64encode(hmac.new(secret.encode(), body, hashlib.sha256).digest())
assert header_value == f"sha256={expected.decode()}"
```
