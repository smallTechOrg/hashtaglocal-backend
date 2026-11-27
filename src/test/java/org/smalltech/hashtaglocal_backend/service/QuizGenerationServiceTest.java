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
import org.smalltech.hashtaglocal_backend.service.QuizGenerationService.Scope;
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
    verify(aiFallbackChain, times(QuizGenerationService.MAX_TOTAL_ATTEMPTS))
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
    // The retry's generation prompt lists the rejected question so it isn't written again.
    verify(aiFallbackChain)
        .complete(
            and(startsWith(GEN), contains("- Which river flows through Port Blair?")), anyInt());
  }

  @Test
  void existingQuestionsGoToTheGeneratorButNotTheEvaluator() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.findByLocality_IdAndCreatedAtAfter(anyLong(), any()))
        .thenReturn(List.of(recentQuiz(RECENT_QUESTION)));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    service.generatePendingQuiz(portBlair);

    verify(aiFallbackChain).complete(and(startsWith(GEN), contains(RECENT_QUESTION)), anyInt());
    verify(aiFallbackChain, never())
        .complete(and(startsWith(EVAL), contains(RECENT_QUESTION)), anyInt());
  }

  @Test
  void quizBadOnEveryEvaluationSavesNothing() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(BAD_VERDICT));

    assertFalse(service.generatePendingQuiz(portBlair).isPresent());
    verify(aiFallbackChain, times(QuizGenerationService.MAX_TOTAL_ATTEMPTS))
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
  void generationPromptListsExistingQuizzesToAvoid() {
    String prompt = service.buildPrompt(portBlair, List.of(RECENT_QUESTION), Scope.LOCALITY);

    assertTrue(prompt.contains("community of Port Blair in India."));
    assertTrue(prompt.contains("Do NOT repeat or closely paraphrase any of them:"));
    assertTrue(prompt.contains("- " + RECENT_QUESTION));
    assertFalse(prompt.contains("#portblair"));
    assertFalse(prompt.contains("{locality}"));
    assertFalse(prompt.contains("{existingQuizzes}"));
    // The JSON shape keeps its braces.
    assertTrue(prompt.contains("{\"question\": \"...\""));
  }

  @Test
  void generationPromptWithNothingToAvoidHasNoListOrGap() {
    String prompt = service.buildPrompt(portBlair, List.of(), Scope.LOCALITY);

    assertFalse(prompt.contains("already existing quizzes"));
    assertFalse(prompt.contains("{existingQuizzes}"));
    assertFalse(prompt.contains("\n\n\n"));
  }

  @Test
  void evaluationPromptChecksQualityAndAccuracyOnly() {
    QuizGenerationService.GeneratedQuiz quiz = service.parse(GOOD_REPLY).orElseThrow();

    String prompt = service.buildEvaluationPrompt(portBlair, quiz, Scope.LOCALITY);
    assertTrue(prompt.contains("related to the locality Port Blair"));
    assertTrue(prompt.contains("Accuracy."));
    assertTrue(prompt.contains("Question: Which river flows through Port Blair?"));
    assertTrue(prompt.contains("4. None of these"));
    assertFalse(prompt.contains("Non-Duplicate"));
    assertFalse(prompt.contains("DUPLICATE"));
    assertFalse(prompt.contains("existing quizzes"));
    assertFalse(prompt.contains("{quiz}"));
  }

  @Test
  void fallsBackToTheStateWhenEveryLocalityQuizIsRejected() {
    // Like #siddipet: every quiz about the town itself is rejected, the first state quiz passes.
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt()))
        .thenReturn(
            reply(BAD_VERDICT), reply(BAD_VERDICT), reply(BAD_VERDICT), reply(GOOD_VERDICT));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    assertTrue(service.generatePendingQuiz(portBlair).isPresent());
    String stateRule = "Ask about the Indian state that Port Blair is in, as a whole";
    // Three locality attempts first, then one state attempt, which passed.
    verify(aiFallbackChain, times(QuizGenerationService.MAX_ATTEMPTS + 1))
        .complete(startsWith(GEN), anyInt());
    verify(aiFallbackChain, times(1)).complete(and(startsWith(GEN), contains(stateRule)), anyInt());
    // The state quiz is judged against the state, not the town.
    verify(aiFallbackChain, times(1))
        .complete(
            and(startsWith(EVAL), contains("about the Indian state that Port Blair is in")),
            anyInt());
  }

  @Test
  void noStateAttemptWhenALocalityQuizPasses() {
    when(aiFallbackChain.complete(startsWith(GEN), anyInt())).thenReturn(reply(GOOD_REPLY));
    when(aiFallbackChain.complete(startsWith(EVAL), anyInt())).thenReturn(reply(GOOD_VERDICT));
    when(quizRepository.save(any(QuizEntity.class))).thenAnswer(inv -> inv.getArgument(0));

    service.generatePendingQuiz(portBlair);

    verify(aiFallbackChain, never())
        .complete(and(startsWith(GEN), contains("Indian state that")), anyInt());
  }

  @Test
  void generationPromptAsksForDocumentedFactsAndNoPolitics() {
    for (Scope scope : Scope.values()) {
      String prompt = service.buildPrompt(portBlair, List.of(), scope);
      assertTrue(prompt.contains("Use only well-documented facts"));
      assertTrue(prompt.contains("Do not claim that something is unique to"));
      assertTrue(prompt.contains("Do not ask about politicians"));
      assertFalse(prompt.contains("{scopeRules}"));
      assertFalse(prompt.contains("{locality}"));
    }
  }

  @Test
  void statePromptAsksAboutTheWholeStateAndNoOtherCity() {
    String prompt = service.buildPrompt(portBlair, List.of(), Scope.STATE);

    assertTrue(prompt.contains("Ask about the Indian state that Port Blair is in, as a whole"));
    assertTrue(prompt.contains("Name the state in the question."));
    assertTrue(
        prompt.contains(
            "Do NOT ask about Port Blair itself or about any other city, town or district"));
    assertFalse(prompt.contains("Ask about this place"));
  }

  @Test
  void parseEvaluationReadsTheRejectionDetail() {
    String verdict =
        "{\"is_evaluation_passed\": false, \"rejection_reason\": \"INACCURATE\","
            + " \"rejection_detail\": \"That temple is near Warangal, not Siddipet.\"}";

    QuizGenerationService.Evaluation evaluation = service.parseEvaluation(verdict);

    assertEquals("INACCURATE", evaluation.rejectionReason());
    assertEquals("That temple is near Warangal, not Siddipet.", evaluation.detail());
    assertEquals(null, service.parseEvaluation(BAD_VERDICT).detail());
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
