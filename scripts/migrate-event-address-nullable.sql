-- Migration: allow events.address to be NULL
-- Run on any environment with strict schema management (staging / production).
-- Dev uses ddl-auto=update, but Hibernate's "update" mode never DROPS an existing NOT NULL
-- constraint, so this must be applied by hand even on dev DBs created before this change.
--
-- WHY:
--   Scraped events sometimes arrive without an address. Import previously failed the whole scrape
--   batch on the events.address NOT NULL constraint — one address-less row rolled back every event
--   in that cycle, so nothing reached the ops portal. address is now optional in the entity: the
--   event is kept for admin review and geocoded later once an admin supplies an address via the
--   ops portal edit endpoint (PUT /admin/event/{id}).
--
-- ROLLBACK (only safe once no rows have address IS NULL):
--   ALTER TABLE events ALTER COLUMN address SET NOT NULL;

ALTER TABLE events ALTER COLUMN address DROP NOT NULL;
