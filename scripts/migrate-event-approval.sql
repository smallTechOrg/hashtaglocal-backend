-- Migration: Event Approval Workflow
-- Run on any environment that uses strict schema management (staging / production).
-- Dev uses ddl-auto=update so Hibernate creates the table automatically on startup.
--
-- WHAT THIS DOES:
--   1. Creates the event_approvals table (new table — events table is NOT altered).
--   2. Seeds every existing event row as PENDING so admins review them before they
--      appear on the public site again.
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

-- Seed all existing events as PENDING so they land in the admin review queue.
-- ON CONFLICT is a safety net in case this script is run twice.
INSERT INTO event_approvals (event_id, status, created_at)
SELECT id, 'PENDING', NOW()
FROM events
ON CONFLICT (event_id) DO NOTHING;

COMMIT;
