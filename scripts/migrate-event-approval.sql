-- Migration: Event Approval Workflow
-- Run on any environment that uses strict schema management (staging / production).
-- Dev uses ddl-auto=update so Hibernate creates the table automatically on startup.
--
-- WHAT THIS DOES:
--   1. Creates the event_approvals table (new table — events table is NOT altered).
--   2. Auto-approves all existing events so they remain visible on the public site.
--      (New imports are auto-approved in code; this keeps existing events consistent.)
--
-- ROLLBACK: DROP TABLE event_approvals;  (zero risk to existing event data)

BEGIN;

CREATE TABLE IF NOT EXISTS event_approvals (
  event_id    BIGINT      PRIMARY KEY REFERENCES events(id) ON DELETE CASCADE,
  status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  display_name VARCHAR(500),
  reviewed_at TIMESTAMP,
  created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- Seed all existing events as APPROVED so they stay live on the public site.
-- ON CONFLICT is a safety net in case this script is run twice.
INSERT INTO event_approvals (event_id, status, reviewed_at, created_at)
SELECT id, 'APPROVED', NOW(), NOW()
FROM events
ON CONFLICT (event_id) DO NOTHING;
COMMIT;
