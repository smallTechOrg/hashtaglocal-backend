package org.smalltech.hashtaglocal_backend.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

/**
 * The data payload of a push notification: the contract between the backend and the mobile app,
 * described in {@code NOTIFICATION_CONTRACT.md} at the repo root. The app checks these keys with
 * {@code parseNotificationData} ({@code models/NotificationData.ts} in
 * hashtaglocal-frontend-mobile) and uses them to decide which screen to open when the push is
 * tapped. Every push builds its data through this class, so a key can't be misspelled or go missing
 * on one sender only. Change this class, the doc and the app's type together.
 *
 * <p>FCM data values must be strings: {@link #toMap()} turns the set fields into the key/value map
 * that is sent and logged; unset (null) fields are left out.
 *
 * <p>Which fields each {@link Type} carries:
 *
 * <ul>
 *   <li>{@code BULLETIN}: bulletinId, localityId, date
 *   <li>{@code CHAT}: feedPostId, kind, localityId, hashtag, eventId (event posts only)
 *   <li>{@code ISSUE_DETAIL}: issueId, plus status, feedPostId, localityId, hashtag for the
 *       locality's "New issue / Issue resolved" push, or status, event for the reporter's "Issue is
 *       live"
 *   <li>{@code BROADCAST}: nothing else
 * </ul>
 *
 * Every sent push also carries {@code notificationLogId}, added just before sending.
 */
@Value
@Builder(toBuilder = true)
public class NotificationData {

  /** What the app opens when the push is tapped. Changing a name breaks the app. */
  public enum Type {
    /** Bulletin page. */
    BULLETIN,
    /** Chat, switched to {@code hashtag}'s locality. */
    CHAT,
    /** Issue page for {@code issueId}: any push about one issue. */
    ISSUE_DETAIL,
    /** Home (map) screen. */
    BROADCAST
  }

  /** What happened to the issue, for {@code ISSUE_DETAIL} pushes. */
  public enum IssueEvent {
    STATUS_CHANGE
  }

  // The data keys the app reads. Renaming one breaks the app.
  public static final String KEY_TYPE = "type";
  public static final String KEY_NOTIFICATION_LOG_ID = "notificationLogId";
  public static final String KEY_LOCALITY_ID = "localityId";
  public static final String KEY_HASHTAG = "hashtag";
  public static final String KEY_FEED_POST_ID = "feedPostId";
  public static final String KEY_KIND = "kind";
  public static final String KEY_EVENT_ID = "eventId";
  public static final String KEY_ISSUE_ID = "issueId";
  public static final String KEY_STATUS = "status";
  public static final String KEY_EVENT = "event";
  public static final String KEY_BULLETIN_ID = "bulletinId";
  public static final String KEY_DATE = "date";

  @NonNull Type type;

  /** {@code notification_log.id}, so the app can report the push was opened. */
  Long notificationLogId;

  Long localityId;
  String hashtag;

  Long feedPostId;
  FeedPostKind kind;
  Long eventId;

  Long issueId;
  IssueStatusModel status;
  IssueEvent event;

  Long bulletinId;
  LocalDate date;

  /** A copy with the log id set, for sending once the log row exists. */
  public NotificationData withNotificationLogId(Long id) {
    return toBuilder().notificationLogId(id).build();
  }

  /** The FCM data map: each set field as a string, keyed by the names the app reads. */
  public Map<String, String> toMap() {
    Map<String, String> map = new LinkedHashMap<>();
    map.put(KEY_TYPE, type.name());
    put(map, KEY_NOTIFICATION_LOG_ID, notificationLogId);
    put(map, KEY_LOCALITY_ID, localityId);
    put(map, KEY_HASHTAG, hashtag);
    put(map, KEY_FEED_POST_ID, feedPostId);
    put(map, KEY_KIND, kind);
    put(map, KEY_EVENT_ID, eventId);
    put(map, KEY_ISSUE_ID, issueId);
    put(map, KEY_STATUS, status);
    put(map, KEY_EVENT, event);
    put(map, KEY_BULLETIN_ID, bulletinId);
    put(map, KEY_DATE, date);
    return map;
  }

  private static void put(Map<String, String> map, String key, Object value) {
    if (value == null) {
      return;
    }
    map.put(key, value instanceof Enum<?> e ? e.name() : value.toString());
  }
}
