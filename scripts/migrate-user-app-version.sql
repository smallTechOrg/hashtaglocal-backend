-- Migration: record the latest mobile app version on each auth session.
-- Run BEFORE deploying this feature (staging first).
-- Nullable: existing sessions and older app builds don't send a version.

BEGIN;

ALTER TABLE user_auth_sessions
  ADD COLUMN IF NOT EXISTS app_version VARCHAR(50);

COMMIT;

-- Rollback:
-- ALTER TABLE user_auth_sessions DROP COLUMN IF EXISTS app_version;
