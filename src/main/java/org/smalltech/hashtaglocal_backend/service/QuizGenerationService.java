package org.smalltech.hashtaglocal_backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.service.ai.AiFallbackChain;
import org.smalltech.hashtaglocal_backend.service.ai.AiProviderException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates one quiz per locality through the AI fallback chain (Claude, then Groq, then Gemini)
 * and saves it as PENDING for admin review.
 *
 * <p>Flow per locality:
 *
 * <ol>
 *   <li>Generate a quiz with the LLM. The prompt lists the locality's existing questions (last 100
 *       days) so the model writes a new one, plus any attempts already rejected in this run.
 *   <li>Check it in code: valid JSON, four distinct options, a valid answer index, and not an exact
 *       repeat of an existing question.
 *   <li>Evaluate it with a second LLM call for quality and accuracy only (relevant, interesting,
 *       100% accurate). Duplicates are handled by the generation prompt and the code check.
 *   <li>If it passes, save it as PENDING. If not, generate again, up to MAX_ATTEMPTS times.
 *   <li>If no quiz about the locality passed (small towns have few well-documented facts), repeat
 *       steps 1-4 up to MAX_ATTEMPTS more times for a quiz about the locality's state as a whole.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

  private static final int NO_REPEAT_DAYS = 100;

  /** Attempts per scope: first about the locality itself, then about its state. */
  static final int MAX_ATTEMPTS = 3;

  /** Most generation attempts in one run: MAX_ATTEMPTS for each scope. */
  static final int MAX_TOTAL_ATTEMPTS = MAX_ATTEMPTS * Scope.values().length;

  private static final int MAX_TOKENS = 700;
  private static final int EVAL_MAX_TOKENS = 250;
  private static final int OPTION_COUNT = 4;

  /** Matches a named placeholder such as {locality} in the prompt templates. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

  /**
   * What a quiz is about. Small towns often have few well-documented facts, and the model then
   * makes some up, which the evaluator rejects as INACCURATE (seen for #siddipet). So when every
   * LOCALITY attempt fails, the run falls back to the STATE the locality is in, as a whole, never
   * another city in it. The model names the state itself; localities don't store it.
   */
  enum Scope {
    LOCALITY(
        """
        - Ask about this place: its history, geography, culture, landmarks, or civic life, or any \
        other topic which is interesting but related to the locality.""",
        "Should be related to the locality {locality}"),
    STATE(
        """
        - Ask about the Indian state that {locality} is in, as a whole: the state's history, \
        geography, culture, festivals, food, language, or statewide landmarks. Name the state in \
        the question.
        - Do NOT ask about {locality} itself or about any other city, town or district in the \
        state.""",
        "Should be about the Indian state that {locality} is in, as a whole (not about"
            + " {locality} or another city, town or district in it)");

    private final String generationRules;
    private final String relevance;

    Scope(String generationRules, String relevance) {
      this.generationRules = generationRules;
      this.relevance = relevance;
    }
  }

  // Placeholders: {locality}, {scopeRules}, {existingQuizzes}. {existingQuizzes} is empty when
  // there are none.
  private static final String PROMPT_TEMPLATE =
      """
      Be a quizmaster and write one multiple-choice quiz for the community of {locality} in India.

      Rules:
      {scopeRules}
      - Use only well-documented facts you are certain of. Do not claim that something is unique \
      to, famous for, or found only in a place unless that is widely documented.
      - Do not ask about politicians, political parties or elections.
      - Exactly four options, all distinct, exactly one correct.
      - answer_index is the 1-based position of the correct option.
      - explanation should be 1-2 highly interesting short sentences about the question and answer.

      {existingQuizzes}

      Reply with ONLY a JSON object, no other text, in this shape:
      {"question": "...", "options": ["...", "...", "...", "..."],
      "answer_index": 1, "explanation": "..."}
      """;

  // Placeholders: {relevance}, {locality}, {quiz}.
  private static final String EVAL_PROMPT_TEMPLATE =
      """
      Be a quiz evaluator and evaluate the quiz on these following criteria:
      - Relevant & High Quality. {relevance} and should be interesting.
      - Accuracy. The question and answer should be 100% accurate.

      This is the quiz:
      {quiz}

      Reply with ONLY a JSON object, no other text, in this shape:
      {"is_evaluation_passed": true, "rejection_reason": null, "rejection_detail": null}
      When it fails, rejection_reason is one of: INACCURATE, NOT_RELEVANT, LOW_QUALITY, and \
      rejection_detail is one short sentence saying what is wrong.
      """;

  private final AiFallbackChain aiFallbackChain;
  private final QuizRepository quizRepository;
  private final ObjectMapper objectMapper;

  /**
   * Generates and saves one PENDING quiz, or returns empty if no acceptable quiz was produced.
   *
   * @throws AiProviderException when the provider configured in the Ops Dashboard fails. Nothing is
   *     saved before that point, so it does not need to roll back a caller's transaction.
   */
  @Transactional(noRollbackFor = AiProviderException.class)
  public Optional<QuizEntity> generatePendingQuiz(Locality locality) {
    Optional<GeneratedQuiz> candidate = generateCandidate(locality, recentQuestions(locality));
    if (candidate.isEmpty()) {
      log.warn("No acceptable quiz generated for {}", locality.getHashtag());
      return Optional.empty();
    }
    QuizEntity saved = quizRepository.save(toEntity(locality, candidate.get()));
    log.info("Saved PENDING quiz {} for {}", saved.getId(), locality.getHashtag());
    return Optional.of(saved);
  }

  /**
   * Generates and evaluates quizzes, returning the first one that passes the code checks and the
   * LLM evaluation: up to MAX_ATTEMPTS about the locality itself, then, if none passed, up to
   * MAX_ATTEMPTS about its state as a whole (see {@link Scope}). The generation prompt lists {@code
   * existingQuestions} plus every question already tried in this run, so each retry asks for
   * something new.
   */
  Optional<GeneratedQuiz> generateCandidate(Locality locality, List<String> existingQuestions) {
    Set<String> existingKeys =
        existingQuestions.stream()
            .map(QuizGenerationService::questionKey)
            .collect(Collectors.toSet());
    List<String> avoid = new ArrayList<>(existingQuestions);
    String hashtag = locality.getHashtag();

    for (Scope scope : Scope.values()) {
      if (scope == Scope.STATE) {
        log.info("No locality quiz passed for {}; trying its state instead", hashtag);
      }
      for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
        Optional<GeneratedQuiz> quiz =
            aiFallbackChain
                .complete(buildPrompt(locality, avoid, scope), MAX_TOKENS)
                .flatMap(
                    reply -> parse(reply.text()).map(q -> q.withGeneratedBy(reply.generatedBy())));
        if (quiz.isEmpty()) {
          log.info("Attempt {} ({}) for {}: no valid quiz", attempt, scope, hashtag);
          continue;
        }
        String question = quiz.get().question();
        // Safety net: the model was told not to repeat, but an exact repeat is caught for free.
        if (existingKeys.contains(questionKey(question))) {
          log.info(
              "Attempt {} ({}) for {}: exact repeat of an existing question: {}",
              attempt,
              scope,
              hashtag,
              question);
          continue;
        }
        Evaluation evaluation = evaluate(locality, quiz.get(), scope);
        if (evaluation.passed()) {
          log.info("Attempt {} ({}) for {}: quiz passed evaluation", attempt, scope, hashtag);
          return quiz;
        }
        // Options, marked answer and the evaluator's reason, so a failure can be diagnosed from
        // this one line.
        log.info(
            "Attempt {} ({}) for {}: rejected by evaluation ({}: {}): {} | options: {} | marked"
                + " answer: {}",
            attempt,
            scope,
            hashtag,
            evaluation.rejectionReason(),
            evaluation.detail(),
            question,
            quiz.get().options(),
            quiz.get().answerOptionIndex());
        // Don't let the next attempt write the same rejected question again.
        avoid.add(question);
      }
    }
    return Optional.empty();
  }

  /**
   * Asks the LLM whether the quiz passes on quality and accuracy. An unavailable evaluator or an
   * unreadable reply counts as a fail, so nothing unevaluated reaches the pending list. That
   * includes a temporary failure (rate limit, outage) of the Ops Dashboard connection, so the next
   * attempt can still run; a config failure (bad key, unknown model) is thrown, since every later
   * call would fail too.
   */
  Evaluation evaluate(Locality locality, GeneratedQuiz quiz, Scope scope) {
    try {
      return aiFallbackChain
          .complete(buildEvaluationPrompt(locality, quiz, scope), EVAL_MAX_TOKENS)
          .map(reply -> parseEvaluation(reply.text()))
          .orElse(Evaluation.failed("EVALUATOR_UNAVAILABLE"));
    } catch (AiProviderException e) {
      if (!e.isTemporary()) {
        throw e;
      }
      log.warn("Evaluator unavailable for {}: {}", locality.getHashtag(), e.getMessage());
      return Evaluation.failed("EVALUATOR_UNAVAILABLE");
    }
  }

  /**
   * Reads {"is_evaluation_passed": true|false, "rejection_reason": "...", "rejection_detail":
   * "..."}. Anything that is not a clear JSON {@code true} counts as a fail. The detail is only
   * logged, and is null when the model leaves it out.
   */
  Evaluation parseEvaluation(String reply) {
    int start = reply.indexOf('{');
    int end = reply.lastIndexOf('}');
    if (start < 0 || end < start) {
      return Evaluation.failed("UNREADABLE_EVALUATION");
    }
    try {
      JsonNode node = objectMapper.readTree(reply.substring(start, end + 1));
      JsonNode passed = node.path("is_evaluation_passed");
      if (passed.isBoolean() && passed.booleanValue()) {
        return new Evaluation(true, null, null);
      }
      String reason = node.path("rejection_reason").asText("").trim();
      String detail = node.path("rejection_detail").asText("").trim();
      return new Evaluation(
          false,
          reason.isBlank() || "null".equals(reason) ? "UNKNOWN" : reason,
          detail.isBlank() || "null".equals(detail) ? null : detail);
    } catch (JsonProcessingException e) {
      return Evaluation.failed("UNREADABLE_EVALUATION");
    }
  }

  /** The prompt asking the LLM to judge one quiz on quality and accuracy, for the given scope. */
  String buildEvaluationPrompt(Locality locality, GeneratedQuiz quiz, Scope scope) {
    List<String> o = quiz.options();
    String options =
        IntStream.range(0, o.size())
            .mapToObj(i -> (i + 1) + ". " + o.get(i))
            .collect(Collectors.joining("\n"));
    String quizText =
        "Question: "
            + quiz.question()
            + "\nOptions:\n"
            + options
            + "\nCorrect answer: "
            + quiz.answerOptionIndex()
            + "\nExplanation: "
            + quiz.explanation();
    // The scope text has its own {locality}; fill() is single-pass, so fill that first.
    String relevance = fill(scope.relevance, Map.of("locality", locality.getName()));
    return fill(
        EVAL_PROMPT_TEMPLATE,
        Map.of("relevance", relevance, "locality", locality.getName(), "quiz", quizText));
  }

  /** Parses the model's reply, ignoring any text around the JSON. Empty if a rule is broken. */
  Optional<GeneratedQuiz> parse(String reply) {
    int start = reply.indexOf('{');
    int end = reply.lastIndexOf('}');
    if (start < 0 || end < start) {
      return Optional.empty();
    }
    try {
      return toQuiz(objectMapper.readTree(reply.substring(start, end + 1)));
    } catch (JsonProcessingException e) {
      return Optional.empty();
    }
  }

  /** Checks the parsed JSON against the quality rules and builds the candidate. */
  private Optional<GeneratedQuiz> toQuiz(JsonNode node) {
    String question = node.path("question").asText("").trim();
    String explanation = node.path("explanation").asText("").trim();
    int answerIndex = node.path("answer_index").asInt(0);
    List<String> options = textList(node.path("options"));

    boolean valid =
        !question.isBlank()
            && !explanation.isBlank()
            && options.size() == OPTION_COUNT
            && options.stream().noneMatch(String::isBlank)
            && allDistinct(options)
            && answerIndex >= 1
            && answerIndex <= OPTION_COUNT;
    return valid
        ? Optional.of(new GeneratedQuiz(question, options, answerIndex, explanation, null))
        : Optional.empty();
  }

  /**
   * The generation prompt: the locality name, what to ask about (the scope), and the questions to
   * avoid (existing ones and any rejected in this run). With nothing to avoid, the section and its
   * blank line are left out.
   */
  String buildPrompt(Locality locality, List<String> avoidQuestions, Scope scope) {
    String existing =
        avoidQuestions.isEmpty()
            ? ""
            : "These are the already existing quizzes. Do NOT repeat or closely paraphrase any of"
                + " them:\n"
                + avoidQuestions.stream().map(q -> "- " + q).collect(Collectors.joining("\n"));
    // The scope rules have their own {locality}; fill() is single-pass, so fill that first.
    String scopeRules = fill(scope.generationRules, Map.of("locality", locality.getName()));
    return fill(
            PROMPT_TEMPLATE,
            Map.of(
                "locality",
                locality.getName(),
                "scopeRules",
                scopeRules,
                "existingQuizzes",
                existing))
        .replaceAll("\n{3,}", "\n\n");
  }

  /**
   * Replaces each {name} in the template with its value, in one pass, so text inside a value (e.g.
   * a model-written question) is never itself treated as a placeholder. Unknown names, and JSON
   * braces like {"question": ...}, are left as they are.
   */
  static String fill(String template, Map<String, String> values) {
    return PLACEHOLDER
        .matcher(template)
        .replaceAll(m -> Matcher.quoteReplacement(values.getOrDefault(m.group(1), m.group())));
  }

  /** Questions created for this locality within the no-repeat window. */
  private List<String> recentQuestions(Locality locality) {
    LocalDateTime since = LocalDateTime.now().minusDays(NO_REPEAT_DAYS);
    return quizRepository.findByLocality_IdAndCreatedAtAfter(locality.getId(), since).stream()
        .map(QuizEntity::getQuestion)
        .toList();
  }

  private static QuizEntity toEntity(Locality locality, GeneratedQuiz quiz) {
    List<String> o = quiz.options();
    return QuizEntity.builder()
        .locality(locality)
        .question(quiz.question())
        .option1(option(o.get(0)))
        .option2(option(o.get(1)))
        .option3(option(o.get(2)))
        .option4(option(o.get(3)))
        .answerOptionIndex(quiz.answerOptionIndex())
        .explanation(quiz.explanation())
        .generatedBy(quiz.generatedBy())
        .status(QuizStatus.PENDING)
        .build();
  }

  /** Trimmed text of each element in a JSON array; empty if the node is not an array. */
  private static List<String> textList(JsonNode node) {
    if (!node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    node.forEach(item -> values.add(item.asText("").trim()));
    return values;
  }

  /** True when no two options are the same once normalized. */
  private static boolean allDistinct(List<String> options) {
    return options.stream().map(QuizGenerationService::questionKey).distinct().count()
        == options.size();
  }

  private static Map<String, Object> option(String text) {
    return Map.of("text", text);
  }

  /** Normalizes case, punctuation and spacing, so near-identical questions compare equal. */
  private static String questionKey(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
  }

  /**
   * The evaluator's verdict on one quiz candidate. {@code rejectionReason} is null when it passed;
   * otherwise INACCURATE, NOT_RELEVANT or LOW_QUALITY from the model, or one of our own codes
   * (EVALUATOR_UNAVAILABLE, UNREADABLE_EVALUATION, UNKNOWN). {@code detail} is the model's one
   * sentence on what is wrong, or null; it is only logged.
   */
  record Evaluation(boolean passed, String rejectionReason, String detail) {
    static Evaluation failed(String reason) {
      return new Evaluation(false, reason, null);
    }
  }

  /**
   * A quiz candidate from the AI, before it is saved. {@code generatedBy} is the provider/model
   * that wrote it, e.g. "anthropic/claude-sonnet-5-5".
   */
  public record GeneratedQuiz(
      String question,
      List<String> options,
      int answerOptionIndex,
      String explanation,
      String generatedBy) {

    GeneratedQuiz withGeneratedBy(String source) {
      return new GeneratedQuiz(question, options, answerOptionIndex, explanation, source);
    }
  }
}
