package org.smalltech.hashtaglocal_backend.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NotificationDataTest {

  @Test
  void toMapSendsOnlyTheFieldsThatAreSet() {
    NotificationData data =
        NotificationData.builder().type(NotificationData.Type.BROADCAST).build();

    assertEquals(Map.of("type", "BROADCAST"), data.toMap());
  }

  @Test
  void toMapWritesEveryValueAsTheStringTheAppReads() {
    NotificationData data =
        NotificationData.builder()
            .type(NotificationData.Type.CHAT)
            .notificationLogId(99L)
            .localityId(1L)
            .hashtag("koramangala")
            .feedPostId(42L)
            .kind(FeedPostKind.EVENT_REF)
            .eventId(12L)
            .issueId(5L)
            .status(IssueStatusModel.RESOLVED)
            .event(NotificationData.IssueEvent.STATUS_CHANGE)
            .bulletinId(7L)
            .date(LocalDate.of(2026, 11, 5))
            .build();

    assertEquals(
        Map.ofEntries(
            Map.entry("type", "CHAT"),
            Map.entry("notificationLogId", "99"),
            Map.entry("localityId", "1"),
            Map.entry("hashtag", "koramangala"),
            Map.entry("feedPostId", "42"),
            Map.entry("kind", "EVENT_REF"),
            Map.entry("eventId", "12"),
            Map.entry("issueId", "5"),
            Map.entry("status", "RESOLVED"),
            Map.entry("event", "STATUS_CHANGE"),
            Map.entry("bulletinId", "7"),
            Map.entry("date", "2026-11-05")),
        data.toMap());
  }

  @Test
  void withNotificationLogIdReturnsACopy() {
    NotificationData data =
        NotificationData.builder().type(NotificationData.Type.ISSUE_DETAIL).issueId(5L).build();

    NotificationData sent = data.withNotificationLogId(99L);

    assertEquals("99", sent.toMap().get("notificationLogId"));
    assertEquals("5", sent.toMap().get("issueId"));
    assertNull(data.getNotificationLogId());
  }

  @Test
  void typeIsRequired() {
    assertThrows(NullPointerException.class, () -> NotificationData.builder().build());
  }
}
