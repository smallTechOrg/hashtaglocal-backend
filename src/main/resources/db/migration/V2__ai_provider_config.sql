-- AI provider for quiz generation, editable from the Ops Dashboard (admin only).
-- Single row (id = 1). When absent, quiz generation uses the ai.quiz.chain from .env.
-- The API key is stored only as AES-256-GCM ciphertext; the master key is the
-- AI_CONFIG_ENCRYPTION_KEY env var and is never stored in the database.
-- Safe to re-run; Hibernate (ddl-auto: update) creates the same tables where Flyway is off.

CREATE TABLE IF NOT EXISTS ai_provider_config (
    id                 BIGINT       PRIMARY KEY,
    provider           VARCHAR(50)  NOT NULL,
    endpoint           VARCHAR(500) NOT NULL,
    model              VARCHAR(200) NOT NULL,
    api_key_ciphertext TEXT         NOT NULL,
    api_key_last4      VARCHAR(4),
    updated_by         BIGINT       NOT NULL,
    updated_at         TIMESTAMP(6) NOT NULL,
    version            BIGINT
);

ALTER TABLE ai_provider_config DROP CONSTRAINT IF EXISTS ai_provider_config_single_row;
ALTER TABLE ai_provider_config ADD CONSTRAINT ai_provider_config_single_row CHECK (id = 1);

ALTER TABLE ai_provider_config DROP CONSTRAINT IF EXISTS ai_provider_config_https_endpoint;
ALTER TABLE ai_provider_config
    ADD CONSTRAINT ai_provider_config_https_endpoint CHECK (endpoint LIKE 'https://%');

CREATE TABLE IF NOT EXISTS ai_provider_config_audit (
    id          BIGSERIAL    PRIMARY KEY,
    action      VARCHAR(20)  NOT NULL,
    provider    VARCHAR(50),
    endpoint    VARCHAR(500),
    model       VARCHAR(200),
    key_changed BOOLEAN      NOT NULL,
    changed_by  BIGINT       NOT NULL,
    changed_at  TIMESTAMP(6) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ai_provider_config_audit_changed_at
    ON ai_provider_config_audit (changed_at DESC);
