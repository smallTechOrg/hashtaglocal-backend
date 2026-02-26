

---

# Issue Action Approval Workflow

## What Was Built

A full admin-gated approval workflow for issue lifecycle transitions, with role-based access control and viewer-aware media visibility.

### State Machine

| User Action | Precondition | Immediate Effect | Admin Approval Effect |
|---|---|---|---|
| `REPORT` (create issue) | — | status → `ONHOLD` | `APPROVED` → `OPEN`; `REJECTED` → `REJECTED` |
| `VERIFY` | status = `OPEN` | action saved (`PENDING`), no status change | `APPROVED` → verifyCount++, media approved; `REJECTED` → action/media rejected |
| `VERIFY` | status = `ONHOLD` | **400 — blocked** | — |
| `RESOLVE` | status = `OPEN` | status → `PENDING` | `APPROVED` → `RESOLVED`; `REJECTED` → revert to `OPEN` |
| `REJECT` (owner) | any | status → `REJECTED` | not required |
| `UPDATE` | any | metadata updated | not required |

### Visibility Rules

- **ONHOLD issues**: Hidden from public home feed (`GET /api/v1/issues`). Accessible only to the issue owner via `GET /api/v1/issue/{id}` (other callers receive `404`).
- **Pending verification media**: Media submitted with a `VERIFY` action starts as `ONHOLD`. Only the issue owner sees their own pending media; public callers see only `APPROVED` media.
- **`verifyCount`**: Counts only admin-`APPROVED` VERIFY actions.

### New Files Created

- `model/UserRole.java` — `USER` / `ADMIN` enum
- `model/IssueActionApprovalStatus.java` — `NOT_REQUIRED` / `PENDING` / `APPROVED` / `REJECTED` enum
- `model/MediaStatusModel.java` — added `REJECTED` value
- `service/IssueActionAdminService.java` — handles all six approval/rejection combinations
- `controller/IssueAdminController.java` — `GET /admin/issue-action/pending`, `PUT /admin/issue-action/{id}/approve`, `PUT /admin/issue-action/{id}/reject`
- `model/response/IssueActionAdminResponseData.java` — admin queue response DTO
- `scripts/migrate-issue-action-approval.sql` — reference SQL for production migrations

### Modified Files

- `entity/UserEntity.java` — added `role: UserRole` (default `USER`)
- `entity/IssueActionEntity.java` — added `approvalStatus`, `approvedByUser`, `approvedAt`
- `entity/IssueActionEntity.java` — added nullable `media: MediaEntity` FK (`media_id`) so each action directly references its single attached media item
- `security/AccessTokenAuthFilter.java` — populates `ROLE_ADMIN`/`ROLE_USER` authorities; now runs on all requests (optional auth, not hard-fail on missing token)
- `repository/UserAuthSessionRepository.java` — `@EntityGraph` on `findByAccessToken` to eager-fetch user (needed for role resolution in filter)
- `config/SecurityConfig.java` — added `.requestMatchers("/admin/**").hasRole("ADMIN")`
- `repository/IssueActionRepository.java` — `verifyCount` query filters to `APPROVED` only; added `findByApprovalStatusOrderByCreatedAtAsc`
- `repository/MediaRepository.java` — added `findByIssueAndStatus`, `findByIssueAndStatusIn`
- `service/IssueActionService.java` — VERIFY-on-ONHOLD guard; VERIFY no longer transitions status; sets `approvalStatus` on actions; saves media then sets `action.media` FK directly to the first saved media item
- `service/impl/DefaultIssueReportService.java` — saves a `REPORT` `IssueActionEntity` with `PENDING` approval after issue creation; report media set to `APPROVED`
- `service/IssueQueryService.java` — owner-aware `get(issueId, viewerUserId)` overload; returns `404` for ONHOLD to non-owners; removed fallback-to-id-1 logic
- `service/IssueHomeQueryService.java` — removed `ONHOLD` from public status list
- `mapper/IssueViewMapper.java` — viewer-aware media fetching; `isOwner` in `ViewerContext`; `map(entity, viewerUserId)` overload
- `model/ViewerContext.java` — added `isOwner: boolean`
- `controller/IssueController.java` — `getIssue` accepts optional `@AuthenticationPrincipal` for owner checks
- `integration/IssueTestDataConfig.java` (test) — media status set to `APPROVED`; updated assertions to match new visibility contract
- `integration/IssueHomeIntegrationTests.java` (test) — updated for ONHOLD-excluded public feed

### DB Migrations (ddl-auto: update applies automatically in dev)

```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS role VARCHAR(20) NOT NULL DEFAULT 'USER';
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL;
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approved_by_user_id BIGINT REFERENCES users(id);
ALTER TABLE issue_actions ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP;
ALTER TABLE media ADD COLUMN IF NOT EXISTS issue_action_id BIGINT REFERENCES issue_actions(id);
```

## Status

✅ **COMPLETE** — All 94 tests pass. Full build and spotless formatting verified.
