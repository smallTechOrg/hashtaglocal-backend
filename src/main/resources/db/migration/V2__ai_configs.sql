-- AI connection for quiz generation, editable from the Ops Dashboard (admin only).
-- A connection is a name the admin chooses plus an OpenAI-compatible endpoint, a model and a key.
-- One table holds both the current connection and every earlier version: each Save adds a row
-- with is_active = true and sets the previous row inactive; "Remove key" sets the active row
-- inactive. With no active row, quiz generation uses the ai.quiz.chain from .env.
-- The API key is stored only as AES-256-GCM ciphertext, and only on the active row (it is cleared
-- when a row is set inactive). The master key is the AI_CONFIG_ENCRYPTION_KEY env var, never the
-- database.
-- Safe to re-run; Hibernate (ddl-auto: update) creates the same table where Flyway is off.

CREATE TABLE IF NOT EXISTS ai_configs (
    id                 BIGSERIAL    PRIMARY KEY,
    name               VARCHAR(100) NOT NULL,
    endpoint           VARCHAR(500) NOT NULL,
    model              VARCHAR(200) NOT NULL,
    api_key_ciphertext TEXT,
    api_key_last4      VARCHAR(4),
    is_active          BOOLEAN      NOT NULL,
    created_by         BIGINT       NOT NULL,
    updated_by         BIGINT       NOT NULL,
    -- Both set automatically (Hibernate @CreationTimestamp / @UpdateTimestamp; the defaults cover
    -- rows written outside the app).
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE ai_configs DROP CONSTRAINT IF EXISTS ai_configs_https_endpoint;
ALTER TABLE ai_configs ADD CONSTRAINT ai_configs_https_endpoint
    CHECK (endpoint LIKE 'https://%');

-- Only the active row may carry a key.
ALTER TABLE ai_configs DROP CONSTRAINT IF EXISTS ai_configs_key_only_when_active;
ALTER TABLE ai_configs ADD CONSTRAINT ai_configs_key_only_when_active
    CHECK (is_active OR api_key_ciphertext IS NULL);

-- At most one active row.
CREATE UNIQUE INDEX IF NOT EXISTS ai_configs_one_active ON ai_configs (is_active) WHERE is_active;
