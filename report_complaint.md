Use this prompt for backend:

Backend Task: Implement Gov Portal Complaint Reporting API

Please implement and document an endpoint for reporting an issue to the government portal.

Endpoint
1. Method: POST
2. Path: /api/v1/report_complaint
3. Auth: same auth mechanism currently used by ops/admin review APIs (if required in your backend design)

Request Body
{
  "source": "GOV_PORTAL_ISSUE",
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

Validation Rules
1. source is required and should accept GOV_PORTAL_ISSUE (optionally also accept GOV_ISSUE_PORTAL for backward compatibility).
2. context.portal is required.
3. context.action.type is required and fixed to REPORT_ISSUE.
4. category and sub_category are required.
5. username and password are required.
6. description, media_url, latitude, longitude can be accepted as strings.


you will send this to https://staging.api.smalltech.in/webscraperstaging/api/v1/scrape
and will get tracking id on correct response. this is kind of bastion request where you take the request from frontend, verify and send to correct endpoint. 
Success Response
1. HTTP 200
2. Body:
{
  "data": {
    "tracking_id": 232
  }
}


Failure Response
1. Return proper non-200 codes with clear error body/message if validation fails or downstream portal call fails.
2. If downstream portal is slow/unreachable, return a meaningful timeout/failure response.

Timing Expectation
1. This API can take long due to external gov portal latency.
2. Frontend waits up to 300 seconds for response.
3. Please ensure server/proxy timeouts allow long-running request handling up to 300 seconds where possible.

Acceptance Criteria
1. Endpoint accepts payload in the above structure.
2. On successful portal submission, returns HTTP 200 with numeric tracking_id.
3. Error scenarios are consistent and debuggable.
4. API contract is documented (request fields, validation, success/error responses).

If you want, I can also give you a stricter OpenAPI-ready version of this prompt with exact schemas and error models.