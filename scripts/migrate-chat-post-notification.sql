-- Migration: lets notification_log record chat-post and issue-update pushes.
-- Run after migrate-bulletin-notification.sql and BEFORE deploying this feature (staging first).
-- Hibernate's ddl-auto won't make these changes, so they must be run by hand.

BEGIN;

-- Step 1: allow the new sub_type value.
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_sub_type_check;
ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_sub_type_check
  CHECK (sub_type IS NULL OR sub_type IN
         ('CHAT_BULLETIN', 'CHAT_POST', 'CHAT_ISSUE', 'CHAT_ISSUE_RESOLVED',
          'BROADCAST_LOCALITY'));

-- Step 2: allow the new source_ref_type value.
ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_source_ref_type_check;
ALTER TABLE notification_log
  ADD CONSTRAINT notification_log_source_ref_type_check
  CHECK (source_ref_type IS NULL OR source_ref_type IN ('ISSUE', 'BULLETIN', 'FEED_POST'));

-- Step 3: duplicate-send guards. The listener inserts its claim row before any FCM call; these
--         indexes make a parallel/re-run insert fail.
--   One chat push per feed post:
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_log_feed_post
  ON notification_log (source_ref_id)
  WHERE type = 'AUTO_CHAT' AND sub_type = 'CHAT_POST' AND source_ref_type = 'FEED_POST';
--   One "opened" and one "resolved" push per issue (a regenerated issue post never re-notifies):
CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_log_issue_update
  ON notification_log (source_ref_id, sub_type)
  WHERE type = 'AUTO_CHAT' AND sub_type IN ('CHAT_ISSUE', 'CHAT_ISSUE_RESOLVED')
    AND source_ref_type = 'ISSUE';

COMMIT;

-- Rollback (run inside BEGIN; ... COMMIT;). First delete any FEED_POST or CHAT_ISSUE_RESOLVED
-- rows, otherwise the old constraints can't be added back:
-- DROP INDEX IF EXISTS uq_notification_log_issue_update;
-- DROP INDEX IF EXISTS uq_notification_log_feed_post;
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_source_ref_type_check;
-- ALTER TABLE notification_log
--   ADD CONSTRAINT notification_log_source_ref_type_check
--   CHECK (source_ref_type IS NULL OR source_ref_type IN ('ISSUE', 'BULLETIN'));
-- ALTER TABLE notification_log DROP CONSTRAINT IF EXISTS notification_log_sub_type_check;
-- ALTER TABLE notification_log
--   ADD CONSTRAINT notification_log_sub_type_check
--   CHECK (sub_type IS NULL OR sub_type IN
--          ('CHAT_BULLETIN', 'CHAT_POST', 'CHAT_ISSUE', 'BROADCAST_LOCALITY'));
