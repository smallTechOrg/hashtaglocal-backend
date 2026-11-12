#!/bin/bash
# One-time GCP setup for the Cloud Run deploy in .github/workflows/deploy.yml.
#
# Run it once per project, by someone with Owner (or IAM Admin + Service Usage
# Admin + Workload Identity Pool Admin) on the project. It is safe to run
# again: every step skips what exists.
#
#   PROJECT_ID=ai-agent-boilerplate0 REGION=us-central1 ./scripts/gcp_setup.sh
#
# GitHub signs in to Google without a stored key (Workload Identity
# Federation): the workflow's OIDC token is exchanged for short-lived
# credentials of the hashtaglocal-deployer account, and only for this
# repository on the branches listed below. The account has just the roles the
# deploy needs.
#
# App settings and credentials are GitHub repository variables, not Secret
# Manager secrets (see infra/cloud-run.md), so nothing about them is set up here.
set -euo pipefail

PROJECT_ID="${PROJECT_ID:?set PROJECT_ID}"
REGION="${REGION:-us-central1}"
REPO="${REPO:-smallTechOrg/hashtaglocal-backend}"
# Branches allowed to deploy. Remove the test branch once it is merged.
BRANCHES="${BRANCHES:-main staging ci/cloud-run-deploy}"
POOL="${POOL:-github}"
PROVIDER="${PROVIDER:-hashtaglocal-github}"

AR_REPOSITORY="hashtaglocal-backend"
DEPLOY_SA="hashtaglocal-deployer@${PROJECT_ID}.iam.gserviceaccount.com"
RUNTIME_SA="hashtaglocal-backend-run@${PROJECT_ID}.iam.gserviceaccount.com"
SCHEDULER_SA="hashtaglocal-scheduler@${PROJECT_ID}.iam.gserviceaccount.com"

gcloud config set project "$PROJECT_ID" >/dev/null
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"

echo "== Enabling APIs"
gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  cloudscheduler.googleapis.com \
  iamcredentials.googleapis.com \
  sts.googleapis.com

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

echo "== Service accounts"
# The runtime and scheduler accounts have no roles: the app uses its own key
# files and trusts the scheduler by email.
create_sa "$RUNTIME_SA" "Hashtag Local backend (Cloud Run runtime)"
create_sa "$SCHEDULER_SA" "Hashtag Local backend (Cloud Scheduler invoker)"
create_sa "$DEPLOY_SA" "Hashtag Local backend (GitHub Actions deployer)"

echo "== Deploy account (${DEPLOY_SA}) can build, deploy and schedule"
for role in roles/run.admin roles/artifactregistry.writer roles/cloudscheduler.admin \
            roles/compute.networkUser; do   # networkUser: Direct VPC egress to the database
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

echo "== Workload Identity provider ${POOL}/${PROVIDER} (this repository, listed branches only)"
condition="assertion.repository=='${REPO}'"
refs=""
for b in $BRANCHES; do refs="${refs:+${refs},}'refs/heads/${b}'"; done
condition="${condition} && assertion.ref in [${refs}]"
gcloud iam workload-identity-pools describe "$POOL" --location global >/dev/null 2>&1 \
  || gcloud iam workload-identity-pools create "$POOL" --location global --display-name "GitHub Actions"
if gcloud iam workload-identity-pools providers describe "$PROVIDER" \
     --workload-identity-pool "$POOL" --location global >/dev/null 2>&1; then
  gcloud iam workload-identity-pools providers update-oidc "$PROVIDER" \
    --workload-identity-pool "$POOL" --location global \
    --attribute-condition "$condition" >/dev/null
else
  gcloud iam workload-identity-pools providers create-oidc "$PROVIDER" \
    --workload-identity-pool "$POOL" --location global \
    --issuer-uri "https://token.actions.githubusercontent.com" \
    --attribute-mapping "google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
    --attribute-condition "$condition"
fi
# This repository may act as the deploy account.
gcloud iam service-accounts add-iam-policy-binding "$DEPLOY_SA" \
  --role roles/iam.workloadIdentityUser \
  --member "principalSet://iam.googleapis.com/projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/attribute.repository/${REPO}" >/dev/null

cat <<EOF

Done. Set these GitHub repository variables (not sensitive):
  GCP_PROJECT                    ${PROJECT_ID}
  GCP_WORKLOAD_IDENTITY_PROVIDER projects/${PROJECT_NUMBER}/locations/global/workloadIdentityPools/${POOL}/providers/${PROVIDER}
  GCP_DEPLOYER_SA                ${DEPLOY_SA}

Next:
  1. Add the app variables to the GitHub repository (infra/cloud-run.md).
  2. Set CLOUD_SCHEDULER_ENABLED=true when the background jobs should run
     (until then they stay paused; see infra/cloud-run.md).
  3. Merge to staging and check the service URL in the job summary.
  4. Point the load balancer at the Cloud Run services (infra/cloud-run.md).
EOF
