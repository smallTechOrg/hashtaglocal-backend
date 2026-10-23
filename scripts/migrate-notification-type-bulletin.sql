-- Migration: allow notification_log.type = 'BULLETIN' (daily bulletin push).
-- Run BEFORE deploying the new JAR.
--
-- Hibernate created notification_log with a CHECK constraint listing the NotificationType enum
-- values at the time. ddl-auto=update never widens it, so inserting a BULLETIN row would fail.

ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_type_check;

ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_type_check
  CHECK (type IN ('ISSUE_DETAIL', 'BROADCAST', 'CHAT', 'BULLETIN'));

-- Dedup lookup used by the bulletin notification cron (one push per bulletin).
CREATE INDEX IF NOT EXISTS idx_notification_log_source_ref
    ON notification_log (source_ref_type, source_ref_id);

-- Rollback (apply before JAR rollback if needed):
-- DELETE FROM notification_log WHERE type = 'BULLETIN';
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_type_check;
-- ALTER TABLE notification_log
--   ADD CONSTRAINT notification_log_type_check
--   CHECK (type IN ('ISSUE_DETAIL', 'BROADCAST', 'CHAT'));
-- DROP INDEX IF EXISTS idx_notification_log_source_ref;
