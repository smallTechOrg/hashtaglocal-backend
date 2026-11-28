-- events.portal gained the value WEB (any scraper0 site added in Ops → Events → Sources).
-- Hibernate created events_portal_check from the old enum, so inserts with WEB were rejected.
-- Rebuild it from the current EventPortalModel values. Safe to re-run.
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_portal_check;
ALTER TABLE events ADD CONSTRAINT events_portal_check
    CHECK (portal IS NULL OR portal IN ('TEAMEVEREST', 'IVOLUNTEERIN', 'MYBHARATGOVIN', 'TWITTER',
                                        'INSTAGRAM', 'ADMIN', 'WEB'));
