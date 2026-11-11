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

## Things that differ from the VM

- **In-process jobs.** The app schedules its own jobs (event scrape every 5 min,
  weather bulletin 08:00, session cleanup 03:00, feed moderation sweeper). Cloud Run
  would starve them, so the service runs with `--no-cpu-throttling`,
  `min-instances=1` and **`max-instances=1`**. With more than one instance every
  job runs once per instance. Change it only with `CLOUD_RUN_MAX_INSTANCES`
  after moving the jobs to Cloud Scheduler.
- **Key files** (`gcs-key.json`, `firebase-key.json`) are Secret Manager secrets
  mounted as files at `/secrets/gcs/` and `/secrets/firebase/`. Nothing is baked into the image.
- **Logs** go to stdout (Cloud Logging); the log file is turned off.
- **Database** is reached through Direct VPC egress on the `default` network
  (private ranges only), because Cloud SQL has a private IP.
- **Load balancer.** The service gets a `*.run.app` URL, but the apps call it through
  `/local` and `/localstaging` on the shared load balancer. Moving that traffic means
  swapping the backend behind those routes from the VM to a serverless NEG for the
  Cloud Run service (see `scripts/lb_cutover.sh` in `chatbot0`). Until that is done
  the VMs keep serving. Do not stop them before the switch.

## One-time setup

1. **Secrets** in Secret Manager (all must exist before step 2):

   | Env var in the app | Secret |
   |---|---|
   | `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `HASHTAGLOCAL_DB_URL_<ENV>` / `HASHTAGLOCAL_DB_USERNAME_<ENV>` / `HASHTAGLOCAL_DB_PASSWORD_<ENV>` (`<ENV>` = `STAGING` or `PRODUCTION`) |
   | file `/secrets/gcs/gcs-key.json` | `HASHTAGLOCAL_GCS_KEY` |
   | file `/secrets/firebase/firebase-key.json` | `HASHTAGLOCAL_FIREBASE_KEY` |
   | `GOOGLE_MAPS_API_KEY`, `GROQ_API_KEY`, `GEMINI_API_KEY` | same name |
   | `SPRING_MAIL_PASSWORD` | `SPRING_MAIL_PASSWORD` |
   | `PORTALISSUE_AUTH_USERNAME`, `PORTALISSUE_AUTH_PASSWORD` | same name |

   The JDBC URL secret is the full URL, e.g. `jdbc:postgresql://10.110.112.7:5432/hashtaglocal`.
   The password is the raw one, not URL-encoded.

2. **GCP** (as project Owner):
   ```bash
   PROJECT_ID=<project> REGION=us-central1 \
   DEPLOY_SA=github-deployer@<project>.iam.gserviceaccount.com \
   ./scripts/gcp_setup.sh
   ```
   Enables the APIs, creates the `hashtaglocal-backend` image repository and the
   `hashtaglocal-backend-run@` runtime account (can read those secrets, nothing else),
   and gives the deploy account `run.admin`, `artifactregistry.writer` and *act as*
   on the runtime account. Safe to run again.

3. **GitHub** repository settings:

   | Kind | Name | Notes |
   |---|---|---|
   | Secret | `GCP_PROJECT` | project id |
   | Secret | `GCP_SA_KEY` | JSON key of the deploy account |
   | Variable | `GOOGLE_STORAGE_BUCKET_NAME` | the media bucket |
   | Variable | `APP_SELF_BASE_URL_STAGING` / `_PRODUCTION` | this backend's public URL, sent to the scraper |
   | Variable | `EVENTS_SCRAPE_URL_STAGING` / `_PRODUCTION` | optional, app default is the staging scraper |
   | Variable | `PORTALISSUE_SCRAPE_URL_STAGING` / `_PRODUCTION` | optional, as above |
   | Variable | `APP_FORWARDED_PREFIX_STAGING` / `_PRODUCTION` | default `/localstaging` and `/local` |
   | Variable | `ACCOUNT_DELETION_ADMIN_EMAIL`, `ACCOUNT_DELETION_FROM_EMAIL`, `SPRING_MAIL_*` | optional mail settings |
   | Variable | `GCP_REGION`, `VPC_NETWORK`, `VPC_SUBNET`, `CLOUD_RUN_*` | optional, see the workflow |

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
| Deploy to Cloud Run | `run.admin` / *act as* missing, or a secret missing or unreadable by the runtime account |
| Smoke test | Revision starts but `/actuator/health` is not 200: usually the database is not reachable from Cloud Run, or a bad DB secret. The step prints the health body; the revision's logs show the stack trace |

**Run the image locally**
```bash
docker build -t hashtaglocal-backend .
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/hashtaglocal \
  -e SPRING_DATASOURCE_USERNAME=postgres -e SPRING_DATASOURCE_PASSWORD=... \
  -e LOGGING_FILE_NAME= \
  -v "$PWD/gcs-key.json:/secrets/gcs/gcs-key.json:ro" \
  -e GOOGLE_APPLICATION_CREDENTIALS=/secrets/gcs/gcs-key.json \
  hashtaglocal-backend
curl localhost:8080/actuator/health
```
