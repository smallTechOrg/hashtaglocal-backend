# Scraping Worker System Implementation Plan

## 1. Objective

Build a safe, scheduled scraping workflow in Spring Boot + PostgreSQL that:

1. Runs every 30 minutes.
2. Finds `portal_issues` rows where `updated_at < now() - 24 hours`.
3. Sends one scrape request at a time to the external API.
4. Updates `status`, `meta_data`, and `updated_at` on success.
5. Remains safe and correct when multiple app instances are running.

## 2. System Architecture

Proposed architecture has four layers:

1. Scheduler Layer
- Triggers every 30 minutes.
- Runs with a fixed delay schedule so that a new scheduler cycle begins
  30 minutes after the previous cycle finishes.

2. Job Selection Layer
- Selects eligible `portal_issues` records (`updated_at` older than 24 hours).
- Claims work in small batches to avoid large locks and long transactions.

3. Worker Processing Layer
- Processes exactly one HTTP scrape call at a time (single-thread worker).
- Handles request/response mapping and retries.

4. Persistence/State Layer
- Writes success/failure outcomes to database.
- Maintains auditability and retry readiness using timestamps and status metadata.

## 3. Database Interaction Strategy

### 3.1 Existing Table Usage

Primary source table: `portal_issues`.

Required fields used directly:
- `tracking_id`
- `portal`
- `status`
- `meta_data`
- `updated_at`

### 3.2 Eligibility Query

An issue is eligible when:

1. `updated_at` is older than 24 hours.
2. Record is not currently being processed by another worker run.

### 3.3 Processing Pattern

The system processes issues sequentially.

Scheduler behavior:

1. The scraping job runs with a fixed delay.
2. The next run starts **30 minutes after the previous run has completely finished**.

Processing approach:

1. Fetch one eligible row from `portal_issues` where  
   `updated_at < now() - interval '24 hours'`.

2. Process the issue:
   - build the scrape request
   - call the external scraping API
   - update the issue row (`status`, `meta_data`, `updated_at`).

3. After processing the row, fetch the next eligible row and repeat until no rows remain.

Because the system processes rows sequentially and a new cycle only begins after the previous one finishes, no additional job queue, claim-state columns, or row claiming mechanism is required.

## 4. Background Worker Design

### 4.1 Single-Request-at-a-Time Guarantee

The system processes scraping requests sequentially.

The scheduler execution itself performs the processing loop, meaning:

1. The scheduler fetches one eligible issue.
2. Processes it by calling the scraping API.
3. Updates the database.
4. Then fetches the next eligible issue.

This loop continues until no eligible rows remain.

Because the scheduler processes issues sequentially within a single execution cycle, only one scraping request is active at any given time.

### 4.2 Processing Loop

The scheduler processes issues sequentially.

For each iteration:

1. Fetch one eligible row from `portal_issues` where  
   `updated_at < now() - interval '24 hours'`.

2. Build the request payload using:
   - `portal` from `portal_issues.portal`
   - `tracking_id` from `portal_issues.tracking_id`
   - `action.type = TRACK_ISSUE`

3. Send POST request to the scraping API endpoint.

4. Parse the response:
   - `data.status`
   - `data.meta_data`

5. Update the row:
   - `status = response.data.status`
   - `meta_data = response.data.meta_data`
   - `updated_at = now()`

6. Repeat until no eligible rows remain.

## 5. Scheduler Behavior

### 5.1 Trigger Policy

The scraping job runs using a fixed delay schedule.

The next execution starts **30 minutes after the previous execution has completed**.

This ensures that only one scheduler run is active at any time.

### 5.2 Execution Flow

Each scheduler execution performs the following steps:

1. Start scheduler cycle.
2. Fetch one eligible issue from `portal_issues`.
3. Process the issue by calling the scraping API.
4. Update the database row.
5. Fetch the next eligible issue.
6. Continue until no eligible issues remain.
7. End scheduler cycle.

The next scheduler execution begins 30 minutes after the cycle completes.

## 6. API Request Flow

### 6.1 Outbound Request

Request structure:

1. `source = GOV_ISSUE_PORTAL`
2. `context.portal = portal_issues.portal`
3. `context.action.type = TRACK_ISSUE`
4. `context.action.data.tracking_id = portal_issues.tracking_id`
5. `context.auth.username/password` can be any placeholder strings. They are included in the request payload but are not used for authentication in this system.

### 6.2 HTTP Settings

1. Configure reasonable connect and read timeouts for the HTTP client.
2. No automatic retry logic is implemented.
3. If a request fails, the error is logged and the issue will be retried naturally
   in a future scheduler cycle because the row remains unchanged.

## 7. Error Handling Strategy

The system uses a simple error handling approach.

If a scraping request fails due to any error (network error, API error, parsing error, etc.):

1. The error is logged.
2. The corresponding database row is left unchanged.
3. The scheduler proceeds to the next issue.

Because the row remains unchanged, it will continue to satisfy the eligibility condition (`updated_at < now() - 24 hours`) and will automatically be retried in a future scheduler cycle.

No retry counters, failure states, or additional database fields are required for the initial implementation.

## 8. Concurrency Handling

The system processes scraping requests sequentially.

Concurrency control is achieved through the following design:

1. The scheduler runs with a fixed delay, meaning the next execution starts **30 minutes after the previous execution finishes**.

2. During each execution cycle, the system processes issues one at a time in a sequential loop.

3. Only one scraping request is active at any given moment.

Because the system processes issues sequentially within a single scheduler execution, no additional worker threads, job queues, or concurrency control mechanisms are required.

## 9. Package and Class Structure

Proposed package layout under `org.smalltech.hashtaglocal_backend`:

1. `job`
- `PortalIssueTrackingJob`
   - scheduled trigger with fixed delay
   - invokes the tracking service once per cycle

2. `service`
- `PortalIssueTrackingService`
   - contains the full sequential processing loop
   - fetches one eligible row, processes it, then fetches next row
   - keeps per-cycle in-memory attempted IDs to avoid reprocessing the same failed row in the same cycle

3. `service`
- `PortalIssueScrapeClient`
   - sends the POST scrape request
   - parses and returns `data.status` and `data.meta_data`

4. `repository`
- `GovPortalRepository`
   - fetches next eligible `portal_issues` row(s)
   - persists updated status/meta data on success

5. `dto`
- `TrackIssueScrapeRequestDTO`
   - request body model for scrape POST endpoint
- `TrackIssueScrapeResponseDTO`
   - response body model for scrape POST endpoint

Configuration values are read directly from `application.yaml` using `@Value`; no dedicated properties class is required.

## 10. End-to-End Operation

1. The scheduler starts a scraping cycle.
2. The scheduler runs with a fixed delay, meaning the next execution starts **30 minutes after the previous cycle finishes**.

During each cycle:

1. Fetch one eligible issue from `portal_issues` where  
   `updated_at < now() - interval '24 hours'`.

2. Build the scraping request using:
   - `portal` from `portal_issues.portal`
   - `tracking_id` from `portal_issues.tracking_id`
   - `action.type = TRACK_ISSUE`

3. Send a POST request to the scraping API.

4. If the request succeeds:
   - extract `data.status`
   - extract `data.meta_data`
   - update the database row:
     - `status`
     - `meta_data`
     - `updated_at = now()`

5. If the request fails:
   - log the error
   - leave the row unchanged.

6. Fetch the next eligible issue and repeat until no eligible rows remain.

7. End the scheduler cycle.

8. The next cycle begins **30 minutes after the previous cycle completes**.

## 11. Configuration Notes

1. The scraping API endpoint URL should be configurable through application configuration (`application.yml` or environment variables).

2. The scheduler delay (30 minutes) should be configurable.

3. `context.auth.username` and `context.auth.password` in the request body can be static placeholder values.

4. Application logs should include:
   - portal
   - tracking_id
   - request success or failure

Sensitive information should not be logged.

## 12. Implementation Sequence

1. Review this implementation plan and confirm understanding of the system behavior.

2. Create the following files:

   - `PortalIssueTrackingJob`
   - `PortalIssueTrackingService`
   - `PortalIssueScrapeClient`
   - `GovPortalRepository`
   - `TrackIssueScrapeRequestDTO`
   - `TrackIssueScrapeResponseDTO`

3. Update configuration in `application.yaml` for endpoint URL, scheduler delay, stale threshold, and auth placeholders.

4. Implement the sequential loop directly in `PortalIssueTrackingService`:
   - fetch one eligible row
   - process and update on success
   - log and continue on failure
   - prevent same-cycle repeated attempts for a failed row

5. Add focused tests for repository query behavior and service/job flow.

6. Provide a short summary of implemented files and responsibilities.

This document is the architecture and execution plan only. No code implementation is included yet.
