-- Migration: consolidate notification tokens into user_auth_sessions
-- Run BEFORE deploying the new JAR.

-- Step 1: extend user_auth_sessions
ALTER TABLE user_auth_sessions
  ADD COLUMN notification_token TEXT,
  ADD COLUMN platform VARCHAR(15);

-- Step 2: drop the replaced table
-- (no data migration — tokens may be stale; clients re-register via POST /account/device-token)
DROP TABLE device_tokens;

-- Step 3: add indexes required for production performance
-- Every authenticated request looks up by access_token — must be a point lookup, not a scan.
CREATE UNIQUE INDEX idx_uas_access_token
    ON user_auth_sessions (access_token);

-- Every token refresh looks up by refresh_token.
CREATE UNIQUE INDEX idx_uas_refresh_token
    ON user_auth_sessions (refresh_token);

-- Notification queries, account deletion, and session cap all filter by user_id.
-- The composite (user_id, platform) index covers plain user_id queries via its leading column.
CREATE INDEX idx_uas_user_platform
    ON user_auth_sessions (user_id, platform);

-- Rollback (apply before JAR rollback if needed):
-- DROP INDEX idx_uas_user_platform;
-- DROP INDEX idx_uas_refresh_token;
-- DROP INDEX idx_uas_access_token;
-- ALTER TABLE user_auth_sessions DROP COLUMN notification_token, DROP COLUMN platform;
-- Restore device_tokens from a pre-migration DB snapshot if required.
