#!/bin/bash
# One-time GCP setup for the Cloud Run deploy in .github/workflows/deploy.yml.
#
# Run it once per project, by someone with Owner (or IAM Admin + Service Usage
# Admin) on the project. It is safe to run again: every step skips what exists.
#
#   PROJECT_ID=ai-agent-boilerplate0 REGION=us-central1 \
#   DEPLOY_SA=github-deployer@ai-agent-boilerplate0.iam.gserviceaccount.com \
#   ./scripts/gcp_setup.sh
#
# DEPLOY_SA is the service account whose key is in the GCP_SA_KEY GitHub
# variable. It gets the roles the workflow needs; nothing else is changed on it.
#
# App settings and credentials are GitHub repository variables, not Secret
# Manager secrets or GitHub secrets (see infra/cloud-run.md), so nothing about them is set up here.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?set PROJECT_ID}"
REGION="${REGION:-us-central1}"
DEPLOY_SA="${DEPLOY_SA:?set DEPLOY_SA to the GitHub deploy service account email}"
AR_REPOSITORY="hashtaglocal-backend"
RUNTIME_SA="hashtaglocal-backend-run@${PROJECT_ID}.iam.gserviceaccount.com"
SCHEDULER_SA="hashtaglocal-scheduler@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID" >/dev/null

echo "== Enabling APIs"
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudscheduler.googleapis.com \
  iamcredentials.googleapis.com

echo "== Artifact Registry repository ${AR_REPOSITORY} (${REGION})"
gcloud artifacts repositories describe "$AR_REPOSITORY" --location "$REGION" >/dev/null 2>&1 \
  || gcloud artifacts repositories create "$AR_REPOSITORY" \
       --location "$REGION" --repository-format docker \
       --description "Hashtag Local backend images"

# Keep the image store small: delete images older than 30 days, but always
# keep the 20 newest so a rollback target is there.
POLICY_FILE="$(mktemp)"
trap 'rm -f "$POLICY_FILE"' EXIT
cat > "$POLICY_FILE" <<'EOF'
[
  {"name": "keep-recent", "action": {"type": "Keep"}, "mostRecentVersions": {"keepCount": 20}},
  {"name": "delete-old", "action": {"type": "Delete"}, "condition": {"olderThan": "30d"}}
]
EOF
gcloud artifacts repositories set-cleanup-policies "$AR_REPOSITORY" \
  --location "$REGION" --policy "$POLICY_FILE" >/dev/null

create_sa() {
  local email="$1" display="$2"
  gcloud iam service-accounts describe "$email" >/dev/null 2>&1 \
    || gcloud iam service-accounts create "${email%%@*}" --display-name "$display"
}

echo "== Service accounts (no roles: the app uses its own key files and trusts the scheduler by email)"
create_sa "$RUNTIME_SA" "Hashtag Local backend (Cloud Run runtime)"
create_sa "$SCHEDULER_SA" "Hashtag Local backend (Cloud Scheduler invoker)"

echo "== Deploy account (${DEPLOY_SA}) can build and deploy"
for role in roles/run.admin roles/artifactregistry.writer roles/cloudscheduler.admin; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${DEPLOY_SA}" --role "$role" --condition None >/dev/null
done
# Deploying a service as RUNTIME_SA, and a scheduler job that signs tokens as
# SCHEDULER_SA, both need "act as" on that account.
for sa in "$RUNTIME_SA" "$SCHEDULER_SA"; do
  gcloud iam service-accounts add-iam-policy-binding "$sa" \
    --member "serviceAccount:${DEPLOY_SA}" \
    --role roles/iam.serviceAccountUser >/dev/null
done

cat <<EOF

Done. Next:
  1. Add the GCP_PROJECT and GCP_SA_KEY variables and the app variables to the
     GitHub repository (infra/cloud-run.md).
  2. Set the variable CLOUD_SCHEDULER_ENABLED=true when the background jobs
     should run (until then they stay paused; see infra/cloud-run.md).
  3. Merge to staging and check the service URL in the job summary.
  4. Point the load balancer at the Cloud Run services (infra/cloud-run.md).
EOF
