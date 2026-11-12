# Cloud Run Deployment - Hashtag Local Backend

Replaces the manual VM deploy in [deployment.md](deployment.md). Same design as
the `chatbot0` repo: GitHub Actions builds a container, deploys a Cloud Run
revision with no traffic, smoke-tests it, then promotes it.

| Branch | Cloud Run service | `APP_ENV` | Database secrets |
|---|---|---|---|
| `staging` | `hashtaglocal-backend-staging` | `staging` | `HASHTAGLOCAL_DB_*_STAGING` |
| `main` | `hashtaglocal-backend` | `production` | `HASHTAGLOCAL_DB_*_PRODUCTION` |

## Flow (`.github/workflows/deploy.yml`)

1. **test** - `./gradlew test` against a throwaway PostGIS database. A red test stops the deploy.
2. **build** - `docker build` from the root `Dockerfile`, pushed to Artifact Registry as
   `<region>-docker.pkg.dev/<project>/hashtaglocal-backend/hashtaglocal-backend:<sha>`.
3. **deploy** - new revision with **no traffic**, tagged `candidate` (the first deploy of a service goes straight to 100%).
4. **smoke test** - `GET /actuator/health` on the candidate URL must return 200 (app up *and* database reachable). Otherwise users stay on the old revision.
5. **promote** - all traffic moves to the new revision.

Re-run by hand: Actions → *Deploy to Cloud Run* → *Run workflow*, pick the branch.

## Migrations

Schema changes run **automatically at startup**: Flyway (`FLYWAY_ENABLED=true` on Cloud Run) applies the
new files in `src/main/resources/db/migration` (`V2__...sql`, `V3__...sql`, ...) before the app serves
traffic. If one fails, the revision does not start, the smoke test fails and users stay on the old revision.
No more running `scripts/migrate-*.sql` by hand for new changes. See the README in that folder for the rules.

**Before the first deploy of each environment**, check that every existing `scripts/migrate-*.sql` is already
applied to that database. The first start baselines the database at version 1 (`FLYWAY_BASELINE_VERSION`),
which marks all of them as done and never runs them. Hibernate (`ddl-auto: update`) still creates new
tables and columns; migrations are for constraints, indexes, back-fills, renames and drops.
Local dev and tests leave Flyway off.

## Things that differ from the VM

- **In-process jobs.** The app schedules its own jobs (event scrape every 5 min,
  weather bulletin 08:00, session cleanup 03:00, feed moderation sweeper). Cloud Run
  would starve them, so the service runs with `--no-cpu-throttling`,
  `min-instances=1` and **`max-instances=1`**. With more than one instance every
  job runs once per instance. Change it only with `CLOUD_RUN_MAX_INSTANCES`
  after moving the jobs to Cloud Scheduler.
- **Configuration and credentials** are all GitHub repository variables (no Secret Manager).
  The two key files are stored as JSON text in `GCS_KEY_JSON` and `FIREBASE_KEY_JSON`; the
  container's `docker-entrypoint.sh` writes them to `/tmp/keys/` at start. Nothing is baked into the image.
- **Logs** go to stdout (Cloud Logging); the log file is turned off.
- **Database** is reached through Direct VPC egress on the `default` network
  (private ranges only), because Cloud SQL has a private IP.
- **Load balancer.** The service gets a `*.run.app` URL, but the apps call it through
  `/local` and `/localstaging` on the shared load balancer. Moving that traffic means
  swapping the backend behind those routes from the VM to a serverless NEG for the
  Cloud Run service (see `scripts/lb_cutover.sh` in `chatbot0`). Until that is done
  the VMs keep serving. Do not stop them before the switch.

## One-time setup

1. **GitHub repository variables** (Settings → Secrets and variables → Actions → *Variables*).
   This is a deliberate choice: every app setting and credential is a variable, none is in Secret Manager.
   Variables are plaintext to anyone with access to the repository and to the Cloud Run service, so keep
   both restricted. The workflow masks the sensitive ones in its logs and never prints values.

   Required, per environment (`NAME_STAGING` for `staging`, `NAME_PRODUCTION` for `main`; a plain `NAME`
   is used by both). The deploy fails and names the missing ones:

   | Variable | Value |
   |---|---|
   | `SPRING_DATASOURCE_URL_<ENV>` | full JDBC URL, e.g. `jdbc:postgresql://10.110.112.7:5432/hashtaglocal` |
   | `SPRING_DATASOURCE_USERNAME_<ENV>` | database user |
   | `SPRING_DATASOURCE_PASSWORD_<ENV>` | the raw password, not URL-encoded |
   | `GCS_KEY_JSON` | the whole contents of `gcs-key.json` |

   Optional credentials (the app default is empty or off): `FIREBASE_KEY_JSON` (the whole `firebase-key.json`;
   without it push notifications are off), `GOOGLE_MAPS_API_KEY`, `GROQ_API_KEY`, `GEMINI_API_KEY`,
   `SPRING_MAIL_PASSWORD`, `GOOGLE_OAUTH_CLIENT_SECRET`, `PORTALISSUE_AUTH_USERNAME`, `PORTALISSUE_AUTH_PASSWORD`.
   A missing `GEMINI_API_KEY` means feed posts go to human review instead of auto-publishing.

   Everything else is listed under *GitHub settings* below.

2. **GCP** (as project Owner):
   ```bash
   PROJECT_ID=<project> REGION=us-central1 \
   DEPLOY_SA=github-deployer@<project>.iam.gserviceaccount.com \
   ./scripts/gcp_setup.sh
   ```
   Enables the APIs, creates the `hashtaglocal-backend` image repository and the
   `hashtaglocal-backend-run@` runtime account (no roles), and gives the deploy account
   `run.admin`, `artifactregistry.writer` and *act as* on the runtime account. Safe to run again.

3. **GitHub** repository settings:

   | Kind | Name | Notes |
   |---|---|---|
   | Secret | `GCP_PROJECT` | project id |
   | Secret | `GCP_SA_KEY` | JSON key of the deploy account |
   | Variable | the required and optional credentials above | see step 1 |
   | Variable | any setting in the list below | plain app setting |
   | Variable | `EXTRA_ENV` | anything else, one `KEY=VALUE` per line (`#` for comments) |
   | Variable | `GCP_REGION`, `VPC_NETWORK`, `VPC_SUBNET`, `CLOUD_RUN_MIN_INSTANCES`, `CLOUD_RUN_MAX_INSTANCES`, `CLOUD_RUN_CONCURRENCY` | deployment settings, see the workflow |

   **App settings read from variables.** Each name is the same as the environment variable in
   `application.yaml`. For each, `NAME_STAGING` / `NAME_PRODUCTION` wins over `NAME`; a name with
   no variable keeps the app default. `APP_FORWARDED_PREFIX` defaults to `/local` (production) and
   `/localstaging` (staging).

   `APP_SELF_BASE_URL`, `APP_FORWARDED_PREFIX`, `EVENTS_SCRAPE_URL`, `EVENTS_SCRAPE_CRON`,
   `PORTALISSUE_ENABLED`, `PORTALISSUE_CRON`, `PORTALISSUE_SCRAPE_URL`, `PORTALISSUE_REPORT_SCRAPE_URL`
   (defaults to `PORTALISSUE_SCRAPE_URL`), `PORTALISSUE_REPORT_CONNECT_TIMEOUT_SECONDS`,
   `PORTALISSUE_REPORT_READ_TIMEOUT_SECONDS`, `GOOGLE_STORAGE_BUCKET_NAME`, `GOOGLE_OAUTH_CLIENT_ID`,
   `GOOGLE_OAUTH_REDIRECT_URI`, `APPLE_BUNDLE_ID`, `GEONAMES_USERNAME`,
   `AUTH_ACCESS_TOKEN_EXPIRY_SECONDS`, `AUTH_REFRESH_TOKEN_EXPIRY_SECONDS`, `AUTH_SESSION_CLEANUP_CRON`,
   `ACCOUNT_DELETION_ADMIN_EMAIL`, `ACCOUNT_DELETION_FROM_EMAIL`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`,
   `SPRING_MAIL_USERNAME`, `SPRING_MAIL_SMTP_AUTH`, `SPRING_MAIL_SMTP_STARTTLS_ENABLE`,
   `SPRING_MAIL_SMTP_FROM`, `SPRING_MAIL_SMTP_LOCALHOST`, `FEED_MODERATION_ENABLED`,
   `FEED_MODERATION_MODEL`, `FEED_MODERATION_ENDPOINT`, `FEED_MODERATION_SWEEPER_DELAY_MS`,
   `FEED_SCRAPE_TIMEOUT_MS`, `FEED_SCRAPE_MAX_BODY_BYTES`, `FEED_SCRAPE_MAX_IMAGE_BYTES`,
   `FEED_AUTO_ISSUE_REF_ENABLED`, `FEED_NOTIFICATION_ENABLED`, `BULLETIN_ENABLED`,
   `BULLETIN_NOTIFICATION_ENABLED`, `BULLETIN_WEATHER_CRON`, `BULLETIN_WEATHER_PROVIDER`,
   `BULLETIN_OPEN_METEO_URL`, `GROQ_API_URL`, `GROQ_MODEL`, `GROQ_REQUESTS_PER_MINUTE`,
   `FLYWAY_BASELINE_VERSION`.

   Note `AUTH_ACCESS_TOKEN_EXPIRY_SECONDS` / `AUTH_REFRESH_TOKEN_EXPIRY_SECONDS` default to 10 and 30
   seconds in `application.yaml`. Set real values here or every user is logged out almost at once.

   To gate production behind an approval, move the two secrets into a
   `deploy-production` environment with required reviewers and add
   `environment:` to the deploy job.

## Operations

```bash
# Health
curl https://<service-url>/actuator/health

# Logs
gcloud run services logs tail hashtaglocal-backend --region us-central1
gcloud run services logs read hashtaglocal-backend-staging --region us-central1 --limit 200

# Rollback
gcloud run revisions list --service hashtaglocal-backend --region us-central1
gcloud run services update-traffic hashtaglocal-backend --region us-central1 \
  --to-revisions <revision-name>=100
```

**Deploy fails**

| Step | Usual cause |
|---|---|
| test | A real test failure, run `./gradlew test` locally |
| Build and push image | Artifact Registry repo missing (run `gcp_setup.sh`) |
| Write service environment | Names the required repository variables that are missing |
| Deploy to Cloud Run | `run.admin` / *act as* missing, or the runtime service account is missing |
| Smoke test | Revision starts but `/actuator/health` is not 200: usually the database is not reachable from Cloud Run, a bad DB secret, or a failed migration. The step prints the health body; the revision's logs show the stack trace |

**Run the image locally**
```bash
docker build -t hashtaglocal-backend .
docker run --rm -p 8080:8080   -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/hashtaglocal   -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=...   -e LOGGING_FILE_NAME=   -e GOOGLE_APPLICATION_CREDENTIALS=/tmp/keys/gcs-key.json   -e GCS_KEY_JSON="$(cat gcs-key.json)"   hashtaglocal-backend
curl localhost:8080/actuator/health
```
