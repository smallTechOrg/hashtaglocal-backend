package org.smalltech.hashtaglocal_backend.infra.notification;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FcmOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class FcmSender {

  private static final Logger log = LoggerFactory.getLogger(FcmSender.class);

  private final FirebaseMessaging firebaseMessaging;

  @Autowired
  public FcmSender(@Nullable FirebaseMessaging firebaseMessaging) {
    this.firebaseMessaging = firebaseMessaging;
  }

  /**
   * Result of a multicast send.
   *
   * @param tokenToMessageId token → FCM message ID for successful sends only
   * @param staleTokens UNREGISTERED/INVALID tokens — caller clears these from DB
   */
  public record MulticastResult(Map<String, String> tokenToMessageId, List<String> staleTokens) {
    public int successCount() {
      return tokenToMessageId.size();
    }
  }

  /** Sends to a single device. Returns the FCM message ID on success, null if FCM rejected. */
  @Nullable
  public String send(
      String token, String title, String body, Map<String, String> data, String analyticsLabel) {
    if (firebaseMessaging == null) {
      log.debug("FCM not configured; skipping notification to token {}", token);
      return null;
    }
    Message.Builder builder =
        Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .setFcmOptions(FcmOptions.withAnalyticsLabel(analyticsLabel))
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification.builder()
                            .setIcon("ic_notification")
                            .setColor("#256D1B")
                            .build())
                    .build());

    data.forEach(builder::putData);

    try {
      String messageId = firebaseMessaging.send(builder.build());
      log.info("FCM sent successfully to token {}", token);
      return messageId;
    } catch (FirebaseMessagingException e) {
      log.warn("FCM send failed for token {}: {}", token, e.getMessagingErrorCode());
      return null;
    }
  }

  /**
   * Sends to up to 500 devices in one FCM multicast call.
   *
   * @return {@link MulticastResult} with per-token message IDs and stale tokens to clean up
   */
  public MulticastResult sendMulticast(
      List<String> tokens,
      String title,
      String body,
      Map<String, String> data,
      String analyticsLabel) {

    if (firebaseMessaging == null) {
      log.debug("FCM not configured; skipping multicast to {} tokens", tokens.size());
      return new MulticastResult(Map.of(), List.of());
    }

    MulticastMessage.Builder builder =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .setFcmOptions(FcmOptions.withAnalyticsLabel(analyticsLabel))
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .setNotification(
                        AndroidNotification.builder()
                            .setIcon("ic_notification")
                            .setColor("#256D1B")
                            .build())
                    .build());

    data.forEach(builder::putData);

    Map<String, String> tokenToMessageId = new HashMap<>();
    List<String> staleTokens = new ArrayList<>();

    try {
      var response = firebaseMessaging.sendEachForMulticast(builder.build());
      List<SendResponse> responses = response.getResponses();
      for (int i = 0; i < responses.size(); i++) {
        SendResponse sr = responses.get(i);
        String token = tokens.get(i);
        if (sr.isSuccessful()) {
          tokenToMessageId.put(token, sr.getMessageId());
        } else {
          FirebaseMessagingException ex = sr.getException();
          MessagingErrorCode code = ex != null ? ex.getMessagingErrorCode() : null;
          if (code == MessagingErrorCode.UNREGISTERED
              || code == MessagingErrorCode.INVALID_ARGUMENT) {
            staleTokens.add(token);
          } else {
            log.warn("FCM send failed for token {}: {}", token, code);
          }
        }
      }
      log.info("FCM multicast: {}/{} sent successfully", tokenToMessageId.size(), tokens.size());
    } catch (FirebaseMessagingException e) {
      log.error("FCM multicast failed: {}", e.getMessage());
    }

    return new MulticastResult(tokenToMessageId, staleTokens);
  }
}
