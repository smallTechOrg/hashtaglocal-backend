-- Migration: enforce NOT NULL on user_auth_sessions expiry columns
-- Run AFTER migrate-notification-token-consolidation.sql.
--
-- Step 1: backfill any existing NULLs with a value that is already expired (epoch 0 = 1970).
--         This forces those sessions to be rejected immediately rather than treated as valid-forever.
UPDATE user_auth_sessions
SET access_token_expiry_ts = 0
WHERE access_token_expiry_ts IS NULL;

UPDATE user_auth_sessions
SET refresh_token_expiry_ts = 0
WHERE refresh_token_expiry_ts IS NULL;

-- Step 2: add NOT NULL constraints so the application can rely on these columns always being set.
ALTER TABLE user_auth_sessions
  ALTER COLUMN access_token_expiry_ts SET NOT NULL,
  ALTER COLUMN refresh_token_expiry_ts SET NOT NULL;

-- Rollback:
-- ALTER TABLE user_auth_sessions
--   ALTER COLUMN access_token_expiry_ts DROP NOT NULL,
--   ALTER COLUMN refresh_token_expiry_ts DROP NOT NULL;
