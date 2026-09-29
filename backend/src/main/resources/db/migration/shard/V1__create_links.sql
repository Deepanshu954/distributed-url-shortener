-- V1: per-shard schema for the core shortener
CREATE TABLE IF NOT EXISTS links (
    id              BIGINT PRIMARY KEY,                   -- Snowflake, minted in-process (ADR-001)
    short_code      VARCHAR(32)   NOT NULL,               -- Base62 code or custom alias; shard routing key
    long_url        VARCHAR(8192) NOT NULL,               -- max length frozen in ADR-011
    user_id         BIGINT,                               -- nullable guest/optional client id
    created_at      TIMESTAMP WITH TIME ZONE   NOT NULL DEFAULT now(),
    expires_at      TIMESTAMP WITH TIME ZONE,                          -- NULL = never expires
    is_custom_alias BOOLEAN       NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_links_short_code ON links (short_code);
CREATE INDEX IF NOT EXISTS ix_links_created_at ON links (created_at DESC);

-- Idempotency-Key -> short_code for POST /api/links
CREATE TABLE IF NOT EXISTS idempotency_keys (
    "key"       VARCHAR(128) PRIMARY KEY,
    short_code  VARCHAR(32)  NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE  NOT NULL DEFAULT now()
);
