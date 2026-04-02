-- Karma Backfill Migration
-- Backfills karma_transactions and users.karma_earned for existing approved actions.
-- Safe to run multiple times (skips actions that already have karma transactions).
-- Run on staging first, then production.

BEGIN;

-- 0. Add karma columns to users table if they don't exist
ALTER TABLE users ADD COLUMN IF NOT EXISTS karma_earned INT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN IF NOT EXISTS karma_pending INT NOT NULL DEFAULT 0;

-- 1. Create karma_transactions for APPROVED REPORT actions (5 pts each)
INSERT INTO karma_transactions (user_id, points, type, status, reference_action_id, created_at)
SELECT ia.user_id, 5, 'REPORT', 'EARNED', ia.id, COALESCE(ia.created_at, NOW())
FROM issue_actions ia
WHERE ia.action = 'REPORT'
  AND ia.approval_status = 'APPROVED'
  AND NOT EXISTS (
    SELECT 1 FROM karma_transactions kt
    WHERE kt.reference_action_id = ia.id AND kt.type = 'REPORT'
  );

-- 2. Create karma_transactions for APPROVED VERIFY actions (3 pts each)
INSERT INTO karma_transactions (user_id, points, type, status, reference_action_id, created_at)
SELECT ia.user_id, 3, 'VERIFY', 'EARNED', ia.id, COALESCE(ia.created_at, NOW())
FROM issue_actions ia
WHERE ia.action = 'VERIFY'
  AND ia.approval_status = 'APPROVED'
  AND NOT EXISTS (
    SELECT 1 FROM karma_transactions kt
    WHERE kt.reference_action_id = ia.id AND kt.type = 'VERIFY'
  );

-- 3. Create karma_transactions for APPROVED RESOLVE actions (5 pts each)
INSERT INTO karma_transactions (user_id, points, type, status, reference_action_id, created_at)
SELECT ia.user_id, 5, 'RESOLVE', 'EARNED', ia.id, COALESCE(ia.created_at, NOW())
FROM issue_actions ia
WHERE ia.action = 'RESOLVE'
  AND ia.approval_status = 'APPROVED'
  AND NOT EXISTS (
    SELECT 1 FROM karma_transactions kt
    WHERE kt.reference_action_id = ia.id AND kt.type = 'RESOLVE'
  );

-- 4. Create karma_transactions for REPORTED_ISSUE_VERIFIED bonus (1 pt to reporter)
--    When someone else verified the reporter's issue and that verify was approved.
INSERT INTO karma_transactions (user_id, points, type, status, reference_action_id, created_at)
SELECT i.user_id, 1, 'REPORTED_ISSUE_VERIFIED', 'EARNED', ia.id, COALESCE(ia.created_at, NOW())
FROM issue_actions ia
JOIN issues i ON i.id = ia.issue_id
WHERE ia.action = 'VERIFY'
  AND ia.approval_status = 'APPROVED'
  AND ia.user_id != i.user_id  -- verifier is not the reporter
  AND NOT EXISTS (
    SELECT 1 FROM karma_transactions kt
    WHERE kt.reference_action_id = ia.id AND kt.type = 'REPORTED_ISSUE_VERIFIED'
  );

-- 5. Recalculate users.karma_earned from karma_transactions
UPDATE users u
SET karma_earned = COALESCE(sub.total, 0)
FROM (
  SELECT user_id, SUM(points) AS total
  FROM karma_transactions
  WHERE status = 'EARNED'
  GROUP BY user_id
) sub
WHERE u.id = sub.user_id;

-- 6. Reset karma_pending for any PENDING actions (should already match, but ensure consistency)
UPDATE users u
SET karma_pending = COALESCE(sub.total, 0)
FROM (
  SELECT user_id, SUM(points) AS total
  FROM karma_transactions
  WHERE status = 'PENDING'
  GROUP BY user_id
) sub
WHERE u.id = sub.user_id;

-- Verify counts before committing
SELECT 'backfill_summary' AS label,
       (SELECT COUNT(*) FROM karma_transactions WHERE type = 'REPORT') AS report_txns,
       (SELECT COUNT(*) FROM karma_transactions WHERE type = 'VERIFY') AS verify_txns,
       (SELECT COUNT(*) FROM karma_transactions WHERE type = 'RESOLVE') AS resolve_txns,
       (SELECT COUNT(*) FROM karma_transactions WHERE type = 'REPORTED_ISSUE_VERIFIED') AS reporter_bonus_txns,
       (SELECT COUNT(*) FROM users WHERE karma_earned > 0) AS users_with_karma;

COMMIT;
