package org.smalltech.hashtaglocal_backend.util;

import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_BODY_LENGTH;
import static org.smalltech.hashtaglocal_backend.config.NotificationConstants.MAX_TITLE_LENGTH;

import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.model.NotificationData;
import org.smalltech.hashtaglocal_backend.model.UserRole;

/**
 * Builds what a chat push says: its title, its body and the data the app routes on, for the daily
 * bulletin, chat post and issue update pushes. Only text and data are built here; deciding who gets
 * a push and sending it stays in {@code FeedNotificationListener}.
 */
public final class NotificationUtils {

  private NotificationUtils() {}

  /** What is pushed: the visible title and body plus the data the app routes on. */
  public record PushMessage(String title, String body, NotificationData data) {}

  public static final String TITLE_PREFIX = "Daily Bulletin — ";

  /** Used only when the bulletin exists but has no summary text (e.g. an admin-created row). */
  public static final String FALLBACK_SUMMARY = "Today's weather update is ready.";

  /** Chat push body when a post has nothing better to show. */
  public static final String FALLBACK_CHAT_BODY = "New post in the chat";

  /** Issue push body when the issue has no description. */
  public static final String FALLBACK_ISSUE_BODY = "Tap to see the details";

  /** Longest chat or issue push body; longer text is cut at a word boundary with an ellipsis. */
  public static final int CHAT_MAX_BODY_LENGTH = 230;

  // ---------------------------------------------------------------------------------------------
  // Bulletin push
  // ---------------------------------------------------------------------------------------------

  /** "Daily Bulletin — {locality}" with the summary as the body; the app opens the bulletin. */
  public static PushMessage buildBulletinMessage(BulletinEntity bulletin) {
    Object summaryText = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;

    NotificationData data =
        NotificationData.builder()
            .type(NotificationData.Type.BULLETIN)
            .bulletinId(bulletin.getId())
            .localityId(bulletin.getLocality().getId())
            .date(bulletin.getDate())
            .build();

    return new PushMessage(
        buildTitle(bulletin.getLocality().getName()),
        buildBody(summaryText != null ? summaryText.toString() : null),
        data);
  }

  /** "Daily Bulletin — {locality}", cut with an ellipsis if it would exceed the title limit. */
  public static String buildTitle(String localityName) {
    return shortenTitle(TITLE_PREFIX + localityName);
  }

  /**
   * The whole summary, truncated at a word boundary with an ellipsis when it exceeds {@code
   * MAX_BODY_LENGTH}. Deliberately not cut at the first sentence: sentences can be far too long or
   * far too short to make a useful push body on their own.
   */
  public static String buildBody(String text) {
    return shorten(text, FALLBACK_SUMMARY, MAX_BODY_LENGTH);
  }

  // ---------------------------------------------------------------------------------------------
  // Chat push (every non-bulletin, non-issue post)
  // ---------------------------------------------------------------------------------------------

  /** Locality name as the title, "{author}: {text}" as the body; the app opens that chat. */
  public static PushMessage buildChatMessage(FeedPostEntity post) {
    Locality locality = post.getLocality();
    FeedPostContentEntity content = post.getContent();

    NotificationData data =
        NotificationData.builder()
            .type(NotificationData.Type.CHAT)
            .feedPostId(post.getId())
            .kind(post.getKind())
            .localityId(locality.getId())
            .hashtag(locality.getHashtag())
            .eventId(
                content != null && content.getEvent() != null ? content.getEvent().getId() : null)
            .build();

    return new PushMessage(
        buildChatTitle(locality),
        shorten(buildChatText(post), FALLBACK_CHAT_BODY, CHAT_MAX_BODY_LENGTH),
        data);
  }

  /**
   * The locality's name, e.g. "Koramangala" (its hashtag if it has no name), cut with an ellipsis
   * if it would exceed the title limit.
   */
  public static String buildChatTitle(Locality locality) {
    return shortenTitle(localityName(locality));
  }

  /**
   * The full push text for a chat post, e.g. "alice: hello" or "ravi shared a link: {title}". Admin
   * posts show just the text, with no name. Returns null when there is nothing to show.
   */
  public static String buildChatText(FeedPostEntity post) {
    FeedPostContentEntity c = post.getContent();
    String author = displayName(post.getAuthor());
    String text = c != null ? firstNonBlank(c.getText()) : null;

    return switch (post.getKind()) {
      case LINK -> {
        String title = c != null ? firstNonBlank(c.getTitle()) : null;
        if (author == null) {
          yield firstNonBlank(title, text);
        }
        yield title != null ? author + " shared a link: " + title : author + " shared a link";
      }
      case EVENT_REF -> {
        EventEntity event = c != null ? c.getEvent() : null;
        String name = event != null ? firstNonBlank(event.getName()) : null;
        yield withAuthor(author, firstNonBlank(text, name != null ? "New event: " + name : null));
      }
      case MEDIA -> {
        boolean video =
            c != null && c.getMedia() != null && c.getMedia().getType() == MediaTypeModel.VIDEO;
        yield withAuthor(author, firstNonBlank(text, video ? "Shared a video" : "Shared a photo"));
      }
      default -> withAuthor(author, firstNonBlank(text, c != null ? c.getTitle() : null));
    };
  }

  // ---------------------------------------------------------------------------------------------
  // Issue update push (ISSUE_REF posts)
  // ---------------------------------------------------------------------------------------------

  /**
   * "New issue in …" or "Issue resolved in …" with the issue description as the body; the app opens
   * the issue page.
   */
  public static PushMessage buildIssueMessage(
      FeedPostEntity post, IssueEntity issue, boolean resolved) {
    Locality locality = post.getLocality();

    NotificationData data =
        NotificationData.builder()
            .type(NotificationData.Type.ISSUE_DETAIL)
            .issueId(issue.getId())
            .status(resolved ? IssueStatusModel.RESOLVED : IssueStatusModel.OPEN)
            .feedPostId(post.getId())
            .localityId(locality.getId())
            .hashtag(locality.getHashtag())
            .build();

    return new PushMessage(
        buildIssueTitle(locality, resolved),
        shorten(issue.getDescription(), FALLBACK_ISSUE_BODY, CHAT_MAX_BODY_LENGTH),
        data);
  }

  /** "New issue in {locality}" or "Issue resolved in {locality}". */
  public static String buildIssueTitle(Locality locality, boolean resolved) {
    return shortenTitle(
        (resolved ? "Issue resolved in " : "New issue in ") + localityName(locality));
  }

  // ---------------------------------------------------------------------------------------------
  // Text helpers
  // ---------------------------------------------------------------------------------------------

  /** {@code text} cut to {@code maxLength} on a word boundary; {@code fallback} if blank. */
  public static String shorten(String text, String fallback, int maxLength) {
    if (text == null || text.isBlank()) {
      return fallback;
    }
    String trimmed = text.strip();
    if (trimmed.length() <= maxLength) {
      return trimmed;
    }
    int cut = trimmed.lastIndexOf(' ', maxLength - 1);
    String head = trimmed.substring(0, cut > 0 ? cut : maxLength - 1);
    int last = head.length();
    while (last > 0 && " ,;:-".indexOf(head.charAt(last - 1)) >= 0) {
      last--;
    }
    return head.substring(0, last) + "…";
  }

  private static String shortenTitle(String title) {
    if (title.length() <= MAX_TITLE_LENGTH) {
      return title;
    }
    return title.substring(0, MAX_TITLE_LENGTH - 1) + "…";
  }

  private static String localityName(Locality locality) {
    String name = firstNonBlank(locality.getName(), locality.getHashtag());
    return name != null ? name : "";
  }

  /** The name to show for a post's author: none for admins (their posts read as official). */
  private static String displayName(UserEntity user) {
    if (user == null || user.getRole() == UserRole.ADMIN) {
      return null;
    }
    return firstNonBlank(user.getUsername());
  }

  /** {@code "{author}: {text}"}; the text alone when there is no author name. */
  private static String withAuthor(String author, String text) {
    if (author == null || text == null) {
      return text;
    }
    return author + ": " + text;
  }

  private static String firstNonBlank(String... values) {
    for (String v : values) {
      if (v != null && !v.isBlank()) {
        return v.strip();
      }
    }
    return null;
  }
}
