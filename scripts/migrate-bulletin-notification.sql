-- Migration: notification_log schema for the daily bulletin push
-- Run on staging first, BEFORE enabling BULLETIN_NOTIFICATION_ENABLED=true.
-- (ddl-auto=update never widens check constraints or creates partial indexes.)
--
-- Access pattern this supports:
--   select * from notification_log
--   where type = 'AUTO_CHAT' and sub_type = 'CHAT_BULLETIN'
--     and source_ref_type = 'BULLETIN' and source_ref_id = :bulletin_id;

-- Pre-check: source_ref_type is becoming an enum (ISSUE_UPDATE, BULLETIN). This must return no rows;
-- if it returns any, fix or null out those values first or the app cannot read them.
--   SELECT DISTINCT source_ref_type FROM notification_log
--   WHERE source_ref_type    IS NOT NULL AND source_ref_type NOT IN ('ISSUE_UPDATE', 'BULLETIN');

-- Step 1: new sub_type column (nullable: existing rows have no sub type).
ALTER TABLE notification_log ADD COLUMN IF NOT EXISTS sub_type VARCHAR(100);

-- Step 2: widen/replace the check constraints Hibernate may have generated for the enums.
--   To see what exists:
--   SELECT conname FROM pg_constraint
--   WHERE conrelid = 'notification_log'::regclass AND contype = 'c';
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_type_check;
ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_type_check
  CHECK (type IN ('ISSUE_DETAIL', 'BROADCAST', 'CHAT', 'AUTO_CHAT'));

ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_sub_type_check;
ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_sub_type_check
  CHECK (sub_type IS NULL OR sub_type IN
         ('CHAT_BULLETIN', 'CHAT_POST', 'CHAT_ISSUE', 'BROADCAST_LOCALITY'));

ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_source_ref_type_check;
ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_source_ref_type_check
  CHECK (source_ref_type IS NULL OR source_ref_type IN ('ISSUE_UPDATE', 'BULLETIN'));

-- Step 3: duplicate-send guard, matching the access pattern. The listener inserts its claim row
--         before any FCM call; this index makes a parallel/re-run insert fail, and it also serves
--         the access-pattern lookup above.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_log_bulletin
  ON notification_log (source_ref_id)
  WHERE type = 'AUTO_CHAT' AND sub_type = 'CHAT_BULLETIN' AND source_ref_type = 'BULLETIN';

-- Rollback:
-- DROP INDEX IF EXISTS uq_notification_log_bulletin;
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_source_ref_type_check;
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_sub_type_check;
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_type_check;
-- ALTER TABLE notification_log
--   ADD CONSTRAINT notification_log_type_check
--   CHECK (type IN ('ISSUE_DETAIL', 'BROADCAST', 'CHAT'));
-- ALTER TABLE notification_log DROP COLUMN IF EXISTS sub_type;
