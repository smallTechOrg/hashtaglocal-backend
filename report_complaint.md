# Gov Portal Complaint Reporting API - Backend Spec

## Objective
Implement and document a bastion API that accepts complaint payloads from frontend, validates them, forwards them to the staging scraper endpoint, and returns a tracking ID.

## Endpoint
- Method: `POST`
- Path: `/api/v1/report_complaint`
- Auth: Reuse the same authentication mechanism currently used by ops/admin review APIs (if applicable in current backend design).

## Request Contract

### Request JSON
```json
{
  "source": "GOV_ISSUE_PORTAL",
  "context": {
    "portal": "SMARTONEBLR",
    "action": {
      "type": "REPORT_ISSUE",
      "data": {
        "category": "Road Engineering",
        "sub_category": "Potholes",
        "description": "Broken road near signal",
        "media_url": "https://example.com/image.jpg",
        "latitude": "12.9716",
        "longitude": "77.5946"
      }
    },
    "auth": {
      "username": "portal_user",
      "password": "portal_pass"
    }
  }
}
```

### Validation Rules
1. `source` is required.
2. `source` accepted values:
   - `GOV_PORTAL_ISSUE` (primary)
   - `GOV_ISSUE_PORTAL` (optional backward compatibility)
3. `context.portal` is required (non-empty string).
4. `context.action.type` is required and must be exactly `REPORT_ISSUE`.
5. `context.action.data.category` is required.
6. `context.action.data.sub_category` is required.
7. `context.auth.username` is required.
8. `context.auth.password` is required.
9. `description`, `media_url`, `latitude`, `longitude` are optional and accepted as strings.

## Downstream Forwarding
- Forward validated request to:
  - `https://staging.api.smalltech.in/webscraperstaging/api/v1/scrape`
- Behavior:
  - This endpoint acts as a pass-through bastion with validation.
  - Preserve the complaint intent payload needed by downstream.
  - Parse downstream success body and extract `tracking_id`.

## Success Response
- HTTP `200 OK`
- Response JSON:

```json
{
  "data": {
    "tracking_id": 232
  }
}
```

Notes:
- `tracking_id` must be numeric.

## Error Handling
Return non-200 status codes with clear, debuggable messages.

Recommended mapping:
- `400 Bad Request`: payload validation failures.
- `401/403`: auth failure, if endpoint-level auth applies.
- `502 Bad Gateway`: downstream returns error/invalid response.
- `504 Gateway Timeout`: downstream did not respond in configured timeout window.

Recommended error body:
```json
{
  "error": {
    "code": "DOWNSTREAM_TIMEOUT",
    "message": "Government portal request timed out",
    "details": null
  }
}
```

## Timeout and Long-Running Behavior
- External gov workflow can be slow.
- Frontend wait budget: up to `300 seconds`.
- Configure app HTTP client timeout and relevant proxy/gateway timeout settings to support up to 300 seconds where possible.
- Ensure timeout errors are deterministic and return a meaningful response body.

## Acceptance Criteria
1. Endpoint accepts payload in the contract above.
2. Successful downstream submission returns `200` with numeric `tracking_id`.
3. Validation and downstream failures return consistent, actionable error responses.
4. Contract is fully documented: fields, validation, success, and error models.

## Implementation Notes (Suggested)
- Add DTO-level validation for required fields.
- Add service-layer guard for fixed enum-like values (`source`, `action.type`).
- Add integration tests for:
  - happy path (returns tracking ID)
  - invalid source
  - missing required fields
  - downstream 5xx
  - downstream timeout