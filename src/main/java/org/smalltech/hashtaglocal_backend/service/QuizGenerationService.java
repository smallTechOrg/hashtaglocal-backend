package org.smalltech.hashtaglocal_backend.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.model.request.CreateQuizRequest;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Weekly automated quiz generation: runs every Sunday at 7 AM IST (via {@code QuizGenerationJob})
 * and creates one quiz per locality per day for the upcoming Monday–Sunday using Groq. Idempotent —
 * skips any (locality, date) pair that already has a quiz.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizGenerationService {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  private final UserRepository userRepository;
  private final BulletinRepository bulletinRepository;
  private final QuizAdminService quizAdminService;
  private final GroqClient groqClient;

  /**
   * Manual trigger: today → this Sunday (inclusive). Fills gaps in the current week so ops can run
   * this any day and catch up without waiting for Sunday's cron.
   */
  public GenerationResult generateForCurrentWeek() {
    LocalDate today = LocalDate.now(IST);
    LocalDate sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
    return generateQuizzes(today, sunday);
  }

  /**
   * Cron trigger (Sunday 7 AM IST): next Monday → next Sunday. Intended to pre-fill the entire
   * coming week before it starts.
   */
  public GenerationResult generateWeeklyQuizzes() {
    LocalDate today = LocalDate.now(IST);
    LocalDate nextMonday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    return generateQuizzes(nextMonday, nextMonday.plusDays(6));
  }

  private GenerationResult generateQuizzes(LocalDate startDate, LocalDate endDate) {

    List<Locality> localities = userRepository.findDistinctUserLocalities();
    log.info(
        "Quiz generation started for {} localities, {} to {}",
        localities.size(),
        startDate,
        endDate);

    int generated = 0, skipped = 0, failed = 0;

    for (Locality locality : localities) {
      for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
        try {
          boolean hasQuiz =
              bulletinRepository
                  .findByLocalityIdAndDate(locality.getId(), date)
                  .map(b -> b.getQuiz() != null)
                  .orElse(false);
          if (hasQuiz) {
            skipped++;
            continue;
          }

          List<String> recentQuestions =
              bulletinRepository.findRecentQuizQuestions(locality.getId(), 20);
          GroqClient.QuizDraft draft = groqClient.generateQuiz(locality.getName(), recentQuestions);
          if (draft == null) {
            failed++;
            log.warn("Groq returned no quiz for {} on {}", locality.getHashtag(), date);
            continue;
          }

          CreateQuizRequest req = new CreateQuizRequest();
          req.setLocalityId(locality.getId());
          req.setDate(date);
          req.setQuestion(draft.question());
          req.setOptions(draft.options());
          req.setAnswerOptionIndex(draft.answerOptionIndex());
          req.setExplanation(draft.explanation());
          quizAdminService.createQuiz(req);
          generated++;

          Thread.sleep(600); // gentle rate-limiting between Groq calls
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("Quiz generation interrupted");
          return result(startDate, endDate, localities.size(), generated, skipped, failed);
        } catch (Exception e) {
          failed++;
          log.error(
              "Quiz generation failed for {} on {}: {}",
              locality.getHashtag(),
              date,
              e.getMessage());
        }
      }
    }

    log.info(
        "Quiz generation finished: {} generated, {} skipped, {} failed",
        generated,
        skipped,
        failed);
    return result(startDate, endDate, localities.size(), generated, skipped, failed);
  }

  private GenerationResult result(
      LocalDate start, LocalDate end, int total, int generated, int skipped, int failed) {
    return GenerationResult.builder()
        .startDate(start)
        .endDate(end)
        .totalLocalities(total)
        .generated(generated)
        .skipped(skipped)
        .failed(failed)
        .build();
  }

  @Data
  @Builder
  public static class GenerationResult {
    private LocalDate startDate;
    private LocalDate endDate;
    private int totalLocalities;
    private int generated;
    private int skipped;
    private int failed;
  }
}
