# Push Notifications — Backend Setup

Firebase Cloud Messaging (FCM) is used to send push notifications to Android (and later iOS) devices.

---

## Files Added

| File | Purpose |
|---|---|
| `src/.../config/FirebaseConfig.java` | Initialises `FirebaseApp` and exposes a `FirebaseMessaging` Spring bean |
| `src/.../infra/notification/FcmSender.java` | Sends notifications — single device and multicast |

---

## Credentials Setup

1. Open Firebase Console → Project Settings → Service accounts
2. Click **Generate new private key** → download the JSON file
3. Rename it `firebase-key.json` and place it in the project root (next to `gcs-key.json`)
4. Add it to `.gitignore` — **never commit this file**

On the VM, set the path via environment variable instead of relying on the default filename:

```
FIREBASE_CREDENTIALS_PATH=/home/deploy/secrets/firebase-key.json
```

Add to `/etc/systemd/system/hashtaglocal-backend.service` under `[Service]`:
```ini
Environment="FIREBASE_CREDENTIALS_PATH=/home/deploy/secrets/firebase-key.json"
```

Then reload:
```bash
sudo systemctl daemon-reload
sudo systemctl restart hashtaglocal-backend
```

---

## Gradle Dependency

Already added to `build.gradle.kts`:
```groovy
implementation("com.google.firebase:firebase-admin:9.2.0")
```

---

## Sending a Notification

Inject `FcmSender` into any service and call:

```java
// Single device
fcmSender.send(deviceToken, "Issue resolved", "The pothole on MG Road is fixed.", Map.of(
    "type", "ISSUE_UPDATE",
    "issueId", issue.getId().toString()
));

// Multiple devices — returns stale tokens to delete from DB
List<String> stale = fcmSender.sendMulticast(tokens, "New issue nearby", "A pothole was reported 0.2km from you.", Map.of(
    "type", "NEARBY_ISSUE",
    "issueId", issue.getId().toString()
));
stale.forEach(deviceTokenRepository::deleteByToken);
```

---

## Notification Types

All `data` values must be strings (FCM requirement).

| `type` | Extra fields | App navigates to |
|---|---|---|
| `ISSUE_UPDATE` | `issueId` | Issue detail screen |
| `ISSUE_COMMENT` | `issueId` | Issue detail screen |
| `NEARBY_ISSUE` | `issueId` (optional) | Map tab |
| `KARMA_UPDATE` | — | Map tab |

---

## When to Trigger

| Event | Type | Recipient |
|---|---|---|
| Issue status changes | `ISSUE_UPDATE` | Reporter |
| New comment on issue | `ISSUE_COMMENT` | Reporter |
| New issue within X km | `NEARBY_ISSUE` | Users in radius |
| Karma earned/confirmed | `KARMA_UPDATE` | That user |

---

## Device Token Storage

The frontend calls `POST /account/device-token` after every login when the token has changed. The backend should store tokens per user:

```sql
CREATE TABLE device_tokens (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token       TEXT NOT NULL UNIQUE,
  platform    TEXT NOT NULL,   -- 'android' | 'ios'
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  updated_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_device_tokens_user_id ON device_tokens(user_id);
```

Stale tokens returned by `sendMulticast()` should be deleted immediately to keep the table clean.

---

## Testing Without a Backend

Use Firebase Console → Messaging → New campaign → **Firebase Notification messages**:

1. Fill in title + body
2. Additional options → Custom data → add `type` = `ISSUE_UPDATE`, `issueId` = `123`
3. Target → Single device → paste the FCM token from the app logs
4. Send
