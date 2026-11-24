#!/bin/sh
# Writes the service-account key files from the environment, then starts the app.
# On Cloud Run the keys arrive as GCS_KEY_JSON / FIREBASE_KEY_JSON (GitHub
# repository variables, set by .github/workflows/deploy.yml); the app reads
# them from the paths in GOOGLE_APPLICATION_CREDENTIALS / FIREBASE_CREDENTIALS_PATH.
# Without them (local runs that mount their own files) nothing is written.
set -eu

KEY_DIR=/tmp/keys
mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

if [ -n "${GCS_KEY_JSON:-}" ]; then
  printf '%s' "$GCS_KEY_JSON" > "$KEY_DIR/gcs-key.json"
  chmod 600 "$KEY_DIR/gcs-key.json"
fi
if [ -n "${FIREBASE_KEY_JSON:-}" ]; then
  printf '%s' "$FIREBASE_KEY_JSON" > "$KEY_DIR/firebase-key.json"
  chmod 600 "$KEY_DIR/firebase-key.json"
fi

# The raw key text is no longer needed in the app's environment.
unset GCS_KEY_JSON FIREBASE_KEY_JSON

exec java -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
