package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;

@ExtendWith(MockitoExtension.class)
class QuizGenerationServiceTest {

  private static final String GOOD_REPLY =
      """
      Sure! {"question": "Which river flows through Port Blair?",
      "options": ["Ganga", "Brahmaputra", "Yamuna", "None of these"],
      "answer_index": 4,
      "explanation": "Port Blair has no major river; the city sits on the Andaman islands."}
      """;

  // Repeats the recent question used by the tests below ("Which airport serves Port Blair?").
  private static final String REPEAT_REPLY =
      "{\"question\": \"Which  airport serves PORT BLAIR?\","
          + " \"options\": [\"A\", \"B\", \"C\", \"D\"], \"answer_index\": 1,"
          + " \"explanation\": \"Because.\"}";

  private static final String RECENT_QUESTION = "Which airport serves Port Blair?";

  @Mock private ClaudeClient claudeClient;
  @Mock private QuizRepository quizRepository;

  private QuizGenerationService service;

  private final Locality portBlair =
      Locality.builder().id(5L).hashtag("portblair").name("Port Blair").build();

  @BeforeEach
  void setUp() {
    // Real Jackson mapper: the parser is what is under test, not Spring's bean.
    service = new QuizGenerationService(claudeClient, quizRepository, new ObjectMapper());
  }

  @Test
  void validReplyIsSavedAsPendingQuiz() {
    when(claudeClient.complete(anyString(), anyInt())).thenReturn(Optional.of(GOOD_REPLY));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<QuizEntity> saved = service.generatePendingQuiz(portBlair);

    assertTrue(saved.isPresent());
    QuizEntity quiz = saved.get();
    assertEquals(QuizStatus.PENDING, quiz.getStatus());
    assertEquals("Which river flows through Port Blair?", quiz.getQuestion());
    assertEquals(4, quiz.getAnswerOptionIndex());
    assertEquals("None of these", quiz.getOption4().get("text"));
    assertEquals(portBlair, quiz.getLocality());
  }

  @Test
  void repeatedQuestionIsRejectedAndClaudeIsAskedAgain() {
    when(claudeClient.complete(anyString(), anyInt()))
        .thenReturn(Optional.of(REPEAT_REPLY), Optional.of(GOOD_REPLY));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<QuizEntity> saved = service.generatePendingQuiz(portBlair);

    // Repeat on attempt 1 is dropped; attempt 2 is the fresh question and is saved.
    assertTrue(saved.isPresent());
    assertEquals(4, saved.get().getAnswerOptionIndex());
    verify(claudeClient, times(2)).complete(anyString(), anyInt());
  }

  @Test
  void twoBadRepliesSaveNothing() {
    when(claudeClient.complete(anyString(), anyInt()))
        .thenReturn(Optional.of("not json at all"), Optional.of(REPEAT_REPLY));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void claudeUnavailableSavesNothing() {
    when(claudeClient.complete(anyString(), anyInt())).thenReturn(Optional.empty());

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void parseRejectsWrongOptionCount() {
    String threeOptions =
        "{\"question\": \"Q?\", \"options\": [\"a\", \"b\", \"c\"], \"answer_index\": 1,"
            + " \"explanation\": \"E.\"}";

    assertFalse(service.parse(threeOptions).isPresent());
  }

  @Test
  void parseRejectsDuplicateOptionsAndBadAnswerIndex() {
    String duplicate =
        "{\"question\": \"Q?\", \"options\": [\"Same\", \"same \", \"c\", \"d\"],"
            + " \"answer_index\": 1, \"explanation\": \"E.\"}";
    String badIndex =
        "{\"question\": \"Q?\", \"options\": [\"a\", \"b\", \"c\", \"d\"],"
            + " \"answer_index\": 5, \"explanation\": \"E.\"}";

    assertFalse(service.parse(duplicate).isPresent());
    assertFalse(service.parse(badIndex).isPresent());
  }

  @Test
  void promptListsRecentQuestionsToAvoid() {
    String prompt =
        service.buildPrompt(portBlair, List.of("Which river flows through Port Blair?"));

    assertTrue(prompt.contains("Port Blair (#portblair)"));
    assertTrue(prompt.contains("- Which river flows through Port Blair?"));
    assertTrue(prompt.contains("Reply with ONLY a JSON object"));
  }

  private static QuizEntity recentQuiz(String question) {
    return QuizEntity.builder().question(question).build();
  }
}
