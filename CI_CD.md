# CI/CD Pipeline

## Overview

Automated build → test → deploy pipeline via GitHub Actions. The JAR is built in CI (not on the VM), then SCP'd and the systemd service is restarted.

## Architecture

```
Push to staging ──▶ build+test ──▶ SCP JAR to staging-instance ──▶ systemctl restart (port 8080)
Push to main   ──▶ build+test ──▶ SCP JAR to hashtaglocal-backend-prod ──▶ systemctl restart (port 8080)
```

## Pipeline Stages

### 1. Build and Test (`build-and-test` job)

Runs on every push to `main` or `staging`.

- **Service container**: PostGIS 16-3.4 on `localhost:5432` (DB: `hashtaglocaltest`, user: `postgres`, password: `Postgres`)
- **Java**: Temurin 25 (matches `build.gradle.kts` toolchain)
- **Gradle**: Managed via `gradle/actions/setup-gradle@v4` (caches dependencies)
- **Build command**: `./gradlew clean build`
  - Runs `spotlessCheck` (Google Java Format linting)
  - Runs JUnit tests with `SPRING_PROFILES_ACTIVE=test` (uses `application-test.yaml` → `create-drop` DDL)
  - Produces `build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar`
- **Artifact**: JAR uploaded for deploy jobs (retained 1 day)

### 2. Deploy Staging (`deploy-staging` job)

Triggers only on pushes to `staging`. Requires `build-and-test` to pass.

1. Downloads the JAR artifact
2. Authenticates to GCP
3. `gcloud compute scp` the JAR to `/tmp/hashtaglocal-backend.jar` on the staging VM
4. SSHs in: `sudo mv` JAR to `/opt/hashtaglocal-backend/` → `sudo systemctl restart hashtaglocal-backend`
5. Waits 15s, then verifies `GET /actuator/health` returns 200

### 3. Deploy Production (`deploy-production` job)

Triggers only on pushes to `main`. Same flow as staging targeting the production VM.

## GitHub Secrets Required

| Secret | Environment | Value |
|--------|-------------|-------|
| `GCP_SA_KEY` | (repo-level) | Service account JSON key |
| `GCP_PROJECT` | (repo-level) | `ai-agent-boilerplate0` |
| `GCP_VM_INSTANCE_NAME_STAGING` | staging | `staging-instance` |
| `GCP_ZONE_STAGING` | staging | `us-central1-f` |
| `GCP_VM_INSTANCE_NAME_PROD` | production | `hashtaglocal-backend-prod` |
| `GCP_ZONE_PROD` | production | `us-central1-f` |

## GitHub Environments

Create two environments in **repo Settings → Environments**:

- **staging** — no protection rules needed
- **production** — recommended: add required reviewers

## VM Prerequisites

Each VM must have:

1. Java 25 installed (`openjdk-25-jdk`)
2. Directory `/opt/hashtaglocal-backend/` with:
   - `.env` file containing `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, `GOOGLE_APPLICATION_CREDENTIALS`
   - `gcs-key.json` for GCS access
3. Systemd service `hashtaglocal-backend` configured (see `infra/deployment.md` Step 8)
4. The systemd service `EnvironmentFile` pointing to `/opt/hashtaglocal-backend/.env`

### Systemd Service (already on VMs)

```ini
[Unit]
Description=Hashtag Local Backend
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/hashtaglocal-backend
EnvironmentFile=/opt/hashtaglocal-backend/.env
ExecStart=/usr/bin/java -jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

## Rollback

```bash
# Re-deploy a previous JAR: build locally from the good commit
git checkout GOOD_COMMIT_SHA
./gradlew clean build

# SCP and restart
gcloud compute scp build/libs/hashtaglocal-backend-0.0.1-SNAPSHOT.jar \
  VM_NAME:/tmp/hashtaglocal-backend.jar --zone=ZONE

gcloud compute ssh VM_NAME --zone=ZONE \
  --command="sudo mv /tmp/hashtaglocal-backend.jar /opt/hashtaglocal-backend/hashtaglocal-backend.jar && sudo systemctl restart hashtaglocal-backend"
```

Or revert the commit and push to trigger a new pipeline run.
