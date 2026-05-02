-- ============================================================
-- Shipment Tracking Platform - PostgreSQL Schema
-- Optimized for 10,000+ events/minute, multi-tenancy, archiving
-- ============================================================

-- -----------------------------------------------
-- EXTENSION
-- -----------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pg_trgm; -- for text search on addresses

-- -----------------------------------------------
-- COMPANIES (Tenants)
-- -----------------------------------------------
CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(255) NOT NULL,
    api_key_hash    VARCHAR(255) NOT NULL UNIQUE,  -- hashed API key
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- -----------------------------------------------
-- USERS
-- -----------------------------------------------
CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    role            VARCHAR(50) NOT NULL DEFAULT 'USER',  -- USER, ADMIN
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_company_id ON users(company_id);
CREATE INDEX idx_users_email ON users(email);

-- -----------------------------------------------
-- SHIPMENTS
-- Partitioned by created_at for archiving old data
-- -----------------------------------------------
CREATE TABLE shipments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    shipment_id     VARCHAR(100) NOT NULL,  -- external ID e.g. SHP-12345
    company_id      UUID NOT NULL REFERENCES companies(id),
    carrier         VARCHAR(100),
    origin          JSONB NOT NULL,         -- { "address": "...", "lat": ..., "lng": ... }
    destination     JSONB NOT NULL,
    current_status  VARCHAR(50) NOT NULL DEFAULT 'CREATED',
    current_location JSONB,
    eta             TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_shipment_company UNIQUE (shipment_id, company_id)
) PARTITION BY RANGE (created_at);

-- Partitions — one per quarter (example for 2026)
CREATE TABLE shipments_2026_q1 PARTITION OF shipments
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE shipments_2026_q2 PARTITION OF shipments
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE shipments_2026_q3 PARTITION OF shipments
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE shipments_2026_q4 PARTITION OF shipments
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');

-- Indexes on shipments
CREATE INDEX idx_shipments_company_id       ON shipments(company_id);
CREATE INDEX idx_shipments_shipment_id      ON shipments(shipment_id);
CREATE INDEX idx_shipments_status           ON shipments(current_status);
CREATE INDEX idx_shipments_company_status   ON shipments(company_id, current_status);
CREATE INDEX idx_shipments_created_at       ON shipments(created_at);

-- -----------------------------------------------
-- SHIPMENT_EVENTS
-- Immutable audit log. Heavy read/write. Partitioned by created_at.
-- -----------------------------------------------
CREATE TABLE shipment_events (
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    event_id        VARCHAR(100) NOT NULL,   -- e.g. EVT-<uuid>
    shipment_id     VARCHAR(100) NOT NULL,
    company_id      UUID NOT NULL,           -- denormalized for tenant isolation at row level
    event_type      VARCHAR(50) NOT NULL,    -- CREATED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, EXCEPTION
    event_timestamp TIMESTAMPTZ NOT NULL,
    location        JSONB,                   -- { "latitude": ..., "longitude": ..., "address": "..." }
    metadata        JSONB,                   -- carrier-specific fields: vehicle, driver, etc.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, created_at)             -- include partition key for proper partitioning
) PARTITION BY RANGE (created_at);

-- Partitions for shipment_events (monthly — high volume)
CREATE TABLE shipment_events_2026_01 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE shipment_events_2026_02 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE shipment_events_2026_03 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE shipment_events_2026_04 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE shipment_events_2026_05 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE shipment_events_2026_06 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');
CREATE TABLE shipment_events_2026_07 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-07-01') TO ('2026-08-01');
CREATE TABLE shipment_events_2026_08 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-08-01') TO ('2026-09-01');
CREATE TABLE shipment_events_2026_09 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-09-01') TO ('2026-10-01');
CREATE TABLE shipment_events_2026_10 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-10-01') TO ('2026-11-01');
CREATE TABLE shipment_events_2026_11 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-11-01') TO ('2026-12-01');
CREATE TABLE shipment_events_2026_12 PARTITION OF shipment_events
    FOR VALUES FROM ('2026-12-01') TO ('2027-01-01');

-- Catch-all for future data
CREATE TABLE shipment_events_future PARTITION OF shipment_events
    FOR VALUES FROM ('2027-01-01') TO (MAXVALUE);

-- Indexes on shipment_events (created on parent, auto-inherited by partitions)
CREATE INDEX idx_events_shipment_id       ON shipment_events(shipment_id, created_at DESC);
CREATE INDEX idx_events_company_id        ON shipment_events(company_id, created_at DESC);
CREATE INDEX idx_events_event_type        ON shipment_events(event_type);
CREATE INDEX idx_events_event_timestamp   ON shipment_events(event_timestamp DESC);
-- Partial index for active (recent) events — most queries are recent
CREATE INDEX idx_events_company_shipment  ON shipment_events(company_id, shipment_id, created_at DESC);
-- GIN index for JSONB location search
CREATE INDEX idx_events_location_gin      ON shipment_events USING GIN (location);
CREATE INDEX idx_events_metadata_gin      ON shipment_events USING GIN (metadata);

-- -----------------------------------------------
-- WEBHOOKS
-- -----------------------------------------------
CREATE TABLE webhooks (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    url             VARCHAR(2048) NOT NULL,
    secret          VARCHAR(255) NOT NULL,   -- HMAC secret for payload signing
    event_types     TEXT[] NOT NULL DEFAULT '{}',  -- empty = all events
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_webhooks_company_id      ON webhooks(company_id);
CREATE INDEX idx_webhooks_active          ON webhooks(company_id, is_active) WHERE is_active = TRUE;

-- -----------------------------------------------
-- WEBHOOK_DELIVERY_LOGS
-- Audit trail. Partitioned monthly. Never updated (immutable).
-- -----------------------------------------------
CREATE TABLE webhook_delivery_logs (
    id              UUID NOT NULL DEFAULT uuid_generate_v4(),
    webhook_id      UUID NOT NULL,          -- not FK for partition performance
    company_id      UUID NOT NULL,
    event_id        VARCHAR(100) NOT NULL,
    shipment_id     VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    http_status     INT,
    response_body   TEXT,
    attempt_number  INT NOT NULL DEFAULT 1,
    delivered_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE webhook_delivery_logs_2026_q1 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');
CREATE TABLE webhook_delivery_logs_2026_q2 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-04-01') TO ('2026-07-01');
CREATE TABLE webhook_delivery_logs_2026_q3 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-07-01') TO ('2026-10-01');
CREATE TABLE webhook_delivery_logs_2026_q4 PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2026-10-01') TO ('2027-01-01');
CREATE TABLE webhook_delivery_logs_future PARTITION OF webhook_delivery_logs
    FOR VALUES FROM ('2027-01-01') TO (MAXVALUE);

CREATE INDEX idx_wdl_webhook_id   ON webhook_delivery_logs(webhook_id, created_at DESC);
CREATE INDEX idx_wdl_company_id   ON webhook_delivery_logs(company_id, created_at DESC);
CREATE INDEX idx_wdl_event_id     ON webhook_delivery_logs(event_id);
CREATE INDEX idx_wdl_delivered    ON webhook_delivery_logs(delivered_at) WHERE delivered_at IS NULL;

-- -----------------------------------------------
-- API_RATE_LIMITS
-- Sliding window per company. Updated frequently.
-- -----------------------------------------------
CREATE TABLE api_rate_limits (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    company_id      UUID NOT NULL REFERENCES companies(id),
    window_start    TIMESTAMPTZ NOT NULL,
    request_count   INT NOT NULL DEFAULT 0,
    window_seconds  INT NOT NULL DEFAULT 60,
    max_requests    INT NOT NULL DEFAULT 1000,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_rate_limit_company_window ON api_rate_limits(company_id, window_start);
CREATE INDEX idx_rate_limit_window ON api_rate_limits(window_start);

-- -----------------------------------------------
-- REFRESH TOKENS (Bonus: JWT refresh)
-- -----------------------------------------------
CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID NOT NULL REFERENCES users(id),
    token_hash      VARCHAR(255) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user    ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_expiry  ON refresh_tokens(expires_at) WHERE NOT revoked;

-- -----------------------------------------------
-- TRIGGERS: auto-update updated_at
-- -----------------------------------------------
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_companies_updated_at BEFORE UPDATE ON companies
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_shipments_updated_at BEFORE UPDATE ON shipments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_webhooks_updated_at BEFORE UPDATE ON webhooks
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_rate_limits_updated_at BEFORE UPDATE ON api_rate_limits
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- -----------------------------------------------
-- ROW LEVEL SECURITY (Multi-tenancy)
-- -----------------------------------------------
ALTER TABLE shipments        ENABLE ROW LEVEL SECURITY;
ALTER TABLE shipment_events  ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhooks         ENABLE ROW LEVEL SECURITY;
ALTER TABLE webhook_delivery_logs ENABLE ROW LEVEL SECURITY;

-- App role uses current_setting to enforce tenant isolation
CREATE POLICY shipments_tenant_isolation ON shipments
    USING (company_id::text = current_setting('app.current_company_id', TRUE));

CREATE POLICY events_tenant_isolation ON shipment_events
    USING (company_id::text = current_setting('app.current_company_id', TRUE));

CREATE POLICY webhooks_tenant_isolation ON webhooks
    USING (company_id::text = current_setting('app.current_company_id', TRUE));

CREATE POLICY wdl_tenant_isolation ON webhook_delivery_logs
    USING (company_id::text = current_setting('app.current_company_id', TRUE));

-- -----------------------------------------------
-- ARCHIVING STRATEGY
-- Old partitions can be detached and moved to cold storage:
--   ALTER TABLE shipment_events DETACH PARTITION shipment_events_2025_01;
--   Then pg_dump / move to S3 / drop
-- -----------------------------------------------
