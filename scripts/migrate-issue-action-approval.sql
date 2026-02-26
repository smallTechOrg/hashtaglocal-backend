-- Migration: Issue Action Approval Workflow
-- These changes are auto-applied by Hibernate (ddl-auto: update) in development.
-- Run these manually for any environment using strict migration (staging/production).
--
-- IMPORTANT: Execute this script in a single transaction. Steps must run in order.
-- On failure, roll back the entire transaction to avoid partial migration.

BEGIN;

-- ============================================================================
-- 1. Add role column to users table
-- ============================================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';

-- ============================================================================
-- 2. Add approval tracking columns to issue_actions table
-- ============================================================================
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20);
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;

-- Backfill: existing actions have no approval status yet.
-- Mark them NOT_REQUIRED so they don't appear in the admin queue as false positives.
UPDATE issue_actions SET approval_status = 'NOT_REQUIRED' WHERE approval_status IS NULL;

-- Make approval_status non-nullable after backfill
ALTER TABLE issue_actions ALTER COLUMN approval_status SET NOT NULL;

-- ============================================================================
-- 3. Add media_id FK on issue_actions (one action per media item)
-- ============================================================================
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS media_id BIGINT REFERENCES media(id);

-- ============================================================================
-- 4. Backfill: link existing media to REPORT actions via issue_actions.media_id
--    BEFORE we drop the media.issue_id / media.user_id columns we still need
--    them to figure out which action owns which media.
--
--    Strategy:
--      a) For each media row that has issue_id, find a REPORT action on the
--         same issue by the same user that does not yet have a media_id.
--         Assign it.
--      b) If no matching action exists (edge case), create a new REPORT action
--         with approval_status = 'NOT_REQUIRED'.
--
--    Step (a): best-effort UPDATE. For each media, pick one un-linked REPORT
--    action on the same issue/user and link it.
-- ============================================================================

-- (a) Link media to existing REPORT actions that don't yet have a media_id.
--     Uses a lateral sub-query to pick exactly ONE action per media row.
UPDATE issue_actions ia
SET media_id = sub.media_id
FROM (
    SELECT DISTINCT ON (m.id)
        m.id     AS media_id,
        ia2.id   AS action_id
    FROM media m
    JOIN issue_actions ia2
      ON ia2.issue_id = m.issue_id
     AND ia2.user_id  = m.user_id
     AND ia2.action   = 'REPORT'
     AND ia2.media_id IS NULL
    WHERE m.issue_id IS NOT NULL
    ORDER BY m.id, ia2.id
) sub
WHERE ia.id = sub.action_id
  AND ia.media_id IS NULL;

-- (b) For any media still orphaned (no matching action found), create a new
--     REPORT action so the media remains visible.
INSERT INTO issue_actions (issue_id, user_id, action, approval_status, media_id, created_at)
SELECT m.issue_id,
       m.user_id,
       'REPORT',
       'NOT_REQUIRED',
       m.id,
       COALESCE(m.created_at, NOW())
FROM media m
WHERE m.issue_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM issue_actions ia WHERE ia.media_id = m.id
  );

-- ============================================================================
-- 5. Drop redundant columns from media table.
--    Issue and user are now derived transitively via issue_actions
--    (action → issue, action → user).
--    Status is no longer tracked on media — visibility is controlled by the
--    action's approval_status.
-- ============================================================================
ALTER TABLE media DROP COLUMN IF EXISTS issue_id;
ALTER TABLE media DROP COLUMN IF EXISTS user_id;
ALTER TABLE media DROP COLUMN IF EXISTS status;

-- ============================================================================
-- 6. Backfill existing issues that are OPEN to stay OPEN (no change needed).
--    New issues created after this migration start in ONHOLD.
--    No existing issues need status changes — they were created under the old
--    workflow and are already in their correct state.
-- ============================================================================

COMMIT;
