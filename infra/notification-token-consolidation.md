# Notification Token Consolidation

## Overview

Move FCM/APNs push notification token storage from the standalone `device_tokens` table into
`user_auth_sessions`. The token-management API (`POST/DELETE /account/device-token`) remains
unchanged in structure — only its backing storage changes. "Device token" and "notification token"
are the same thing; `notification_token` is the renamed column.

---

## Problem

- `device_tokens` is a separate table with its own lifecycle, but tokens are meaningless
  without a live session — orphaned rows accumulate after sessions expire or are revoked.
- Keeping tokens and sessions in sync requires two separate cleanup paths.
- The `Platform` enum (`android`, `ios`) has no concept of web clients.

---

## Solution

1. Add `notification_token` (TEXT, nullable) and `platform` (VARCHAR(15), nullable) to
   `user_auth_sessions`. These replace the `token` and `platform` columns in `device_tokens`.
2. Refactor `DeviceTokenService` to read/write these fields on the session row identified by
   the caller's Bearer token, instead of managing a separate table.
3. Extend `Platform` with `WEB_ANDROID` and `WEB_IOS`.
4. Drop the `device_tokens` table and delete its entity/repository.
5. Stale-token cleanup: set `notification_token = NULL` on the session (session stays active).
6. Web platforms (`WEB_ANDROID`, `WEB_IOS`) never carry a notification token — no push is sent.
7. At most one FCM token per user per mobile platform (`android`/`ios`). Registering a new token
   for a platform deactivates (`isActive = false`) any other active session for that user+platform
   that already holds a notification token.
8. `POST /auth/refresh` and all auth (login) endpoints are **not changed**.

---

## API Changes

The endpoint paths and HTTP methods do not change. Only the internal storage backing them changes.

### POST /account/device-token (unchanged contract)

```json
{
  "data": {
    "notification_token": "<FCM token>",
    "platform": "android"
  }
}
```

`token` field in the request body is **renamed** to `notification_token`.
The authenticated Bearer token identifies which session receives the token.

### DELETE /account/device-token (unchanged contract)

```json
{
  "data": {
    "platform": "android"
  }
}
```

Clears `notification_token` on all sessions for the authenticated user+platform (sessions
remain active; only the push capability is removed).

### No changes to auth endpoints

`POST /auth/apple`, `GET /auth/google/token`, `GET /auth/google/callback`, `POST /auth/refresh`
are not modified.

---

## Platform enum values

| Value | Push notifications sent? | Use case |
|-------|--------------------------|----------|
| `android` | Yes | Native Android app (FCM) |
| `ios` | Yes | Native iOS app (APNs via FCM) |
| `WEB_ANDROID` | No | Web browser on Android — platform tracked, no push |
| `WEB_IOS` | No | Web browser on iOS — platform tracked, no push |

---

## Database migration

**Script:** `scripts/migrate-notification-token-consolidation.sql`

```sql
-- Step 1: extend user_auth_sessions
ALTER TABLE user_auth_sessions
  ADD COLUMN notification_token TEXT,
  ADD COLUMN platform VARCHAR(15);

-- Step 2: drop the replaced table
-- (no data migration — tokens may be stale and clients will re-register
--  via POST /account/device-token after the deploy)
DROP TABLE device_tokens;
```

**Run this script BEFORE deploying the new JAR.**

Rollback SQL (apply before JAR rollback if needed):
```sql
ALTER TABLE user_auth_sessions
  DROP COLUMN notification_token,
  DROP COLUMN platform;

-- Restore device_tokens from a pre-migration DB snapshot if required
```

---

## Code changes

### Files modified

| File | Change |
|------|--------|
| `model/Platform.java` | Add `WEB_ANDROID`, `WEB_IOS` |
| `entity/UserAuthSessionEntity.java` | Add `notificationToken`, `platform` fields |
| `repository/UserAuthSessionRepository.java` | Add `findActiveNotificationTokensByUserId`, `clearNotificationToken`, `deactivateByUserIdAndPlatformWithToken` |
| `service/DeviceTokenService.java` | Rewrite to operate on `user_auth_sessions` via `UserAuthSessionRepository`; inject `UserAuthSessionRepository` instead of `DeviceTokenRepository` |
| `controller/DeviceTokenController.java` | Pass Bearer token (from `Authorization` header) to service so it can identify the current session |
| `dto/RegisterDeviceTokenRequest.java` | Rename field `token` → `notificationToken` (JSON: `notification_token`) |
| `service/IssueNotificationListener.java` | Replace `DeviceTokenRepository` with `UserAuthSessionRepository` |

### Files deleted

| File | Reason |
|------|--------|
| `entity/DeviceTokenEntity.java` | Table dropped |
| `repository/DeviceTokenRepository.java` | Replaced by new methods on `UserAuthSessionRepository` |

---

## Key implementation details

### Identifying the session in DeviceTokenService

`POST /account/device-token` is an authenticated request — the caller's Bearer token is already
validated by `AccessTokenAuthFilter`. The controller reads it from the `Authorization` header
and passes it to the service. The service calls `userAuthSessionRepository.findByAccessToken(token)`
to get the exact session to update.

```java
// DeviceTokenService.register(String accessToken, String notificationToken, Platform platform)
UserAuthSessionEntity session = userAuthSessionRepository
    .findByAccessToken(accessToken)
    .orElseThrow(() -> new RuntimeException("Session not found"));

// Deactivate other active sessions for this user+platform that already have a token
if (notificationToken != null
    && (platform == Platform.android || platform == Platform.ios)) {
  userAuthSessionRepository.deactivateByUserIdAndPlatformWithToken(
      session.getUser().getId(), platform);
}

session.setNotificationToken(notificationToken);
session.setPlatform(platform);
userAuthSessionRepository.save(session);
```

### Repository methods added to UserAuthSessionRepository

```java
// For FCM multicast send in IssueNotificationListener
@Query("SELECT s.notificationToken FROM UserAuthSessionEntity s "
     + "WHERE s.user.id = :userId AND s.isActive = true AND s.notificationToken IS NOT NULL")
List<String> findActiveNotificationTokensByUserId(@Param("userId") Long userId);

// For FCM stale-token cleanup
@Modifying
@Query("UPDATE UserAuthSessionEntity s SET s.notificationToken = NULL "
     + "WHERE s.notificationToken = :token")
int clearNotificationToken(@Param("token") String token);

// For deduplication — deactivate the old session before registering a new token
@Modifying
@Query("UPDATE UserAuthSessionEntity s SET s.isActive = false "
     + "WHERE s.user.id = :userId AND s.platform = :platform "
     + "AND s.notificationToken IS NOT NULL AND s.isActive = true")
int deactivateByUserIdAndPlatformWithToken(@Param("userId") Long userId,
                                           @Param("platform") Platform platform);
```

### DeviceTokenService.remove (DELETE /account/device-token)

Clears the notification_token on all sessions for userId+platform (sessions remain active):
```java
// DeviceTokenService.remove(Long userId, Platform platform)
userAuthSessionRepository.clearNotificationTokenByUserIdAndPlatform(userId, platform);
```

Requires one more repository method:
```java
@Modifying
@Query("UPDATE UserAuthSessionEntity s SET s.notificationToken = NULL "
     + "WHERE s.user.id = :userId AND s.platform = :platform")
int clearNotificationTokenByUserIdAndPlatform(@Param("userId") Long userId,
                                               @Param("platform") Platform platform);
```

### Stale token cleanup (IssueNotificationListener)

FCM `sendMulticast()` returns stale token strings. Instead of:
```java
stale.forEach(deviceTokenRepository::deleteByToken);
```
Use:
```java
stale.forEach(userAuthSessionRepository::clearNotificationToken);
```

### Backward compatibility

All new columns are nullable. Existing sessions without a notification_token simply won't
receive push notifications — no error, no disruption. Clients re-register after upgrading.

---

## Deployment steps

1. **Build** the JAR: `./gradlew build`
2. **Run DB migration** on production:
   ```
   psql -h <host> -U <user> -d <db> -f scripts/migrate-notification-token-consolidation.sql
   ```
3. **Deploy** the new JAR.
4. **Smoke test**:
   - `POST /account/device-token` with `notification_token` + `platform` → session row in DB has both fields set.
   - `POST /account/device-token` again for the same platform from a different session → old session deactivated, new session has the token.
   - `DELETE /account/device-token` for a platform → `notification_token` cleared on those sessions.
   - Trigger issue approval → FCM push sent via `findActiveNotificationTokensByUserId`.
   - Confirm `device_tokens` table no longer exists.

---

## Rollback plan

1. Redeploy the previous JAR (old code will fail to find `device_tokens` — restore from DB
   snapshot first, or keep the snapshot ready before running the migration).
2. Apply the column-drop rollback SQL.
