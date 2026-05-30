-- Migration: Locality hashtag tree + #india root
-- Run on environments with strict schema management (staging / production).
-- Dev uses ddl-auto=update so Hibernate adds the parent_id column automatically; the NOT NULL
-- drop on geo_boundary and the #india seed/reparent below still need to be applied everywhere
-- (Hibernate update does NOT relax existing column constraints, and does not seed data).
--
-- WHAT THIS DOES:
--   1. Adds nullable parent_id (self-FK) to localities.
--   2. Relaxes geo_boundary to NULLable (root/virtual localities have no polygon).
--   3. Inserts the #india root locality (no boundary, no parent).
--   4. Points every other locality's parent_id at #india.
--
-- ROLLBACK:
--   UPDATE localities SET parent_id = NULL;
--   DELETE FROM localities WHERE hashtag = '#india';
--   ALTER TABLE localities DROP COLUMN parent_id;
--   -- (re-adding NOT NULL on geo_boundary only if every row has a boundary again)

BEGIN;

-- 1. parent_id self-referencing FK (nullable)
ALTER TABLE localities ADD COLUMN IF NOT EXISTS parent_id BIGINT;
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'fk_localities_parent'
  ) THEN
    ALTER TABLE localities
      ADD CONSTRAINT fk_localities_parent
      FOREIGN KEY (parent_id) REFERENCES localities(id);
  END IF;
END $$;

-- 2. geo_boundary nullable (root has none)
ALTER TABLE localities ALTER COLUMN geo_boundary DROP NOT NULL;

-- 3. Seed the #india root (idempotent on the unique hashtag).
INSERT INTO localities (hashtag, name, geo_boundary, parent_id)
VALUES ('#india', 'India', NULL, NULL)
ON CONFLICT (hashtag) DO NOTHING;

-- 4. Reparent every existing locality under #india (except #india itself, and any already parented).
UPDATE localities
SET parent_id = (SELECT id FROM localities WHERE hashtag = '#india')
WHERE hashtag <> '#india'
  AND parent_id IS NULL;

COMMIT;
