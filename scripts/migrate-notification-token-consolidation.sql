-- Migration: consolidate notification tokens into user_auth_sessions
-- Run BEFORE deploying the new JAR.

-- Step 1: extend user_auth_sessions
ALTER TABLE user_auth_sessions
  ADD COLUMN notification_token TEXT,
  ADD COLUMN platform VARCHAR(15);

-- Step 2: drop the replaced table
-- (no data migration — tokens may be stale; clients re-register via POST /account/device-token)
DROP TABLE device_tokens;

-- Rollback (apply before JAR rollback if needed):
-- ALTER TABLE user_auth_sessions DROP COLUMN notification_token, DROP COLUMN platform;
-- Restore device_tokens from a pre-migration DB snapshot if required.
