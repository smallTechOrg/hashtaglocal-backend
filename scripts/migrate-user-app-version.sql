-- Migration: record the latest mobile app version on each user.
-- Run BEFORE deploying this feature (staging first). Hibernate's ddl-auto won't do it.
-- Nullable: existing users and older app builds don't send a version.

BEGIN;

ALTER TABLE users
  ADD COLUMN IF NOT EXISTS app_version VARCHAR(50);

COMMIT;

-- Rollback:
-- ALTER TABLE users DROP COLUMN IF EXISTS app_version;
