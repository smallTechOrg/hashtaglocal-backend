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
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.model.QuizStatus;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates one quiz per locality with Claude and saves it as PENDING for admin review. Quality
 * rules are checked in code: valid JSON, four distinct options, a valid answer index, and no repeat
 * of a question from the last 100 days. A bad candidate is retried once, then skipped.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

  private static final int NO_REPEAT_DAYS = 100;
  private static final int MAX_ATTEMPTS = 2;
  private static final int MAX_TOKENS = 700;
  private static final int OPTION_COUNT = 4;

  private static final String PROMPT_TEMPLATE =
      """
      You write one multiple-choice quiz for the community of %s (#%s) in India.

      Rules:
      - Ask about this place: its history, geography, culture, landmarks, or civic life.
      - The facts must be accurate. If you are not sure of a fact, pick another topic.
      - Exactly four options, all distinct, exactly one correct.
      - answer_index is the 1-based position of the correct option.
      - explanation is 1-2 short sentences on why the answer is correct.

      %sReply with ONLY a JSON object, no other text, in this shape:
      {"question": "...", "options": ["...", "...", "...", "..."],
      "answer_index": 1, "explanation": "..."}
      """;

  private final ClaudeClient claudeClient;
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

  /** Asks Claude up to MAX_ATTEMPTS times for a valid quiz that is not a recent repeat. */
  Optional<GeneratedQuiz> generateCandidate(Locality locality, List<String> recentQuestions) {
    Set<String> recentKeys =
        recentQuestions.stream()
            .map(QuizGenerationService::questionKey)
            .collect(Collectors.toSet());
    String prompt = buildPrompt(locality, recentQuestions);

    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Optional<GeneratedQuiz> quiz = claudeClient.complete(prompt, MAX_TOKENS).flatMap(this::parse);
      if (quiz.isPresent() && !recentKeys.contains(questionKey(quiz.get().question()))) {
        return quiz;
      }
      log.info("Attempt {} gave no usable quiz", attempt);
    }
    return Optional.empty();
  }

  /** Parses Claude's reply, ignoring any text around the JSON. Empty if a rule is broken. */
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
        ? Optional.of(new GeneratedQuiz(question, options, answerIndex, explanation))
        : Optional.empty();
  }

  /** The full prompt sent to Claude, including the recent questions to avoid. */
  String buildPrompt(Locality locality, List<String> recentQuestions) {
    String avoid =
        recentQuestions.isEmpty()
            ? ""
            : "Do NOT repeat or closely paraphrase any of these recent questions:\n"
                + recentQuestions.stream().map(q -> "- " + q).collect(Collectors.joining("\n"))
                + "\n\n";
    return PROMPT_TEMPLATE.formatted(locality.getName(), locality.getHashtag(), avoid);
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

  /** A quiz candidate from Claude, before it is saved. */
  public record GeneratedQuiz(
      String question, List<String> options, int answerOptionIndex, String explanation) {}
}
