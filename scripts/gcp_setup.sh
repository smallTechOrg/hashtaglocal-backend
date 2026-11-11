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
# secret. It gets the roles the workflow needs; nothing else is changed on it.
#
# The secrets themselves must already exist in Secret Manager (see
# infra/cloud-run.md). The script only lets the runtime account read them.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?set PROJECT_ID}"
REGION="${REGION:-us-central1}"
DEPLOY_SA="${DEPLOY_SA:?set DEPLOY_SA to the GitHub deploy service account email}"
AR_REPOSITORY="hashtaglocal-backend"
RUNTIME_SA="hashtaglocal-backend-run@${PROJECT_ID}.iam.gserviceaccount.com"
SECRETS=(
  HASHTAGLOCAL_DB_URL_STAGING HASHTAGLOCAL_DB_USERNAME_STAGING HASHTAGLOCAL_DB_PASSWORD_STAGING
  HASHTAGLOCAL_DB_URL_PRODUCTION HASHTAGLOCAL_DB_USERNAME_PRODUCTION HASHTAGLOCAL_DB_PASSWORD_PRODUCTION
  HASHTAGLOCAL_GCS_KEY HASHTAGLOCAL_FIREBASE_KEY
  GOOGLE_MAPS_API_KEY GROQ_API_KEY GEMINI_API_KEY SPRING_MAIL_PASSWORD
  GOOGLE_OAUTH_CLIENT_SECRET
  PORTALISSUE_AUTH_USERNAME PORTALISSUE_AUTH_PASSWORD
)

gcloud config set project "$PROJECT_ID" >/dev/null

echo "== Checking the secrets exist"
missing=()
for secret in "${SECRETS[@]}"; do
  gcloud secrets describe "$secret" >/dev/null 2>&1 || missing+=("$secret")
done
if [ "${#missing[@]}" -gt 0 ]; then
  echo "Missing in Secret Manager: ${missing[*]}" >&2
  echo "Create each with: printf '%s' '<value>' | gcloud secrets create NAME --data-file=-" >&2
  echo "(key files: gcloud secrets create NAME --data-file=path/to/key.json)" >&2
  exit 1
fi

echo "== Enabling APIs"
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
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

echo "== Runtime service account"
gcloud iam service-accounts describe "$RUNTIME_SA" >/dev/null 2>&1 \
  || gcloud iam service-accounts create "${RUNTIME_SA%%@*}" \
       --display-name "Hashtag Local backend (Cloud Run runtime)"

echo "== Runtime account reads the app secrets"
for secret in "${SECRETS[@]}"; do
  gcloud secrets add-iam-policy-binding "$secret" \
    --member "serviceAccount:${RUNTIME_SA}" \
    --role roles/secretmanager.secretAccessor >/dev/null
done

echo "== Deploy account (${DEPLOY_SA}) can build and deploy"
for role in roles/run.admin roles/artifactregistry.writer; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" \
    --member "serviceAccount:${DEPLOY_SA}" --role "$role" --condition None >/dev/null
done
# Deploying a service as RUNTIME_SA needs "act as" on that account.
gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member "serviceAccount:${DEPLOY_SA}" \
  --role roles/iam.serviceAccountUser >/dev/null

cat <<EOF

Done. Next:
  1. Add the GCP_PROJECT and GCP_SA_KEY secrets to the GitHub repository.
  2. Merge to staging and check the service URL in the job summary.
  3. Point the load balancer at the Cloud Run services (infra/cloud-run.md).
EOF
