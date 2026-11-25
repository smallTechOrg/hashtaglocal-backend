package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
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
import org.smalltech.hashtaglocal_backend.service.ai.AiFailure;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain.AiCompletion;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;

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

  private static final String GOOD_VERDICT =
      "{\"is_evaluation_passed\": true, \"rejection_reason\": null}";

  private static final String BAD_VERDICT =
      "{\"is_evaluation_passed\": false, \"rejection_reason\": \"INACCURATE\"}";

  // Prompts start with these words, so the mock can tell generation from evaluation.
  private static final String GEN = "Be a quizmaster";
  private static final String EVAL = "Be a quiz evaluator";

  private static final String GENERATED_BY = "anthropic/claude-sonnet-5-5";

  @Mock private AiFallbackChain aiFallbackChain;
  @Mock private QuizRepository quizRepository;

  private QuizGenerationService service;

  private final Locality portBlair =
      Locality.builder().id(5L).hashtag("portblair").name("Port Blair").build();

  @BeforeEach
  void setUp() {
    // Real Jackson mapper: the parser is what is under test, not Spring's bean.
    service = new QuizGenerationService(aiFallbackChain, quizRepository, new ObjectMapper());
  }

  @Test
  void validReplyIsSavedAsPendingQuiz() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<QuizEntity> saved = service.generatePendingQuiz(portBlair);

    assertTrue(saved.isPresent());
    QuizEntity quiz = saved.get();
    assertEquals(QuizStatus.PENDING, quiz.getStatus());
    assertEquals("Which river flows through Port Blair?", quiz.getQuestion());
    assertEquals(4, quiz.getAnswerOptionIndex());
    assertEquals("None of these", quiz.getOption4().get("text"));
    assertEquals(portBlair, quiz.getLocality());
    assertEquals(GENERATED_BY, quiz.getGeneratedBy());
  }

  @Test
  void repeatedQuestionIsRejectedAndAiIsAskedAgain() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt()))
        .thenReturn(reply(REPEAT_REPLY), reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    Optional<QuizEntity> saved = service.generatePendingQuiz(portBlair);

    // Repeat on attempt 1 is dropped; attempt 2 is the fresh question and is saved.
    assertTrue(saved.isPresent());
    assertEquals(4, saved.get().getAnswerOptionIndex());
    verify(aiFallbackChain, times(2)).complete(startsWith(GEN), anyInt());
    // The repeat never reaches the evaluator.
    verify(aiFallbackChain, times(1)).complete(startsWith(EVAL), anyInt());
  }

  @Test
  void badRepliesOnEveryAttemptSaveNothing() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt()))
        .thenReturn(reply("not json at all"), reply(REPEAT_REPLY));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(aiFallbackChain, times(QuizGenerationService.MAX_ATTEMPTS))
        .complete(startsWith(GEN), anyInt());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void quizRejectedByEvaluationIsRegenerated() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt()))
        .thenReturn(reply(BAD_VERDICT), reply(GOOD_VERDICT));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    assertTrue(service.generatePendingQuiz(portBlair).isPresent());
    verify(aiFallbackChain, times(2)).complete(startsWith(GEN), anyInt());
    verify(aiFallbackChain, times(2)).complete(startsWith(EVAL), anyInt());
  }

  @Test
  void existingQuestionsGoToTheEvaluatorButNeverToTheGenerator() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    service.generatePendingQuiz(portBlair);

    verify(aiFallbackChain).complete(and(startsWith(EVAL), contains(RECENT_QUESTION)), anyInt());
    verify(aiFallbackChain, never())
        .complete(and(startsWith(GEN), contains(RECENT_QUESTION)), anyInt());
  }

  @Test
  void quizBadOnEveryEvaluationSavesNothing() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(BAD_VERDICT));

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(aiFallbackChain, times(QuizGenerationService.MAX_ATTEMPTS))
        .complete(startsWith(EVAL), anyInt());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void evaluatorUnavailableCountsAsBad() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(Optional.empty());

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void temporaryEvaluatorFailureCountsAsBadAndTheNextAttemptRuns() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt()))
        .thenThrow(new AiProviderException("rate limited", AiFailure.RATE_LIMITED))
        .thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    assertTrue(service.generatePendingQuiz(portBlair).isPresent());
    verify(aiFallbackChain, times(2)).complete(startsWith(GEN), anyInt());
  }

  @Test
  void configEvaluatorFailureIsThrown() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt()))
        .thenThrow(new AiProviderException("bad key", AiFailure.AUTH_FAILED));

    assertThrows(AiProviderException.class, () -> service.generatePendingQuiz(portBlair));
    verify(aiFallbackChain, times(1)).complete(startsWith(GEN), anyInt());
    verify(quizRepository, never()).save(any(QuizEntity.class));
  }

  @Test
  void parseEvaluationReadsVerdictAndTreatsGarbageAsFail() {
    assertTrue(service.parseEvaluation("Here: " + GOOD_VERDICT).passed());
    assertFalse(service.parseEvaluation(BAD_VERDICT).passed());
    assertEquals("INACCURATE", service.parseEvaluation(BAD_VERDICT).rejectionReason());
    assertFalse(service.parseEvaluation("looks fine to me").passed());
    // Only a JSON boolean true passes; the string "true" does not.
    assertFalse(service.parseEvaluation("{\"is_evaluation_passed\": \"true\"}").passed());
    assertEquals(
        "UNKNOWN", service.parseEvaluation("{\"is_evaluation_passed\": false}").rejectionReason());
  }

  @Test
  void aiUnavailableSavesNothing() {
    when(aiFallbackChain.complete(anyString(), anyInt())).thenReturn(Optional.empty());

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
  void generationPromptHasOnlyTheLocalityName() {
    String prompt = service.buildPrompt(portBlair);

    assertTrue(prompt.contains("community of Port Blair in India."));
    assertFalse(prompt.contains("#portblair"));
    assertFalse(prompt.contains("{locality}"));
    // The JSON shape keeps its braces.
    assertTrue(prompt.contains("{\"question\": \"...\""));
  }

  @Test
  void evaluationPromptHasQuizAndExistingQuestions() {
    QuizGenerationService.GeneratedQuiz quiz = service.parse(GOOD_REPLY).orElseThrow();

    String withHistory = service.buildEvaluationPrompt(portBlair, quiz, List.of(RECENT_QUESTION));
    assertTrue(withHistory.contains("related to the locality Port Blair"));
    assertTrue(withHistory.contains("Question: Which river flows through Port Blair?"));
    assertTrue(withHistory.contains("4. None of these"));
    assertTrue(withHistory.contains("- " + RECENT_QUESTION));
    assertFalse(withHistory.contains("{quiz}"));
    assertFalse(withHistory.contains("{existingQuizzes}"));

    String noHistory = service.buildEvaluationPrompt(portBlair, quiz, List.of());
    assertTrue(noHistory.contains("None yet."));
  }

  @Test
  void fillReplacesNamesOnceAndLeavesOtherBracesAlone() {
    String filled =
        QuizGenerationService.fill("{a} and {b} and {unknown}", Map.of("a", "{b} $1", "b", "two"));

    // A value that looks like a placeholder is not filled again, and $ is kept literally.
    assertEquals("{b} $1 and two and {unknown}", filled);
  }

  private static Optional<AiCompletion> reply(String text) {
    return Optional.of(new AiCompletion(text, GENERATED_BY));
  }

  private static QuizEntity recentQuiz(String question) {
    return QuizEntity.builder().question(question).build();
  }
}
