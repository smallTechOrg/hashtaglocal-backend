package org.smalltech.hashtaglocal_backend.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.model.AiCategory;
import org.smalltech.hashtaglocal_backend.model.AiVerdict;
import org.smalltech.hashtaglocal_backend.service.FeedModerationClient.ModerationResult;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain.AiCompletion;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;

class AiFeedModerationClientTest {

  private final AiFallbackChain ai = mock(AiFallbackChain.class);
  private final AiFeedModerationClient client = new AiFeedModerationClient(ai, new ObjectMapper());

  private void reply(String text) {
    when(ai.complete(anyString(), anyInt()))
        .thenReturn(Optional.of(new AiCompletion(text, "claude/model")));
  }

  @Test
  void sendsThePostToTheAiProviderAndReadsTheVerdict() {
    when(ai.complete(contains("Road blocked near the market"), anyInt()))
        .thenReturn(
            Optional.of(
                new AiCompletion(
                    "{\"verdict\":\"ALLOW\",\"category\":\"NONE\",\"confidence\":0.93,"
                        + "\"reason\":\"Civic update.\"}",
                    "claude/model")));

    ModerationResult r = client.classify("Road blocked near the market", null, null);

    assertEquals(AiVerdict.ALLOW, r.verdict());
    assertEquals(AiCategory.NONE, r.category());
    assertEquals(0.93, r.confidence());
    assertEquals("Civic update.", r.reason());
    assertEquals("claude/model", r.model());
  }

  @Test
  void readsAVerdictWrappedInAMarkdownFence() {
    reply(
        "```json\n{\"verdict\":\"block\",\"category\":\"spam\",\"confidence\":0.8,"
            + "\"reason\":\"Ad.\"}\n```");

    ModerationResult r = client.classify("Buy now!!!", null, null);

    assertEquals(AiVerdict.BLOCK, r.verdict());
    assertEquals(AiCategory.SPAM, r.category());
  }

  @Test
  void throwsOnAnUnreadableReplySoThePostIsFlagged() {
    reply("I think this post is fine.");

    assertThrows(IllegalStateException.class, () -> client.classify("Hello", null, null));
  }

  @Test
  void throwsOnAnUnknownVerdict() {
    reply("{\"verdict\":\"MAYBE\",\"category\":\"NONE\",\"confidence\":0.5,\"reason\":\"\"}");

    assertThrows(IllegalStateException.class, () -> client.classify("Hello", null, null));
  }

  @Test
  void throwsWhenTheAiHasNoReply() {
    when(ai.complete(anyString(), anyInt())).thenReturn(Optional.empty());

    assertThrows(IllegalStateException.class, () -> client.classify("Hello", null, null));
  }

  @Test
  void passesOnAProviderFailure() {
    when(ai.complete(anyString(), anyInt())).thenThrow(new AiProviderException("bad key"));

    assertThrows(AiProviderException.class, () -> client.classify("Hello", null, null));
  }
}
