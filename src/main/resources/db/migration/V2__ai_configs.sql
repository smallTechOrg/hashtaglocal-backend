-- AI connection for quiz generation, editable from the Ops Dashboard (admin only).
-- A connection is a name the admin chooses plus an OpenAI-compatible endpoint, a model and a key.
-- One table holds both the current connection and the log of every change: each Save adds a row,
-- and "Remove key" adds a row with no connection. The newest row (highest id) is in effect; when
-- it has no connection, or the table is empty, quiz generation uses the ai.quiz.chain from .env.
-- The API key is stored only as AES-256-GCM ciphertext, and only on the newest row (older rows
-- have it cleared). The master key is the AI_CONFIG_ENCRYPTION_KEY env var, never the database.
-- Safe to re-run; Hibernate (ddl-auto: update) creates the same table where Flyway is off.

CREATE TABLE IF NOT EXISTS ai_configs (
    id                 BIGSERIAL    PRIMARY KEY,
    name               VARCHAR(100),
    endpoint           VARCHAR(500),
    model              VARCHAR(200),
    api_key_ciphertext TEXT,
    api_key_last4      VARCHAR(4),
    key_changed        BOOLEAN      NOT NULL,
    updated_by         BIGINT       NOT NULL,
    -- Both set automatically (Hibernate @CreationTimestamp / @UpdateTimestamp; the defaults cover
    -- rows written outside the app).
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- A row either sets a connection (name, endpoint and model all present) or removes it (all null).
ALTER TABLE ai_configs DROP CONSTRAINT IF EXISTS ai_configs_all_or_none;
ALTER TABLE ai_configs ADD CONSTRAINT ai_configs_all_or_none CHECK (
    (name IS NULL) = (endpoint IS NULL) AND (name IS NULL) = (model IS NULL));

ALTER TABLE ai_configs DROP CONSTRAINT IF EXISTS ai_configs_https_endpoint;
ALTER TABLE ai_configs ADD CONSTRAINT ai_configs_https_endpoint
    CHECK (endpoint IS NULL OR endpoint LIKE 'https://%');

-- A removed row never carries a key.
ALTER TABLE ai_configs DROP CONSTRAINT IF EXISTS ai_configs_no_key_when_removed;
ALTER TABLE ai_configs ADD CONSTRAINT ai_configs_no_key_when_removed
    CHECK (endpoint IS NOT NULL OR api_key_ciphertext IS NULL);
