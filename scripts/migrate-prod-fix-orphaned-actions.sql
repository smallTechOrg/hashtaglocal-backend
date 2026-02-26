-- Migration: Fix orphaned PENDING actions on REJECTED issues
-- ============================================================================
-- Problem:
--   When the mobile app creates a REJECT action (action type = 'REJECT'),
--   the issue status moves to REJECTED but any REPORT/VERIFY/RESOLVE actions
--   that were still PENDING remain in that state. This causes them to appear
--   in the admin pending queue, and the ops portal can't load their issue
--   details because REJECTED issues return 404.
--
--   Additionally, going forward the backend now cascade-rejects remaining
--   PENDING actions when a REPORT is admin-rejected. This migration fixes
--   the historical data.
--
-- Safety:
--   - Runs inside a transaction (atomic rollback on failure)
--   - Uses IF NOT EXISTS / WHERE clauses to be idempotent (safe to run twice)
--   - Dry-run SELECT provided before each UPDATE for verification
--
-- IMPORTANT: Run this BEFORE deploying the new backend JAR.
-- ============================================================================

BEGIN;

-- ── Dry-run: preview what will be updated ──────────────────────────────────
-- (Uncomment and run these SELECTs first to verify affected rows)
--
-- SELECT ia.id AS action_id, ia.issue_id, ia.action, ia.approval_status, i.status AS issue_status
-- FROM issue_actions ia
-- JOIN issues i ON ia.issue_id = i.id
-- WHERE i.status = 'REJECTED'
--   AND ia.approval_status = 'PENDING';

-- ============================================================================
-- 1. Reject all PENDING actions on REJECTED issues
--    These are orphans left behind when the issue was rejected via a REJECT
--    action type (mobile app) instead of via the admin approval workflow.
-- ============================================================================
UPDATE issue_actions
SET approval_status = 'REJECTED',
    approved_at     = NOW()
WHERE approval_status = 'PENDING'
  AND issue_id IN (
      SELECT id FROM issues WHERE status = 'REJECTED'
  );

-- ============================================================================
-- 2. (Optional) Also reject PENDING actions on issues whose REPORT action
--    was already admin-rejected but sibling actions remain PENDING.
--    This covers the case where admin rejected the REPORT via the ops portal
--    but other VERIFY/RESOLVE actions weren't cascaded.
-- ============================================================================
UPDATE issue_actions ia_outer
SET approval_status = 'REJECTED',
    approved_at     = NOW()
WHERE ia_outer.approval_status = 'PENDING'
  AND EXISTS (
      SELECT 1
      FROM issue_actions ia_report
      WHERE ia_report.issue_id = ia_outer.issue_id
        AND ia_report.action = 'REPORT'
        AND ia_report.approval_status = 'REJECTED'
  );

-- ── Verify: no PENDING actions should remain on REJECTED issues ────────────
-- SELECT ia.id, ia.issue_id, ia.action, ia.approval_status, i.status
-- FROM issue_actions ia
-- JOIN issues i ON ia.issue_id = i.id
-- WHERE i.status = 'REJECTED'
--   AND ia.approval_status = 'PENDING';
-- Expected: 0 rows

COMMIT;
