# Notification System — Design Document

Push notifications are delivered via Firebase Cloud Messaging (FCM).

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Database Schema](#2-database-schema)
3. [API Contracts](#3-api-contracts)
4. [Notification Types](#4-notification-types)
5. [Trigger Map](#5-trigger-map)
6. [Delivery Rules](#6-delivery-rules)
7. [Error Handling](#7-error-handling)

---

## Implementation Status

| Feature | Status |
|---|---|
| Device token register / remove | ✅ Done |
| `ISSUE_DETAIL` — `STATUS_CHANGE` → `OPEN` (reporter only) | ✅ Done |
| `ISSUE_DETAIL` — other status changes (`ONHOLD`, `RESOLVED`, `REJECTED`) | ⬜ Planned |
| `ISSUE_DETAIL` — `VERIFIED` / `RESOLVED_BY_USER` events | ⬜ Planned |
| Notify verifiers (not just reporter) | ⬜ Planned |
| `NEARBY_ISSUE` | ⬜ Planned |
| `BULLETIN` | ⬜ Planned |
| `CHAT` | ⬜ Planned |
| `BROADCAST` — ops-portal "send to everyone" (`POST /admin/notification`) | ✅ Done |
| `PORTAL_ISSUE_CLOSED` | ⬜ Planned |
| `ACTIVITY_REMINDER` | ⬜ Planned |
| Don't-notify-actor / dedup rules | ⬜ Planned |
| iOS APNs support | ✅ Done |

---

## 1. Architecture Overview

```
Mobile App
  │
  ├─ POST /account/device-token        ← on login (if token changed)
  └─ DELETE /account/device-token      ← on logout

Backend (event or cron fires)
  │
  ├─ resolve target users (reporter / verifiers / locality members)
  ├─ fetch their device tokens from device_tokens table
  ├─ call FcmSender.send() or sendMulticast()
  └─ delete stale tokens returned by sendMulticast()

Firebase → Android device
  │
  ├─ Foreground  → Alert popup
  ├─ Background  → system tray; tap → navigate
  └─ Killed      → system tray; tap → app opens + navigate
```

**Location model (how targeting works):**

```
User → location_id → Location → locality_id → Locality (polygon, hashtag)
Issue → location_id → Location → locality_id → Locality
```

Users and issues share the same locality hierarchy. Locality-based notifications query all users whose `location.locality_id` matches the issue's locality — no radius arithmetic needed at the app layer.

---

## 2. Database Schema

### device_tokens

```sql
CREATE TABLE device_tokens (
  id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token       TEXT        NOT NULL UNIQUE,
  platform    TEXT        NOT NULL CHECK (platform IN ('android', 'ios')),
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
```

One user can have multiple rows (multiple devices). If a token arrives for a different `user_id` (re-used device), update `user_id` on the existing row.

---

## 3. API Contracts

### 3.1 Register Device Token

Called by the app after every login, only when the FCM token has changed.

```
POST /account/device-token
Authorization: Bearer <jwt>
Content-Type: application/json
```

**Request**
```json
{
  "data": {
    "token": "fcm-token-string",
    "platform": "android"
    }
}
```

**Backend behaviour:** extract `userId` from JWT → upsert the row (insert or update `user_id` + `updated_at`).

**Response:** `200 OK`

---

### 3.2 Remove Device Token

Called by the app on logout.

```
DELETE /account/device-token
Authorization: Bearer <jwt>
Content-Type: application/json
```

**Request**
```json
{
  "data": {
    "platform": "android"
    }
}
```

**Backend behaviour:** delete all `device_tokens` rows for this `user_id` + `platform`.

**Response:** `200 OK`

---

## 4. Notification Types

All FCM `data` values must be **strings** (no numbers or booleans). `notification.title` / `notification.body` is what the user sees in the tray.

---

### 4.1 `ISSUE_DETAIL`

> **Status:** Partially implemented. Only `STATUS_CHANGE → OPEN` is live, and only the reporter is notified. Other events and verifier notifications are planned.

**When:** anything significant happens to an issue the user is involved with:
- Status changes: `PENDING` → `OPEN` ✅ · `OPEN` → `RESOLVED` / `ONHOLD` / `REJECTED` ⬜
- Another user's `VERIFY` action is approved for this issue ⬜
- Another user's `RESOLVE` action is approved for this issue ⬜

**Recipients:**
- The user who submitted the original `REPORT` action (`issue_actions.action = 'REPORT'`) ✅
- All users who submitted a `VERIFY` action on the same `issue_id` (regardless of their approval status — they cared enough to verify) ⬜

**Payload**
```json
{
  "type":    "ISSUE_DETAIL",
  "issueId": "uuid-string",
  "status":  "RESOLVED",
  "event":   "STATUS_CHANGE"
}
```

| Field | Values |
|---|---|
| `status` | `OPEN` · `RESOLVED` · `ONHOLD` · `REJECTED` · `PENDING` |
| `event` | `STATUS_CHANGE` · `VERIFIED` · `RESOLVED_BY_USER` |

**Title / body by event**

| `event` | Title | Body |
|---|---|---|
| `STATUS_CHANGE` → `OPEN` | "Issue is live" | "Your report has been approved and is now open." |
| `STATUS_CHANGE` → `ONHOLD` | "Issue on hold" | "Your report has been put on hold for review." |
| `STATUS_CHANGE` → `RESOLVED` | "Issue resolved" | "The issue you reported has been resolved." |
| `STATUS_CHANGE` → `REJECTED` | "Issue closed" | "Your report could not be actioned." |
| `VERIFIED` | "Someone verified your report" | "A user near you confirmed this issue." |
| `RESOLVED_BY_USER` | "Someone resolved your report" | "A user near you marked this as resolved." |

**App behaviour:** tapping opens the issue detail screen for `issueId`.

---

### 4.2 `NEARBY_ISSUE` ⬜ Planned

**When:** a new issue is created and its status moves to `OPEN` (approved by ops).

**Recipients:** all users whose `location.locality_id` matches the issue's `location.locality_id`. If the locality has very few users, walk up the `parent_id` chain one level to widen the audience.

**Payload**
```json
{
  "type":       "NEARBY_ISSUE",
  "issueId":    "uuid-string",
  "localityId": "uuid-string",
  "hashtag":    "#koramangala"
}
```

**Use cases (same notification type, different body copy):**

| Scenario | Title | Body |
|---|---|---|
| New issue in locality | "Issue near you" | "A new issue was reported in #koramangala. Can you verify?" |
| Unverified issue needs attention | "Help verify a report" | "An issue in #koramangala needs verification. Head out and check." |
| Issue resolved — ask locality to confirm | "Has this been fixed?" | "A reported issue in #koramangala was marked resolved. Go check!" |

**App behaviour:** tapping opens the Map tab centred on the locality.

**Config** (to be added to `application.yaml`):
```yaml
notifications:
  nearby-issue:
    min-locality-audience: ${NEARBY_ISSUE_MIN_AUDIENCE:5}  # walk up parent if below this
```

---

### 4.3 `BROADCAST` ✅ Done

**When:** an admin sends one manually from the ops portal (`/ops/broadcast`).

**Recipients:** every user with an active device token (i.e. all of `findAllActiveNotificationTokens()`), batched at 500 tokens per FCM multicast call.

**Endpoint:** `POST /admin/notification` (ADMIN role required). `notification.type` must be `BROADCAST` — other types are rejected with `400`.
```json
// Request
{
  "notification": {
    "type": "BROADCAST",
    "payload": {
      "title": "New feature: City Bulletins",
      "body": "Check the weather and answer today's quiz."
    }
  }
}

// Response — notification_delivered excludes stale tokens dropped by FCM
{ "data": { "notification_delivered": 479 } }
```

**Payload sent to devices**
```json
{
  "type": "BROADCAST"
}
```

**App behaviour:** tapping opens the home (map) tab. No history is stored — fire-and-forget by design.

---

### 4.4 `PORTAL_ISSUE_CLOSED` ⬜ Planned

**When:** the `PortalIssueTrackingJob` cron detects that a `GovPortalEntity` status has changed from `OPEN` to closed/resolved on the government portal.

**Recipients:**
- The user who submitted the original `REPORT` action for that `issue_id`
- All users who submitted a `VERIFY` action for that `issue_id`

**Goal:** ask them to go out and physically verify whether the issue is actually fixed — and upload a photo.

**Payload**
```json
{
  "type":    "PORTAL_ISSUE_CLOSED",
  "issueId": "uuid-string",
  "portal":  "bbmp"
}
```

| Field | Notes |
|---|---|
| `issueId` | The internal issue UUID (not the portal tracking ID) |
| `portal` | Which government portal (e.g. `"bbmp"`, `"ghmc"`) |

**Title / body**

- Title: "Govt says it's fixed — is it?"
- Body: "The issue you reported has been marked resolved on the portal. Go check and upload a photo!"

**App behaviour:** tapping opens the issue detail screen so the user can take / upload a photo.

---

### 4.4 `ACTIVITY_REMINDER` ⬜ Planned

**When:** a scheduled job runs and finds issues where the reporter has had no activity for N days — a nudge to go back out and see if anything has changed.

**Recipient:** the user who submitted the `REPORT` action.

**Trigger logic (cron job):**
1. Find issues with status `OPEN` or `ONHOLD` where the reporter's last action on that issue was > N days ago (configurable).
2. Send one reminder per issue, per user. Do not repeat until another N days pass.

**Payload**
```json
{
  "type":    "ACTIVITY_REMINDER",
  "issueId": "uuid-string"
}
```

**Title / body examples** (can be varied — pick randomly or by time of day):

| Variant | Title | Body |
|---|---|---|
| Default | "Time for a walk?" | "You reported an issue nearby. Head out and see if anything has changed." |
| Morning | "Good morning!" | "Your report is still open. A quick walk might tell you more." |
| Encouraging | "Your report matters" | "Issues get resolved faster when reporters follow up. Go take a look!" |

**App behaviour:** tapping opens the issue detail screen.

**Config** (to be added to `application.yaml`):
```yaml
notifications:
  activity-reminder:
    enabled: ${ACTIVITY_REMINDER_ENABLED:true}
    inactivity-days: ${ACTIVITY_REMINDER_INACTIVITY_DAYS:7}
    cron: ${ACTIVITY_REMINDER_CRON:0 0 9 * * *}   # 9 AM daily
```

---

## 5. Trigger Map

| Backend event | Type | Recipients | Multicast? |
|---|---|---|---|
| Issue status changes | `ISSUE_DETAIL` | Reporter + all verifiers | No — per user |
| VERIFY/RESOLVE action approved | `ISSUE_DETAIL` | Reporter + all verifiers | No — per user |
| New issue status → OPEN | `NEARBY_ISSUE` | All users in same locality | Yes |
| Portal cron detects OPEN → closed | `PORTAL_ISSUE_CLOSED` | Reporter + all verifiers | No — per user |
| Inactivity threshold crossed | `ACTIVITY_REMINDER` | Reporter | No — per user |

---

## 6. Delivery Rules

- **Don't notify the actor.** ⬜ If the reporter is also the one triggering the action (e.g. they verify their own issue), suppress the notification to them.
- **One notification per event per user.** ⬜ If a user is both reporter and verifier, send one message — not two.
- **`ACTIVITY_REMINDER` — once per N-day window.** ⬜ Track last reminder sent per user+issue to avoid repeat spam. A simple `last_reminded_at` column on `issue_actions` or a separate `notification_log` table works.
- **`NEARBY_ISSUE` — only for OPEN issues.** ⬜ Do not fire for issues still in `PENDING` (awaiting ops approval).
- **Android priority:** ✅ All messages use `AndroidConfig.Priority.HIGH`.
- **iOS (when ready):** ⬜ Add `ApnsConfig` with `content-available: 1` and upload the APNs `.p8` key to Firebase Console.

---

## 7. Error Handling

`FcmSender.sendMulticast()` returns tokens FCM reports as dead. The calling service must delete them:

```java
List<String> stale = fcmSender.sendMulticast(tokens, title, body, data);
stale.forEach(deviceTokenRepository::deleteByToken);
```

| FCM error | Meaning | Action |
|---|---|---|
| `UNREGISTERED` | App uninstalled / token rotated | Delete from `device_tokens` |
| `INVALID_ARGUMENT` | Malformed token | Delete from `device_tokens` |
| `QUOTA_EXCEEDED` | Rate limit | Retry with exponential back-off |
| `UNAVAILABLE` | FCM transient error | Retry |
| `INTERNAL` | FCM error | Log + retry once |
