# Database migrations

Flyway runs the `V<number>__<description>.sql` files here, in order, when the app starts with
`FLYWAY_ENABLED=true` (the Cloud Run deploy sets it). Each file runs once; Flyway records it in
`flyway_schema_history`. Never edit a file after it has been deployed: add a new one.

- Start at `V2`. `V1` is the baseline: an existing database with no history is treated as having
  everything in `scripts/migrate-*.sql` already applied (`FLYWAY_BASELINE_VERSION`, default 1).
- Hibernate (`ddl-auto: update`) still creates new tables and columns. Migrations are for what it
  does not do: check constraints, indexes, data back-fills, renames, drops.
- Write them so they are safe to re-run (`IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`).
- They run while the previous revision still serves traffic, so keep them backwards compatible
  (add before you remove).
- If a migration fails the new revision does not start, the smoke test fails and no traffic moves.
