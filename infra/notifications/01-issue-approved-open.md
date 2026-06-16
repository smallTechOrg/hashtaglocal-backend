# Notification 01 — Issue Approved (ONHOLD → OPEN)

**Type:** `ISSUE_DETAIL`  
**Event:** `STATUS_CHANGE → OPEN`  
**Recipient:** The reporter (the user who submitted the original `REPORT` action)

---

## What happens

When an admin approves a REPORT action, the issue transitions from `ONHOLD` to `OPEN`.  
The reporter gets a push notification telling them their report is now live.

---

## Trigger — where in the code

`IssueActionAdminService.handleApproval()` already publishes an `IssueStatusChangedEvent` after the status is saved:

```java
// IssueActionAdminService.java — already exists, no change needed
eventPublisher.publishEvent(
    new IssueStatusChangedEvent(issueEntity.getId(), issueEntity.getStatus()));
```

`FeedIssueRefListener` already consumes this event with `@TransactionalEventListener(phase = AFTER_COMMIT)`.  
The notification listener hooks in the same way — a second independent listener on the same event.

---

## FCM Payload

```json
{
  "type":    "ISSUE_DETAIL",
  "issueId": "42",
  "status":  "OPEN",
  "event":   "STATUS_CHANGE"
}
```

All values are strings (FCM requirement).

| Field | Value |
|---|---|
| Title | "Issue is live" |
| Body | "Your report has been approved and is now open." |

App behaviour on tap: open the issue detail screen for `issueId`.

---

## Files to create

### `IssueNotificationListener.java`
**Package:** `service/` (alongside `FeedIssueRefListener`)

Listens to `IssueStatusChangedEvent` after commit. For the `OPEN` case:
1. Fetch the reporter's user ID from `IssueActionRepository` (the REPORT action for this issue).
2. Fetch all device tokens for that user from `DeviceTokenRepository`.
3. Call `FcmSender.send()` once per token.
4. Delete any stale tokens FCM rejects back.

Suppression: if the reporter has no device tokens, silently skip. No exception should leak.

---

## Files to change

### `IssueActionRepository.java`

Add a query to find the user who filed the REPORT for a given issue:

```java
@Query("SELECT a.userEntity FROM IssueActionEntity a " +
       "WHERE a.issueEntity.id = :issueId AND a.action = 'REPORT'")
Optional<UserEntity> findReporterByIssueId(@Param("issueId") Long issueId);
```

`DeviceTokenRepository` already has `findAllByUserId(Long userId)` — no changes needed there.

---

## What is NOT in scope for this implementation

- Notifying verifiers (no verifiers exist yet at this point — the issue just became OPEN)
- `REJECTED` / `ONHOLD` status change notifications (separate implementations)
- Stale token multicast cleanup (single `send()` per token is used here, not multicast)

---

## Sequence

```
Admin calls PUT /admin/issue-action/{actionId}/approve
  └─ IssueActionAdminService.handleApproval()
       ├─ issue.status = OPEN
       ├─ issueRepository.save(issue)           ← DB commit
       └─ publishEvent(IssueStatusChangedEvent)

After commit →
  ├─ FeedIssueRefListener.onIssueStatusChanged()  ← already exists
  └─ IssueNotificationListener.onIssueStatusChanged()  ← NEW
       ├─ find reporter via IssueActionRepository
       ├─ fetch tokens via DeviceTokenRepository
       └─ FcmSender.send() per token
```
