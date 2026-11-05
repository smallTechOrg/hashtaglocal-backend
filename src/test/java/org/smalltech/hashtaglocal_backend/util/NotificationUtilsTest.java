package org.smalltech.hashtaglocal_backend.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.config.NotificationConstants;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.util.NotificationUtils.PushMessage;

class NotificationUtilsTest {

  // ---- buildBody ----

  @Test
  void buildBody_blankFallsBack() {
    assertEquals(NotificationUtils.FALLBACK_SUMMARY, NotificationUtils.buildBody(null));
    assertEquals(NotificationUtils.FALLBACK_SUMMARY, NotificationUtils.buildBody("  "));
  }

  @Test
  void buildBody_keepsWholeSummaryWithinLimit() {
    assertEquals(
        "Light showers expected after 5 PM. Carry an umbrella.",
        NotificationUtils.buildBody("Light showers expected after 5 PM. Carry an umbrella."));
  }

  @Test
  void buildBody_stripsSurroundingWhitespace() {
    assertEquals("Sunny all day", NotificationUtils.buildBody("  Sunny all day \n"));
  }

  @Test
  void buildBody_exactlyAtLimitIsUntouched() {
    String text = "x".repeat(NotificationConstants.MAX_BODY_LENGTH);
    assertEquals(text, NotificationUtils.buildBody(text));
  }

  @Test
  void buildBody_longTextCutsOnWordWithEllipsis() {
    String body = NotificationUtils.buildBody("word, ".repeat(40));
    assertTrue(body.endsWith("…"));
    assertTrue(body.length() <= NotificationConstants.MAX_BODY_LENGTH);
    assertTrue(body.startsWith("word, word"));
    assertEquals("word", body.substring(body.length() - 5, body.length() - 1));
  }

  @Test
  void buildBody_longTextWithoutSpacesHardCuts() {
    String body = NotificationUtils.buildBody("x".repeat(400));
    assertTrue(body.endsWith("…"));
    assertEquals(NotificationConstants.MAX_BODY_LENGTH, body.length());
  }

  // ---- buildTitle ----

  @Test
  void buildTitle_prefixesLocalityName() {
    assertEquals("Daily Bulletin — Koramangala", NotificationUtils.buildTitle("Koramangala"));
  }

  @Test
  void buildTitle_cutsOverlongNameToTitleLimit() {
    String title = NotificationUtils.buildTitle("x".repeat(1000));
    assertEquals(NotificationConstants.MAX_TITLE_LENGTH, title.length());
    assertTrue(title.endsWith("…"));
  }

  // ---- buildBulletinMessage ----

  @Test
  void bulletinMessageCarriesTheBulletinIdentity() {
    Locality locality = chatLocality();
    BulletinEntity bulletin =
        BulletinEntity.builder()
            .id(7L)
            .locality(locality)
            .date(LocalDate.of(2026, 11, 5))
            .summary(Map.of("text", "Light showers expected after 5 PM."))
            .build();

    PushMessage message = NotificationUtils.buildBulletinMessage(bulletin);

    assertEquals("Daily Bulletin — Koramangala", message.title());
    assertEquals("Light showers expected after 5 PM.", message.body());
    assertEquals(
        Map.of("type", "BULLETIN", "bulletinId", "7", "localityId", "1", "date", "2026-11-05"),
        message.data().toMap());
  }

  // ---- chat push ----

  @Test
  void chatTitleIsTheLocalityName() {
    assertEquals("Koramangala", NotificationUtils.buildChatTitle(chatLocality()));
  }

  @Test
  void chatMessageNamesTheAuthorAndRoutesToTheChat() {
    UserEntity alice = UserEntity.builder().id(3L).username("alice").build();

    PushMessage message = NotificationUtils.buildChatMessage(textPost(alice, "Water is back"));

    assertEquals("Koramangala", message.title());
    assertEquals("alice: Water is back", message.body());
    assertEquals(
        Map.of(
            "type", "CHAT",
            "feedPostId", "42",
            "kind", "TEXT",
            "localityId", "1",
            "hashtag", "koramangala"),
        message.data().toMap());
  }

  @Test
  void adminPostSendsTextWithoutAuthorName() {
    UserEntity admin = UserEntity.builder().id(2L).username("root").role(UserRole.ADMIN).build();
    assertEquals(
        "Ward office closed on Monday",
        NotificationUtils.buildChatText(textPost(admin, "Ward office closed on Monday")));
  }

  @Test
  void linkWithTitleNamesIt() {
    FeedPostEntity post = linkPost("Metro line 3 opens next week");
    assertEquals(
        "ravi shared a link: Metro line 3 opens next week", NotificationUtils.buildChatText(post));
  }

  @Test
  void linkWithoutTitleYet() {
    assertEquals("ravi shared a link", NotificationUtils.buildChatText(linkPost(null)));
  }

  @Test
  void chatBodyIsCutAt230OnAWord() {
    String body =
        NotificationUtils.shorten(
            "ravi: " + "power cut again ".repeat(30),
            NotificationUtils.FALLBACK_CHAT_BODY,
            NotificationUtils.CHAT_MAX_BODY_LENGTH);
    assertTrue(body.length() <= 230);
    assertTrue(body.length() > NotificationConstants.MAX_BODY_LENGTH);
    assertTrue(body.endsWith("again…") || body.endsWith("cut…") || body.endsWith("power…"));
  }

  @Test
  void authorlessPostWithoutTextFallsBack() {
    FeedPostEntity post = textPost(null, null);
    assertEquals(
        NotificationUtils.FALLBACK_CHAT_BODY,
        NotificationUtils.shorten(
            NotificationUtils.buildChatText(post),
            NotificationUtils.FALLBACK_CHAT_BODY,
            NotificationUtils.CHAT_MAX_BODY_LENGTH));
  }

  // ---- issue push ----

  @Test
  void issueMessageRoutesToTheIssuePage() {
    IssueEntity issue = new IssueEntity();
    issue.setId(5L);
    issue.setDescription("Pothole near the bus stop");
    FeedPostEntity post =
        FeedPostEntity.builder()
            .id(43L)
            .locality(chatLocality())
            .kind(FeedPostKind.ISSUE_REF)
            .status(FeedPostStatus.PUBLISHED)
            .build();

    PushMessage opened = NotificationUtils.buildIssueMessage(post, issue, false);
    PushMessage resolved = NotificationUtils.buildIssueMessage(post, issue, true);

    assertEquals("New issue in Koramangala", opened.title());
    assertEquals("Issue resolved in Koramangala", resolved.title());
    assertEquals("Pothole near the bus stop", opened.body());
    assertEquals(
        Map.of(
            "type", "ISSUE_DETAIL",
            "issueId", "5",
            "status", "OPEN",
            "feedPostId", "43",
            "localityId", "1",
            "hashtag", "koramangala"),
        opened.data().toMap());
    assertEquals("RESOLVED", resolved.data().toMap().get("status"));
  }

  @Test
  void issueBodyIsCutAt230AndFallsBackWhenEmpty() {
    assertEquals(
        NotificationUtils.FALLBACK_ISSUE_BODY,
        NotificationUtils.shorten(
            null, NotificationUtils.FALLBACK_ISSUE_BODY, NotificationUtils.CHAT_MAX_BODY_LENGTH));
    String body =
        NotificationUtils.shorten(
            "Garbage not collected on 5th Main for 3 days ".repeat(10),
            NotificationUtils.FALLBACK_ISSUE_BODY,
            NotificationUtils.CHAT_MAX_BODY_LENGTH);
    assertTrue(body.length() <= 230);
    assertTrue(body.endsWith("…"));
  }

  // ---- fixtures ----

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
}
