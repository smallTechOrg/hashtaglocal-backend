

# Issue import (blr-pages)

Source: https://blr-map-api.warlockdn.workers.dev/api/data (Bengaluru only).

Sample payload
```json
{
  "lat": 12.8719833333333,
  "long": 77.5991444444444,
  "image": "https://ik.imagekit.io/blrpotholes/1750180881636-kqial2dniap_FXaCNY6Ol.jpg",
  "image_thumb": "https://ik.imagekit.io/blrpotholes/tr:n-ik_ml_thumbnail/1750180881636-kqial2dniap_FXaCNY6Ol.jpg",
  "category": 3,
  "created_at": "2025-06-17T17:21:24.077Z"
}
```

Mapping
- Location: use lat/long as-is; attach to #bengaluru (fallbacks to #world if missing), name `Bengaluru`.
- Media: download `image`, upload to GCS `gs://hashtaglocalbucket/issues/blr-pages/<issue-key>.jpg`.
- Description: `Sourced from blr-pages, category: <category>`.
- Issue type/status: `POTHOLE` + `OPEN`.
- created_at: preserved as source timestamp (UTC) for both `createdAt` and `updatedAt`.

Idempotency and traceability
- Unique key per issue: sanitized `created_at` -> stored as issue key `BLR-<...>` and tracked in `issue_import_status` (unique on source + source_issue_id).
- Jobs tracked in `issue_import_jobs`; per-item results stored in `issue_import_status` with payload snapshot and stored media path.

Running the import
- Start: `POST /admin/issues/import?source=BLR_PAGES`
- Check job: `GET /admin/issues/import/{jobId}`
- Check items: `GET /admin/issues/import/{jobId}/status`

Extending to new sources
- Add a new `IssueImportSource` entry.
- Implement a handler in `IssueImportService` that: fetches source data, builds an issue key, de-duplicates via `IssueImportStatusRepository`, uploads media to GCS, and stores issues/media.

