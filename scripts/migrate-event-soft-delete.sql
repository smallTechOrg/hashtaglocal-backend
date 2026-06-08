-- Migration: Soft Delete for Events
-- Adds is_active column to the events table.
-- Run on staging / production (dev uses ddl-auto=update and picks it up automatically).
--
-- WHAT THIS DOES:
--   1. Adds is_active BOOLEAN NOT NULL DEFAULT FALSE to the events table.
--      New events start inactive and only go live after admin approval.
--   2. Back-fills is_active = TRUE for events that are already APPROVED
--      so existing live events remain visible after the migration.
--
-- ROLLBACK: ALTER TABLE events DROP COLUMN is_active;

BEGIN;

ALTER TABLE events
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing approved events must stay live
UPDATE events
SET is_active = TRUE
WHERE id IN (SELECT event_id FROM event_approvals WHERE status = 'APPROVED');

COMMIT;
