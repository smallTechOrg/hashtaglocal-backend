-- Add ADMIN to the events.portal check constraint
-- Required before deploying backend version that introduces EventPortalModel.ADMIN
-- Run on every environment (staging, prod) once before deployment.

ALTER TABLE events DROP CONSTRAINT events_portal_check;

ALTER TABLE events ADD CONSTRAINT events_portal_check CHECK (
  portal = ANY (ARRAY[
    'TEAMEVEREST'::character varying,
    'IVOLUNTEERIN'::character varying,
    'MYBHARATGOVIN'::character varying,
    'TWITTER'::character varying,
    'INSTAGRAM'::character varying,
    'ADMIN'::character varying
  ])
);
