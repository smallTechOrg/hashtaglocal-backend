-- Event sources: the scraper0 site actions that event ingestion reads, editable from the Ops
-- Dashboard (Event sources). Seeded with the three portals that were hardcoded in
-- EventPortalModel, with the same filter texts.
-- Also: events.external_id (the scraper0 item id, unique where set) and events.source_site_id.
-- Safe to re-run; Hibernate (ddl-auto: update) creates the same table and columns where Flyway is
-- off. Flyway runs before Hibernate, so the table and columns are created here too.

CREATE TABLE IF NOT EXISTS event_sources (
    id                  BIGSERIAL    PRIMARY KEY,
    name                VARCHAR(200) NOT NULL,
    site_id             VARCHAR(255) NOT NULL,
    action              VARCHAR(100) NOT NULL DEFAULT 'fetch_events',
    event_filter        TEXT,
    category_filter     TEXT,
    include_online      BOOLEAN      NOT NULL DEFAULT false,
    freshness_hours     INTEGER      NOT NULL DEFAULT 48,
    enabled             BOOLEAN      NOT NULL DEFAULT true,
    last_run_at         TIMESTAMP(6),
    last_status         VARCHAR(20),
    last_error          TEXT,
    last_item_count     INTEGER,
    last_imported_count INTEGER,
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- One source per (site, action). Same name as the entity's @UniqueConstraint.
ALTER TABLE event_sources DROP CONSTRAINT IF EXISTS event_sources_site_action_key;
ALTER TABLE event_sources ADD CONSTRAINT event_sources_site_action_key UNIQUE (site_id, action);

ALTER TABLE event_sources DROP CONSTRAINT IF EXISTS event_sources_last_status_check;
ALTER TABLE event_sources ADD CONSTRAINT event_sources_last_status_check
    CHECK (last_status IS NULL OR last_status IN ('ok', 'partial', 'error'));

INSERT INTO event_sources (name, site_id, action, event_filter, category_filter)
VALUES
    ('Team Everest', 'teameverest-ngo', 'fetch_events',
     'This should be an event where anybody can participate and do something related to cleaning, plantation',
     NULL),
    ('MyBharat', 'mybharat-gov-in', 'fetch_events',
     'civic volunteering, cleanliness drive, tree plantation',
     'civic engagement'),
    ('iVolunteer', 'ivolunteer-in', 'fetch_events',
     'Any in-person volunteering activity: cleanliness drive, cleanup, tree plantation,  mural painting, food distribution, community engagement, elderly care, animal welfare, sanitation. Exclude: remote-only roles like content writing, video editing, social media management, UI/UX design, app development, proposal writing, digital marketing, and pure internships with no direct community interaction.',
     'environment, education, civic engagement, community service, child welfare, health, hunger, disability, animal welfare, elderly care, sanitation')
ON CONFLICT (site_id, action) DO NOTHING;

-- events: the scraper0 item id (dedupe key for imports) and the site it came from.
-- Guarded: on an empty database Hibernate creates events (and these columns) after Flyway.
DO $$
BEGIN
    IF to_regclass('events') IS NOT NULL THEN
        ALTER TABLE events ADD COLUMN IF NOT EXISTS external_id VARCHAR(64);
        ALTER TABLE events ADD COLUMN IF NOT EXISTS source_site_id VARCHAR(255);
        CREATE UNIQUE INDEX IF NOT EXISTS events_external_id_key
            ON events (external_id) WHERE external_id IS NOT NULL;
    END IF;
END $$;
