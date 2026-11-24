package org.smalltech.hashtaglocal_backend.infra.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.infra.notification.FcmSender.MulticastResult;

class FcmSenderTest {

  private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);

  @Test
  void disabled_sendsNothing() {
    FcmSender sender = new FcmSender(firebaseMessaging, false);

    String messageId = sender.send("token", "title", "body", Map.of(), "label");
    MulticastResult result =
        sender.sendMulticast(List.of("a", "b"), "title", "body", Map.of(), "label");

    assertThat(messageId).isNull();
    assertThat(result.successCount()).isZero();
    assertThat(result.staleTokens()).isEmpty();
    verifyNoInteractions(firebaseMessaging);
  }

  @Test
  void enabled_sendsToFcm() throws Exception {
    BatchResponse response = mock(BatchResponse.class);
    when(response.getResponses()).thenReturn(List.of());
    when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id");
    when(firebaseMessaging.sendEachForMulticast(any(MulticastMessage.class))).thenReturn(response);
    FcmSender sender = new FcmSender(firebaseMessaging, true);

    assertThat(sender.send("token", "title", "body", Map.of(), "label")).isEqualTo("message-id");
    sender.sendMulticast(List.of("a"), "title", "body", Map.of(), "label");

    verify(firebaseMessaging).send(any(Message.class));
    verify(firebaseMessaging).sendEachForMulticast(any(MulticastMessage.class));
  }
}
