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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates one quiz per locality through the AI fallback chain (Claude, then Groq, then Gemini)
 * and saves it as PENDING for admin review.
 *
 * <p>Flow per locality:
 *
 * <ol>
 *   <li>Generate a quiz with the LLM. The prompt holds only the locality name; old questions are
 *       never added to it, since longer prompts make the model worse.
 *   <li>Check it in code: valid JSON, four distinct options, a valid answer index, and not an exact
 *       repeat of a question from the last 100 days.
 *   <li>Evaluate it with a second LLM call: relevant and high quality, accurate, and not a
 *       duplicate. The existing questions go into this prompt, so duplicates are caught here.
 *   <li>If it passes, save it as PENDING. If not, generate again, up to MAX_ATTEMPTS times.
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

  private static final int NO_REPEAT_DAYS = 100;
  static final int MAX_ATTEMPTS = 3;
  private static final int MAX_TOKENS = 700;
  private static final int EVAL_MAX_TOKENS = 200;
  private static final int OPTION_COUNT = 4;

  /** Matches a named placeholder such as {locality} in the prompt templates. */
  private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)}");

  // Placeholders: {locality}.
  private static final String PROMPT_TEMPLATE =
      """
      Be a quizmaster and write one multiple-choice quiz for the community of {locality} in India.

      Rules:
      - Ask about this place: its history, geography, culture, landmarks, or civic life, or any \
      other topic which is interesting but related to the locality.
      - Exactly four options, all distinct, exactly one correct.
      - answer_index is the 1-based position of the correct option.
      - explanation should be 1-2 highly interesting short sentences about the question and answer.

      Reply with ONLY a JSON object, no other text, in this shape:
      {"question": "...", "options": ["...", "...", "...", "..."],
      "answer_index": 1, "explanation": "..."}
      """;

  // Placeholders: {locality}, {quiz}, {existingQuizzes}.
  private static final String EVAL_PROMPT_TEMPLATE =
      """
      Be a quiz evaluator and evaluate the quiz on these following criteria:
      - Relevant & High Quality. Should be related to the locality {locality} and should be \
      interesting.
      - Accuracy. The question and answer should be 100% accurate.
      - Non-Duplicate. The quiz should not have already been asked.

      This is the quiz:
      {quiz}

      These are the already existing quizzes:
      {existingQuizzes}

      Reply with ONLY a JSON object, no other text, in this shape:
      {"is_evaluation_passed": true, "rejection_reason": null}
      When it fails, rejection_reason is one of: INACCURATE, NOT_RELEVANT, LOW_QUALITY, DUPLICATE.
      """;

  private final AiFallbackChain aiFallbackChain;
  private final QuizRepository quizRepository;
  private final ObjectMapper objectMapper;

  /** Generates and saves one PENDING quiz, or returns empty if no acceptable quiz was produced. */
  @Transactional
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
   * Generates and evaluates up to MAX_ATTEMPTS quizzes, returning the first one that passes the
   * code checks and the LLM evaluation. {@code existingQuestions} only go to the evaluator.
   */
  Optional<GeneratedQuiz> generateCandidate(Locality locality, List<String> existingQuestions) {
    Set<String> existingKeys =
        existingQuestions.stream()
            .map(QuizGenerationService::questionKey)
            .collect(Collectors.toSet());
    String prompt = buildPrompt(locality);
    String hashtag = locality.getHashtag();

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Optional<GeneratedQuiz> quiz =
          aiFallbackChain
              .complete(prompt, MAX_TOKENS)
              .flatMap(
                  reply -> parse(reply.text()).map(q -> q.withGeneratedBy(reply.generatedBy())));
      if (quiz.isEmpty()) {
        log.info("Attempt {} for {}: no valid quiz", attempt, hashtag);
        continue;
      }
      // Exact repeats are caught here for free; paraphrases are left to the evaluator.
      if (existingKeys.contains(questionKey(quiz.get().question()))) {
        log.info("Attempt {} for {}: exact repeat of an existing question", attempt, hashtag);
        continue;
      }
      Evaluation evaluation = evaluate(locality, quiz.get(), existingQuestions);
      if (evaluation.passed()) {
        log.info("Attempt {} for {}: quiz passed evaluation", attempt, hashtag);
        return quiz;
      }
      log.info(
          "Attempt {} for {}: rejected by evaluation ({})",
          attempt,
          hashtag,
          evaluation.rejectionReason());
    }
    return Optional.empty();
  }

  /**
   * Asks the LLM whether the quiz passes. An unavailable evaluator or an unreadable reply counts as
   * a fail, so nothing unevaluated reaches the pending list.
   */
  Evaluation evaluate(Locality locality, GeneratedQuiz quiz, List<String> existingQuestions) {
    return aiFallbackChain
        .complete(buildEvaluationPrompt(locality, quiz, existingQuestions), EVAL_MAX_TOKENS)
        .map(reply -> parseEvaluation(reply.text()))
        .orElse(Evaluation.failed("EVALUATOR_UNAVAILABLE"));
  }

  /**
   * Reads {"is_evaluation_passed": true|false, "rejection_reason": "..."}. Anything that is not a
   * clear JSON {@code true} counts as a fail.
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
        return new Evaluation(true, null);
      }
      String reason = node.path("rejection_reason").asText("").trim();
      return Evaluation.failed(reason.isBlank() || "null".equals(reason) ? "UNKNOWN" : reason);
    } catch (JsonProcessingException e) {
      return Evaluation.failed("UNREADABLE_EVALUATION");
    }
  }

  /** The prompt asking the LLM to judge one quiz against the criteria and existing questions. */
  String buildEvaluationPrompt(
      Locality locality, GeneratedQuiz quiz, List<String> existingQuestions) {
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
    String existing =
        existingQuestions.isEmpty()
            ? "None yet."
            : existingQuestions.stream().map(q -> "- " + q).collect(Collectors.joining("\n"));
    return fill(
        EVAL_PROMPT_TEMPLATE,
        Map.of("locality", locality.getName(), "quiz", quizText, "existingQuizzes", existing));
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

  /** The generation prompt: only the locality name, nothing else. */
  String buildPrompt(Locality locality) {
    return fill(PROMPT_TEMPLATE, Map.of("locality", locality.getName()));
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
   * otherwise INACCURATE, NOT_RELEVANT, LOW_QUALITY or DUPLICATE from the model, or one of our own
   * codes (EVALUATOR_UNAVAILABLE, UNREADABLE_EVALUATION, UNKNOWN).
   */
  record Evaluation(boolean passed, String rejectionReason) {
    static Evaluation failed(String reason) {
      return new Evaluation(false, reason);
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
