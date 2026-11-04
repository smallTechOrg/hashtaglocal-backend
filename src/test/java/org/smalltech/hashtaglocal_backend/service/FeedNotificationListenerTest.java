package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.config.NotificationConstants;
import org.smalltech.hashtaglocal_backend.config.TimeConstants;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.NotificationLogEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.NotificationSubType;
import org.smalltech.hashtaglocal_backend.model.NotificationType;
import org.smalltech.hashtaglocal_backend.model.SourceRefType;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.NotificationLogRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class FeedNotificationListenerTest {

  private FeedPostRepository feedPostRepository;
  private BulletinRepository bulletinRepository;
  private UserAuthSessionRepository sessionRepository;
  private NotificationLogRepository logRepository;
  private FcmSender fcmSender;
  private FeedNotificationListener listener;

  @BeforeEach
  void setUp() {
    feedPostRepository = mock(FeedPostRepository.class);
    bulletinRepository = mock(BulletinRepository.class);
    sessionRepository = mock(UserAuthSessionRepository.class);
    logRepository = mock(NotificationLogRepository.class);
    fcmSender = mock(FcmSender.class);
    PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    listener =
        new FeedNotificationListener(
            feedPostRepository,
            bulletinRepository,
            sessionRepository,
            logRepository,
            fcmSender,
            new ObjectMapper(),
            txManager);
    ReflectionTestUtils.setField(listener, "bulletinNotificationEnabled", true);
    ReflectionTestUtils.setField(listener, "chatNotificationEnabled", true);
  }

  private BulletinEntity bulletin() {
    Locality locality = new Locality();
    locality.setId(1L);
    locality.setName("Koramangala");
    return BulletinEntity.builder()
        .id(7L)
        .locality(locality)
        .date(LocalDate.now(TimeConstants.APP_ZONE))
        .summary(Map.of("text", "Light showers expected after 5 PM. Carry an umbrella."))
        .build();
  }

  // ---- buildBody ----

  @Test
  void buildBody_blankFallsBack() {
    assertEquals(
        FeedNotificationListener.FALLBACK_SUMMARY, FeedNotificationListener.buildBody(null));
    assertEquals(
        FeedNotificationListener.FALLBACK_SUMMARY, FeedNotificationListener.buildBody("  "));
  }

  @Test
  void buildBody_keepsWholeSummaryWithinLimit() {
    assertEquals(
        "Light showers expected after 5 PM. Carry an umbrella.",
        FeedNotificationListener.buildBody(
            "Light showers expected after 5 PM. Carry an umbrella."));
  }

  @Test
  void buildBody_stripsSurroundingWhitespace() {
    assertEquals("Sunny all day", FeedNotificationListener.buildBody("  Sunny all day \n"));
  }

  @Test
  void buildBody_exactlyAtLimitIsUntouched() {
    String text = "x".repeat(NotificationConstants.MAX_BODY_LENGTH);
    assertEquals(text, FeedNotificationListener.buildBody(text));
  }

  @Test
  void buildBody_longTextCutsOnWordWithEllipsis() {
    String body = FeedNotificationListener.buildBody("word, ".repeat(40));
    assertTrue(body.endsWith("…"));
    assertTrue(body.length() <= NotificationConstants.MAX_BODY_LENGTH);
    assertTrue(body.startsWith("word, word"));
    assertEquals("word", body.substring(body.length() - 5, body.length() - 1));
  }

  @Test
  void buildBody_longTextWithoutSpacesHardCuts() {
    String body = FeedNotificationListener.buildBody("x".repeat(400));
    assertTrue(body.endsWith("…"));
    assertEquals(NotificationConstants.MAX_BODY_LENGTH, body.length());
  }

  // ---- buildTitle ----

  @Test
  void buildTitle_prefixesLocalityName() {
    assertEquals(
        "Daily Bulletin — Koramangala", FeedNotificationListener.buildTitle("Koramangala"));
  }

  @Test
  void buildTitle_cutsOverlongNameToTitleLimit() {
    String title = FeedNotificationListener.buildTitle("x".repeat(1000));
    assertEquals(NotificationConstants.MAX_TITLE_LENGTH, title.length());
    assertTrue(title.endsWith("…"));
  }

  // ---- notifyLocality ----

  @Test
  void skipsWhenAlreadyLogged() {
    when(logRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
            NotificationType.AUTO_CHAT,
            NotificationSubType.CHAT_BULLETIN,
            SourceRefType.BULLETIN,
            7L))
        .thenReturn(true);

    listener.notifyLocality(7L);

    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
    verify(logRepository, never()).saveAndFlush(any());
  }

  @Test
  void missingBulletinSendsNothingAndWritesNoLogRow() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.empty());

    listener.notifyLocality(7L);

    verify(sessionRepository, never()).findActiveNotificationTokensByLocalityId(anyLong());
    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void oldBulletinSendsNothingAndWritesNoLogRow() {
    BulletinEntity old = bulletin();
    old.setDate(LocalDate.now(TimeConstants.APP_ZONE).minusDays(1));
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(old));

    listener.notifyLocality(7L);

    verify(sessionRepository, never()).findActiveNotificationTokensByLocalityId(anyLong());
    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void bulletinWithoutSummaryStillSendsWithFallbackSummary() {
    BulletinEntity noSummary = bulletin();
    noSummary.setSummary(null);
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(noSummary));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any()))
        .thenReturn(NotificationLogEntity.builder().id(99L).build());
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of(), List.of()));

    listener.notifyLocality(7L);

    verify(fcmSender)
        .sendMulticast(
            anyList(),
            eq("Daily Bulletin — Koramangala"),
            eq(FeedNotificationListener.FALLBACK_SUMMARY),
            anyMap(),
            anyString());
  }

  @Test
  void skipsQuietlyWhenNoTokens() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of());

    listener.notifyLocality(7L);

    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void duplicateClaimSendsNothing() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    listener.notifyLocality(7L);

    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void sendsInBatchesClearsStaleTokensAndRecordsCounts() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    List<String> tokens = new ArrayList<>();
    for (int i = 0; i < 501; i++) {
      tokens.add("t" + i);
    }
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(tokens);
    NotificationLogEntity saved = NotificationLogEntity.builder().id(99L).build();
    when(logRepository.saveAndFlush(any())).thenReturn(saved);
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of("a", "m1"), List.of("stale")));

    listener.notifyLocality(7L);

    verify(fcmSender, org.mockito.Mockito.times(2))
        .sendMulticast(
            anyList(),
            eq("Daily Bulletin — Koramangala"),
            eq("Light showers expected after 5 PM. Carry an umbrella."),
            anyMap(),
            eq("bulletin_system"));
    verify(sessionRepository, org.mockito.Mockito.times(2)).clearNotificationToken("stale");
    assertEquals(501, saved.getRecipientCount());
    assertEquals(2, saved.getSuccessCount());
  }

  @Test
  void claimRowFollowsDocumentedAccessPattern() {
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin()));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    when(logRepository.saveAndFlush(any()))
        .thenReturn(NotificationLogEntity.builder().id(99L).build());
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of(), List.of()));

    listener.notifyLocality(7L);

    org.mockito.ArgumentCaptor<NotificationLogEntity> captor =
        org.mockito.ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(logRepository).saveAndFlush(captor.capture());
    NotificationLogEntity row = captor.getValue();
    assertEquals(NotificationType.AUTO_CHAT, row.getType());
    assertEquals(NotificationSubType.CHAT_BULLETIN, row.getSubType());
    assertEquals(SourceRefType.BULLETIN, row.getSourceRefType());
    assertEquals(7L, row.getSourceRefId());
  }

  // ---- chat posts (everything that is not a bulletin) ----

  private Locality chatLocality() {
    Locality locality = new Locality();
    locality.setId(1L);
    locality.setName("Koramangala");
    locality.setHashtag("koramangala");
    return locality;
  }

  private FeedPostEntity textPost(UserEntity author, String text) {
    FeedPostEntity post =
        FeedPostEntity.builder()
            .id(42L)
            .locality(chatLocality())
            .author(author)
            .kind(FeedPostKind.TEXT)
            .status(FeedPostStatus.PUBLISHED)
            .build();
    post.setContent(FeedPostContentEntity.builder().post(post).text(text).build());
    return post;
  }

  private FeedPostEntity issuePost(IssueStatusModel status) {
    IssueEntity issue = new IssueEntity();
    issue.setId(5L);
    issue.setDescription("Pothole near the bus stop");
    issue.setUserEntity(UserEntity.builder().id(8L).username("ravi").build());
    issue.setStatus(status);
    FeedPostEntity post =
        FeedPostEntity.builder()
            .id(43L)
            .locality(chatLocality())
            .kind(FeedPostKind.ISSUE_REF)
            .status(FeedPostStatus.PUBLISHED)
            .build();
    post.setContent(FeedPostContentEntity.builder().post(post).issue(issue).build());
    return post;
  }

  private void stubSend() {
    when(logRepository.saveAndFlush(any()))
        .thenReturn(NotificationLogEntity.builder().id(99L).build());
    when(fcmSender.sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString()))
        .thenReturn(new MulticastResult(Map.of("t2", "m1"), List.of()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void userMessageIsPushedToLocalityExceptItsAuthor() {
    UserEntity alice = UserEntity.builder().id(3L).username("alice").build();
    when(feedPostRepository.findByIdForNotification(42L))
        .thenReturn(Optional.of(textPost(alice, "Water supply is back on 5th main")));
    when(sessionRepository.findActiveNotificationTokensByLocalityIdExcludingUser(1L, 3L))
        .thenReturn(List.of("t2", "t3"));
    stubSend();

    listener.notifyPost(42L);

    org.mockito.ArgumentCaptor<List<String>> tokens =
        org.mockito.ArgumentCaptor.forClass(List.class);
    org.mockito.ArgumentCaptor<Map<String, String>> data =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(fcmSender)
        .sendMulticast(
            tokens.capture(),
            eq("Koramangala"),
            eq("alice: Water supply is back on 5th main"),
            data.capture(),
            eq("chat_system"));
    assertEquals(List.of("t2", "t3"), tokens.getValue());
    assertEquals("CHAT", data.getValue().get("type"));
    assertEquals("42", data.getValue().get("feedPostId"));
    assertEquals("TEXT", data.getValue().get("kind"));
    assertEquals("99", data.getValue().get("notificationLogId"));

    org.mockito.ArgumentCaptor<NotificationLogEntity> row =
        org.mockito.ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(logRepository).saveAndFlush(row.capture());
    assertEquals(NotificationType.AUTO_CHAT, row.getValue().getType());
    assertEquals(NotificationSubType.CHAT_POST, row.getValue().getSubType());
    assertEquals(SourceRefType.FEED_POST, row.getValue().getSourceRefType());
    assertEquals(42L, row.getValue().getSourceRefId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void openedIssueIsPushedAsIssueUpdateWithoutTheReporter() {
    when(feedPostRepository.findByIdForNotification(43L))
        .thenReturn(Optional.of(issuePost(IssueStatusModel.OPEN)));
    when(sessionRepository.findActiveNotificationTokensByLocalityIdExcludingUser(1L, 8L))
        .thenReturn(List.of("t2"));
    stubSend();

    listener.notifyPost(43L);

    org.mockito.ArgumentCaptor<List<String>> tokens =
        org.mockito.ArgumentCaptor.forClass(List.class);
    org.mockito.ArgumentCaptor<Map<String, String>> data =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(fcmSender)
        .sendMulticast(
            tokens.capture(),
            eq("New issue in Koramangala"),
            eq("Pothole near the bus stop"),
            data.capture(),
            eq("issue_update_system"));
    assertEquals(List.of("t2"), tokens.getValue());
    assertEquals("ISSUE_UPDATE", data.getValue().get("type"));
    assertEquals("5", data.getValue().get("issueId"));
    assertEquals("OPEN", data.getValue().get("status"));

    org.mockito.ArgumentCaptor<NotificationLogEntity> row =
        org.mockito.ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(logRepository).saveAndFlush(row.capture());
    assertEquals(NotificationType.AUTO_CHAT, row.getValue().getType());
    assertEquals(NotificationSubType.CHAT_ISSUE, row.getValue().getSubType());
    assertEquals(SourceRefType.ISSUE, row.getValue().getSourceRefType());
    assertEquals(5L, row.getValue().getSourceRefId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void resolvedIssueIsPushedAsIssueResolvedIncludingTheReporter() {
    when(feedPostRepository.findByIdForNotification(43L))
        .thenReturn(Optional.of(issuePost(IssueStatusModel.RESOLVED)));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L))
        .thenReturn(List.of("ravi-phone", "t2"));
    stubSend();

    listener.notifyPost(43L);

    org.mockito.ArgumentCaptor<List<String>> tokens =
        org.mockito.ArgumentCaptor.forClass(List.class);
    org.mockito.ArgumentCaptor<Map<String, String>> data =
        org.mockito.ArgumentCaptor.forClass(Map.class);
    verify(fcmSender)
        .sendMulticast(
            tokens.capture(),
            eq("Issue resolved in Koramangala"),
            eq("Pothole near the bus stop"),
            data.capture(),
            anyString());
    assertEquals(List.of("ravi-phone", "t2"), tokens.getValue());
    assertEquals("RESOLVED", data.getValue().get("status"));
    org.mockito.ArgumentCaptor<NotificationLogEntity> row =
        org.mockito.ArgumentCaptor.forClass(NotificationLogEntity.class);
    verify(logRepository).saveAndFlush(row.capture());
    assertEquals(NotificationSubType.CHAT_ISSUE_RESOLVED, row.getValue().getSubType());
    assertEquals(SourceRefType.ISSUE, row.getValue().getSourceRefType());
    assertEquals(5L, row.getValue().getSourceRefId());
  }

  @Test
  @SuppressWarnings("unchecked")
  void sharedIssuePostLeavesOutOnlyTheSharer() {
    FeedPostEntity post = issuePost(IssueStatusModel.OPEN);
    post.setAuthor(UserEntity.builder().id(9L).username("asha").build());
    when(feedPostRepository.findByIdForNotification(43L)).thenReturn(Optional.of(post));
    when(sessionRepository.findActiveNotificationTokensByLocalityIdExcludingUser(1L, 9L))
        .thenReturn(List.of("ravi-phone", "t2"));
    stubSend();

    listener.notifyPost(43L);

    org.mockito.ArgumentCaptor<List<String>> tokens =
        org.mockito.ArgumentCaptor.forClass(List.class);
    verify(fcmSender)
        .sendMulticast(tokens.capture(), anyString(), anyString(), anyMap(), anyString());
    assertEquals(List.of("ravi-phone", "t2"), tokens.getValue());
  }

  @Test
  void regeneratedIssuePostDoesNotPushAgain() {
    when(feedPostRepository.findByIdForNotification(43L))
        .thenReturn(Optional.of(issuePost(IssueStatusModel.RESOLVED)));
    when(logRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
            NotificationType.AUTO_CHAT,
            NotificationSubType.CHAT_ISSUE_RESOLVED,
            SourceRefType.ISSUE,
            5L))
        .thenReturn(true);

    listener.notifyPost(43L);

    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void issueBodyIsCutAt230AndFallsBackWhenEmpty() {
    assertEquals(
        FeedNotificationListener.FALLBACK_ISSUE_BODY,
        FeedNotificationListener.shorten(
            null,
            FeedNotificationListener.FALLBACK_ISSUE_BODY,
            FeedNotificationListener.CHAT_MAX_BODY_LENGTH));
    String body =
        FeedNotificationListener.shorten(
            "Garbage not collected on 5th Main for 3 days ".repeat(10),
            FeedNotificationListener.FALLBACK_ISSUE_BODY,
            FeedNotificationListener.CHAT_MAX_BODY_LENGTH);
    assertTrue(body.length() <= 230);
    assertTrue(body.endsWith("…"));
  }

  @Test
  void chatPostAlreadyNotifiedIsSkipped() {
    when(feedPostRepository.findByIdForNotification(42L))
        .thenReturn(Optional.of(textPost(null, "hello")));
    when(logRepository.existsByTypeAndSubTypeAndSourceRefTypeAndSourceRefId(
            NotificationType.AUTO_CHAT,
            NotificationSubType.CHAT_POST,
            SourceRefType.FEED_POST,
            42L))
        .thenReturn(true);

    listener.notifyPost(42L);

    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void postNoLongerPublishedIsNotPushed() {
    FeedPostEntity hidden = textPost(null, "hello");
    hidden.setStatus(FeedPostStatus.ADMIN_HIDDEN);
    when(feedPostRepository.findByIdForNotification(42L)).thenReturn(Optional.of(hidden));

    listener.notifyPost(42L);

    verify(sessionRepository, never()).findActiveNotificationTokensByLocalityId(anyLong());
    verify(logRepository, never()).saveAndFlush(any());
  }

  @Test
  void chatPushesCanBeSwitchedOffIndependently() {
    ReflectionTestUtils.setField(listener, "chatNotificationEnabled", false);
    when(feedPostRepository.findByIdForNotification(42L))
        .thenReturn(Optional.of(textPost(null, "hello")));

    listener.notifyPost(42L);

    verify(logRepository, never()).saveAndFlush(any());
    verify(fcmSender, never())
        .sendMulticast(anyList(), anyString(), anyString(), anyMap(), anyString());
  }

  @Test
  void bulletinPostRoutesToTheBulletinPush() {
    BulletinEntity bulletin = bulletin();
    FeedPostEntity post =
        FeedPostEntity.builder()
            .id(44L)
            .locality(bulletin.getLocality())
            .kind(FeedPostKind.BULLETIN)
            .status(FeedPostStatus.PUBLISHED)
            .build();
    post.setContent(FeedPostContentEntity.builder().post(post).bulletin(bulletin).build());
    when(feedPostRepository.findByIdForNotification(44L)).thenReturn(Optional.of(post));
    when(bulletinRepository.findByIdWithLocality(7L)).thenReturn(Optional.of(bulletin));
    when(sessionRepository.findActiveNotificationTokensByLocalityId(1L)).thenReturn(List.of("t1"));
    stubSend();

    listener.notifyPost(44L);

    verify(fcmSender)
        .sendMulticast(
            anyList(),
            eq("Daily Bulletin — Koramangala"),
            anyString(),
            anyMap(),
            eq("bulletin_system"));
  }

  @Test
  void chatTitleIsTheLocalityName() {
    assertEquals("Koramangala", FeedNotificationListener.buildChatTitle(chatLocality()));
  }

  @Test
  void adminPostSendsTextWithoutAuthorName() {
    UserEntity admin = UserEntity.builder().id(2L).username("root").role(UserRole.ADMIN).build();
    assertEquals(
        "Ward office closed on Monday",
        FeedNotificationListener.buildChatText(textPost(admin, "Ward office closed on Monday")));
  }

  @Test
  void linkWithTitleNamesIt() {
    FeedPostEntity post = linkPost("Metro line 3 opens next week");
    assertEquals(
        "ravi shared a link: Metro line 3 opens next week",
        FeedNotificationListener.buildChatText(post));
  }

  @Test
  void linkWithoutTitleYet() {
    assertEquals("ravi shared a link", FeedNotificationListener.buildChatText(linkPost(null)));
  }

  @Test
  void chatBodyIsCutAt230OnAWord() {
    String body =
        FeedNotificationListener.shorten(
            "ravi: " + "power cut again ".repeat(30),
            FeedNotificationListener.FALLBACK_CHAT_BODY,
            FeedNotificationListener.CHAT_MAX_BODY_LENGTH);
    assertTrue(body.length() <= 230);
    assertTrue(body.length() > NotificationConstants.MAX_BODY_LENGTH);
    assertTrue(body.endsWith("again…") || body.endsWith("cut…") || body.endsWith("power…"));
  }

  private FeedPostEntity linkPost(String title) {
    FeedPostEntity post =
        FeedPostEntity.builder()
            .id(45L)
            .locality(chatLocality())
            .author(UserEntity.builder().id(8L).username("ravi").build())
            .kind(FeedPostKind.LINK)
            .status(FeedPostStatus.PUBLISHED)
            .build();
    post.setContent(
        FeedPostContentEntity.builder()
            .post(post)
            .url("https://example.com/metro")
            .title(title)
            .build());
    return post;
  }

  @Test
  void authorlessPostWithoutTextFallsBack() {
    FeedPostEntity post = textPost(null, null);
    assertEquals(
        FeedNotificationListener.FALLBACK_CHAT_BODY,
        FeedNotificationListener.shorten(
            FeedNotificationListener.buildChatText(post),
            FeedNotificationListener.FALLBACK_CHAT_BODY,
            FeedNotificationListener.CHAT_MAX_BODY_LENGTH));
  }
}
