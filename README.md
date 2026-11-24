# hashtag-local
the backend for #local, a location based community platform

## Prerequisites

### Install Java
Install Java using the IntelliJ console.
Refer `.tool-versions` for the exact java version.

### Install Postgres (with PostGIS)
We are currently using version 18 of postgres.
And version 3.6 of PostGIS.

### Build using Gradle
Once you have downloaded the JDK onto IntelliJ, you can build using gradle.
You can do this via the GUI or via this command:
```declarative
./gradlew build
```

### Connect with GCS bucket
Once you have downloaded the SA key in your local, you can set your credential using this command:
```declarative
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\GCS-key.json"
```

Or simply add "gcs-key.json" to the repo.

### Connect with Firebase for notifications
Once you have downloaded the Firebase private key in your local, you can set your credential using this command:
```declarative
$env:GOOGLE_APPLICATION_CREDENTIALS="C:\firebase-key.json"
```

Or simply add "firebase-key.json" to the repo.

If Gradle is running, stop it using:
```declarative
./gradlew --stop
```
After setting the credentials, run your Gradle build command as usual.

## Running the Application
You can run the app via GUI or via this command
```declarative
./gradlew bootRun --args='--spring.profiles.active=local'
```
Verify if the app is up on http://localhost:8080/actuator/health
You can also check the OpenAPI docs on http://localhost:8080/v1/swagger-ui/index.html

## Configuring Local
You can override the `src/main/resources/application.yaml` by creating a `src/main/resources/application-local.yaml` and override the values you need starting with DB.
You will have to do the same for running the tests: `src/test/resources/application-test.yaml`.

Also, make sure to create the necessary DBs as well on your local:
"hashtaglocal" and "hashtaglocaltest".

## Linting
We are using [spotless](https://github.com/diffplug/spotless) to do linting checks.
The linting checks will automatically run at build. 
But if you want to check the linting without building, use this command:
```declarative
./gradlew check
```
To fix any linting issues, run this:
```declarative
./gradlew :spotlessApply
```

## Testing
To run all the test cases, use this command:
```declarative
./gradlew test
```
## Quiz AI provider (Ops Dashboard)

Quiz generation uses the AI provider an admin saves on the Ops Dashboard's **AI Provider** page
(`/ops/ai-provider`, API `/admin/ai-provider`, ADMIN only). With nothing saved, it uses the
`QUIZ_*` fallback chain from `.env`, as before.

- **Env var:** `AI_CONFIG_ENCRYPTION_KEY`, base64 of 32 random bytes (`openssl rand -base64 32`).
  The saved API key is stored only as AES-256-GCM ciphertext under this key; no endpoint ever
  returns the key (only `has_key` and its last 4 characters). Without the env var a key cannot be
  saved. Optional: `AI_CONFIG_ALLOW_PRIVATE_ENDPOINTS` (default `false`), `AI_CONFIG_CACHE_TTL`
  (default `30s`).
- **Migration:** `src/main/resources/db/migration/V2__ai_provider_config.sql` creates
  `ai_provider_config` (one row) and `ai_provider_config_audit`. Flyway runs it on start when
  `FLYWAY_ENABLED=true` (Cloud Run); locally, Hibernate creates the tables, or run the file with
  `psql -d hashtaglocal -f src/main/resources/db/migration/V2__ai_provider_config.sql`.
- **Switching provider:** pick the provider (Claude/Anthropic, OpenAI, Groq, Gemini, or
  OpenAI-compatible), leave the endpoint blank for the provider's default, enter the model and key, click **Test connection**,
  then **Save**. The next quiz generation uses it, with no restart (other instances pick it up
  within the cache TTL). Leaving the key blank keeps the saved key; switching provider needs a new
  key. **Reset to .env** deletes the saved config.
- **OpenAI-compatible** covers any server that speaks the OpenAI Chat Completions format
  (OpenRouter, Together, DeepSeek, Mistral, vLLM, ...). The endpoint is required: a base URL
  (`https://openrouter.ai/api/v1`) gets `/chat/completions` appended, a full URL is used as is. It
  sends `max_tokens` (not OpenAI's `max_completion_tokens`) and strips `<think>` blocks from replies.
- If the saved provider fails (bad key, unknown model, quota), quiz generation returns `502
  AI_PROVIDER_ERROR` with the reason, instead of silently falling back to `.env`.
- **Adding a provider:** add a `@Component` extending `RestAiProvider` in `service/ai`; its
  `name()` then appears in the dashboard and is accepted by the API.

## Deployment

The app runs on Google Cloud Run. Pushing to `main` deploys production and pushing to `staging` deploys
staging, through `.github/workflows/deploy.yml`: test, build the image, deploy a no-traffic revision,
smoke-test `/actuator/health`, then move traffic. All settings and credentials are GitHub repository
variables (`NAME`, with `NAME_STAGING` / `NAME_PRODUCTION` overrides); see [infra/cloud-run.md](infra/cloud-run.md).

- **Background jobs** (event ingestion, weather bulletin, portal issues, session cleanup, feed moderation)
  run from Cloud Scheduler calling `POST /jobs/{name}`, not from in-process timers
  (`SCHEDULER_ENABLED=false` on Cloud Run). Locally the `@Scheduled` timers still run as before.
- **Migrations** use Flyway (`src/main/resources/db/migration`, enabled with `FLYWAY_ENABLED=true`, which the
  deploy sets). See the [migration rules](src/main/resources/db/migration/README.md).
- The older VM-based deployment is described in [infra/deployment.md](infra/deployment.md).
