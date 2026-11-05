# Push notification data contract

Every push the backend sends carries a small `data` map that the app reads when the push is tapped, to decide which screen to open. This doc is the contract for that map. It is defined in code on both sides:

- **Backend:** [`NotificationData.java`](src/main/java/org/smalltech/hashtaglocal_backend/model/NotificationData.java). Every sender builds its data through it.
- **App:** [`models/NotificationData.ts`](https://github.com/smallTechOrg/hashtaglocal-frontend-mobile/blob/staging/models/NotificationData.ts) in hashtaglocal-frontend-mobile. `parseNotificationData` checks every incoming push against it.

**Changing the contract:** update this doc, `NotificationData.java` and `NotificationData.ts` together, and ship the app before the backend starts sending anything new.

## Rules

- All values are strings (FCM only carries strings). Ids are numbers written as strings.
- A key that has no value is left out, not sent empty.
- `type` is always present. The app ignores a push whose `type` it doesn't know.
- Every push also carries `notificationLogId` (the `notification_log` row id), which the app reports to analytics when the push is opened.

## Types

| `type` | Tap opens | Required | Optional | Sent by |
|---|---|---|---|---|
| `BULLETIN` | Bulletin page | | `bulletinId`, `localityId`, `date` (`YYYY-MM-DD`) | Daily bulletin push |
| `CHAT` | Chat, switched to `hashtag`'s locality | | `feedPostId`, `kind`, `localityId`, `hashtag`, `eventId` | Chat post push; ops CHAT broadcast (`type` only) |
| `ISSUE_DETAIL` | Issue page for `issueId` | `issueId` | `status`, `event`, `feedPostId`, `localityId`, `hashtag` | Locality "New issue / Issue resolved" push; reporter's "Issue is live"; ops issue broadcast |
| `BROADCAST` | Home (map) | | | Ops broadcast |

## Fields

| Key | Meaning | Values |
|---|---|---|
| `type` | What the app opens | `BULLETIN`, `CHAT`, `ISSUE_DETAIL`, `BROADCAST` |
| `notificationLogId` | `notification_log.id` | number |
| `localityId` | `localities.id` | number |
| `hashtag` | Locality hashtag, without `#` | e.g. `koramangala` |
| `feedPostId` | `feed_posts.id` | number |
| `kind` | Feed post kind | `TEXT`, `LINK`, `MEDIA`, `EVENT_REF`, … |
| `eventId` | `events.id`, for event posts | number |
| `issueId` | `issues.id` | number |
| `status` | Issue status | `OPEN`, `RESOLVED` |
| `event` | What happened to the issue | `STATUS_CHANGE` |
| `bulletinId` | `bulletins.id` | number |
| `date` | Bulletin date | `YYYY-MM-DD` |

## Examples

```json
{ "type": "CHAT", "feedPostId": "11", "kind": "TEXT", "localityId": "3", "hashtag": "koramangala", "notificationLogId": "42" }
{ "type": "ISSUE_DETAIL", "issueId": "5", "status": "RESOLVED", "feedPostId": "12", "localityId": "3", "hashtag": "koramangala", "notificationLogId": "43" }
{ "type": "BULLETIN", "bulletinId": "7", "localityId": "3", "date": "2026-11-05", "notificationLogId": "44" }
```
