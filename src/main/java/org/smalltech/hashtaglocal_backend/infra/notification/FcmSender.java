package org.smalltech.hashtaglocal_backend.infra.notification;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
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

  /** Sends to a single device. Silently drops send-level errors (token already logged). */
  public void send(String token, String title, String body, Map<String, String> data) {
    if (firebaseMessaging == null) {
      log.debug("FCM not configured; skipping notification to token {}", token);
      return;
    }
    Message.Builder builder =
        Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .setAndroidConfig(
                AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());

    data.forEach(builder::putData);

    try {
      firebaseMessaging.send(builder.build());
      log.info("FCM sent successfully to token {}", token);
    } catch (FirebaseMessagingException e) {
      log.warn("FCM send failed for token {}: {}", token, e.getMessagingErrorCode());
    }
  }

  /**
   * Sends to up to 500 devices in one FCM multicast call.
   *
   * @return tokens that FCM reports as unregistered/invalid — caller should delete these from DB
   */
  public List<String> sendMulticast(
      List<String> tokens, String title, String body, Map<String, String> data) {

    MulticastMessage.Builder builder =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .setAndroidConfig(
                AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build());

    data.forEach(builder::putData);

    if (firebaseMessaging == null) {
      log.debug("FCM not configured; skipping multicast to {} tokens", tokens.size());
      return List.of();
    }
    List<String> staleTokens = new ArrayList<>();
    try {
      var response = firebaseMessaging.sendEachForMulticast(builder.build());
      List<SendResponse> responses = response.getResponses();
      int successCount = 0;
      for (int i = 0; i < responses.size(); i++) {
        SendResponse sr = responses.get(i);
        if (sr.isSuccessful()) {
          successCount++;
        } else {
          MessagingErrorCode code = sr.getException().getMessagingErrorCode();
          if (code == MessagingErrorCode.UNREGISTERED
              || code == MessagingErrorCode.INVALID_ARGUMENT) {
            staleTokens.add(tokens.get(i));
          } else {
            log.warn("FCM send failed for token {}: {}", tokens.get(i), code);
          }
        }
      }
      log.info("FCM multicast: {}/{} sent successfully", successCount, tokens.size());
    } catch (FirebaseMessagingException e) {
      log.error("FCM multicast failed: {}", e.getMessage());
    }
    return staleTokens;
  }
}
