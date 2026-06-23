package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.mapper.BulletinViewMapper;
import org.smalltech.hashtaglocal_backend.model.request.CreateQuizRequest;
import org.smalltech.hashtaglocal_backend.model.request.UpdateQuizRequest;
import org.smalltech.hashtaglocal_backend.model.response.AdminBulletinData;
import org.smalltech.hashtaglocal_backend.model.response.AdminLocalityOptionData;
import org.smalltech.hashtaglocal_backend.model.response.AdminQuizData;
import org.smalltech.hashtaglocal_backend.model.response.WeekCoverageData;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizAttemptRepository;
import org.smalltech.hashtaglocal_backend.repository.QuizRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ops-portal CRUD for quizzes and bulletin summaries. Quizzes are entered manually per locality per
 * date (AI generation comes later); the date binding is the bulletin row, which is upserted here
 * when the quiz is attached and by the 8 AM weather job for the weather side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuizAdminService {

  private final QuizRepository quizRepository;
  private final QuizAttemptRepository quizAttemptRepository;
  private final BulletinRepository bulletinRepository;
  private final LocalityRepository localityRepository;
  private final UserRepository userRepository;
  private final FeedPostRepository feedPostRepository;
  private final GroqClient groqClient;

  // ---------------------------------------------------------------- quizzes

  @Transactional
  public AdminQuizData createQuiz(CreateQuizRequest req) {
    Locality locality =
        localityRepository
            .findById(req.getLocalityId())
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND,
                        "NOT_FOUND",
                        "Unknown locality: " + req.getLocalityId()));

    BulletinEntity bulletin =
        bulletinRepository
            .findByLocalityIdAndDate(locality.getId(), req.getDate())
            .orElseGet(
                () -> BulletinEntity.builder().locality(locality).date(req.getDate()).build());
    if (bulletin.getQuiz() != null) {
      throw new IllegalStateException(
          "A quiz already exists for " + locality.getHashtag() + " on " + req.getDate());
    }

    String explanation =
        req.getExplanation() != null && !req.getExplanation().isBlank()
            ? req.getExplanation()
            : null;

    QuizEntity quiz =
        quizRepository.save(
            QuizEntity.builder()
                .locality(locality)
                .question(req.getQuestion())
                .option1(optionMap(req.getOptions().get(0)))
                .option2(optionMap(req.getOptions().get(1)))
                .option3(optionMap(req.getOptions().get(2)))
                .option4(optionMap(req.getOptions().get(3)))
                .answerOptionIndex(req.getAnswerOptionIndex())
                .explanation(explanation)
                .build());

    bulletin.setQuiz(quiz);
    bulletinRepository.save(bulletin);

    return toQuizData(quiz, req.getDate(), false);
  }

  @Transactional
  public AdminQuizData updateQuiz(Long quizId, UpdateQuizRequest req) {
    QuizEntity quiz = requireQuiz(quizId);

    if (req.getQuestion() != null && !req.getQuestion().isBlank()) {
      quiz.setQuestion(req.getQuestion());
    }
    if (req.getOptions() != null) {
      quiz.setOption1(optionMap(req.getOptions().get(0)));
      quiz.setOption2(optionMap(req.getOptions().get(1)));
      quiz.setOption3(optionMap(req.getOptions().get(2)));
      quiz.setOption4(optionMap(req.getOptions().get(3)));
    }
    if (req.getAnswerOptionIndex() != null) {
      quiz.setAnswerOptionIndex(req.getAnswerOptionIndex());
    }

    if (req.getExplanation() != null) {
      quiz.setExplanation(req.getExplanation());
    }

    quiz = quizRepository.save(quiz);
    LocalDate date =
        bulletinRepository.findByQuizId(quizId).map(BulletinEntity::getDate).orElse(null);
    return toQuizData(quiz, date, quizAttemptRepository.existsByQuizId(quizId));
  }

  @Transactional
  public void deleteQuiz(Long quizId) {
    QuizEntity quiz = requireQuiz(quizId);
    if (quizAttemptRepository.existsByQuizId(quizId)) {
      throw new IllegalStateException("Quiz has user attempts and can no longer be deleted.");
    }
    bulletinRepository
        .findByQuizId(quizId)
        .ifPresent(
            bulletin -> {
              bulletin.setQuiz(null);
              bulletinRepository.save(bulletin);
            });
    quizRepository.delete(quiz);
  }

  /** Quizzes joined with their bulletin dates, optionally filtered by locality and/or date. */
  @Transactional(readOnly = true)
  public List<AdminQuizData> listQuizzes(Long localityId, LocalDate date) {
    return bulletinsFor(localityId, date).stream()
        .filter(b -> b.getQuiz() != null)
        .map(
            b ->
                toQuizData(
                    b.getQuiz(),
                    b.getDate(),
                    quizAttemptRepository.existsByQuizId(b.getQuiz().getId())))
        .collect(Collectors.toList());
  }

  // -------------------------------------------------------------- bulletins

  @Transactional(readOnly = true)
  public List<AdminBulletinData> listBulletins(Long localityId, LocalDate date) {
    return bulletinsFor(localityId, date).stream()
        .map(this::toBulletinData)
        .collect(Collectors.toList());
  }

  /** Edits the AI weather summary and syncs the text of the linked BULLETIN feed post. */
  @Transactional
  public AdminBulletinData updateSummary(Long bulletinId, String summaryText) {
    BulletinEntity bulletin =
        bulletinRepository
            .findById(bulletinId)
            .orElseThrow(
                () ->
                    new DownstreamServiceException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown bulletin: " + bulletinId));
    Map<String, Object> summary =
        bulletin.getSummary() != null ? new HashMap<>(bulletin.getSummary()) : new HashMap<>();
    summary.put("text", summaryText);
    bulletin.setSummary(summary);
    bulletin = bulletinRepository.save(bulletin);

    for (FeedPostEntity post : feedPostRepository.findBulletinPosts(bulletinId)) {
      if (post.getContent() != null) {
        post.getContent().setText(summaryText);
        feedPostRepository.save(post);
      }
    }
    return toBulletinData(bulletin);
  }

  /** The saved-user localities — the set the daily job covers and ops creates quizzes for. */
  @Transactional(readOnly = true)
  public List<AdminLocalityOptionData> listUserLocalities() {
    return userRepository.findDistinctUserLocalities().stream()
        .map(
            l ->
                AdminLocalityOptionData.builder()
                    .id(l.getId())
                    .name(l.getName())
                    .hashtag(l.getHashtag())
                    .build())
        .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
        .collect(Collectors.toList());
  }

  /** Coverage grid for a date range: which localities have a quiz on each day. */
  @Transactional(readOnly = true)
  public WeekCoverageData getWeekCoverage(LocalDate from, LocalDate to) {
    List<Locality> localities = userRepository.findDistinctUserLocalities();
    List<Object[]> covered = bulletinRepository.findCoveredLocalityDatePairs(from, to);

    // Build a set of "localityId_date" keys for O(1) lookup
    java.util.Set<String> coveredKeys =
        covered.stream().map(r -> r[0] + "_" + r[1]).collect(Collectors.toSet());

    List<LocalDate> dates = from.datesUntil(to.plusDays(1)).collect(Collectors.toList());

    List<WeekCoverageData.LocalityCoverage> localityCoverage =
        localities.stream()
            .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
            .map(
                loc -> {
                  List<WeekCoverageData.DateSlot> slots =
                      dates.stream()
                          .map(
                              d ->
                                  new WeekCoverageData.DateSlot(
                                      d, coveredKeys.contains(loc.getId() + "_" + d)))
                          .collect(Collectors.toList());
                  long missing = slots.stream().filter(s -> !s.hasQuiz()).count();
                  return new WeekCoverageData.LocalityCoverage(
                      loc.getId(), loc.getName(), loc.getHashtag(), slots, missing);
                })
            .collect(Collectors.toList());

    long totalReady =
        localityCoverage.stream()
            .flatMap(l -> l.dates().stream())
            .filter(WeekCoverageData.DateSlot::hasQuiz)
            .count();
    long totalExpected = (long) localities.size() * dates.size();
    return new WeekCoverageData(
        from, to, totalExpected, totalReady, totalExpected - totalReady, localityCoverage);
  }

  // ---------------------------------------------------------------- helpers

  private List<BulletinEntity> bulletinsFor(Long localityId, LocalDate date) {
    if (localityId != null && date != null) {
      return bulletinRepository
          .findByLocalityIdAndDate(localityId, date)
          .map(List::of)
          .orElse(List.of());
    }
    if (date != null) {
      return bulletinRepository.findByDateOrderByLocalityId(date);
    }
    if (localityId != null) {
      return bulletinRepository.findByLocalityIdOrderByDateDesc(localityId);
    }
    return bulletinRepository.findAll(Sort.by(Sort.Direction.DESC, "date"));
  }

  private QuizEntity requireQuiz(Long quizId) {
    return quizRepository
        .findById(quizId)
        .orElseThrow(
            () ->
                new DownstreamServiceException(
                    HttpStatus.NOT_FOUND, "NOT_FOUND", "Unknown quiz: " + quizId));
  }

  private Map<String, Object> optionMap(String text) {
    Map<String, Object> option = new HashMap<>();
    option.put("text", text);
    return option;
  }

  private AdminQuizData toQuizData(QuizEntity quiz, LocalDate date, boolean hasAttempts) {
    return AdminQuizData.builder()
        .id(quiz.getId())
        .localityId(quiz.getLocality().getId())
        .localityName(quiz.getLocality().getName())
        .hashtag(quiz.getLocality().getHashtag())
        .date(date)
        .question(quiz.getQuestion())
        .options(BulletinViewMapper.optionTexts(quiz))
        .answerOptionIndex(quiz.getAnswerOptionIndex())
        .explanation(quiz.getExplanation())
        .hasAttempts(hasAttempts)
        .build();
  }

  private AdminBulletinData toBulletinData(BulletinEntity bulletin) {
    Object summaryText = bulletin.getSummary() != null ? bulletin.getSummary().get("text") : null;
    return AdminBulletinData.builder()
        .id(bulletin.getId())
        .localityId(bulletin.getLocality().getId())
        .localityName(bulletin.getLocality().getName())
        .hashtag(bulletin.getLocality().getHashtag())
        .date(bulletin.getDate())
        .weather(bulletin.getPeriodicData() != null ? bulletin.getPeriodicData().getData() : null)
        .weatherSource(
            bulletin.getPeriodicData() != null ? bulletin.getPeriodicData().getSource() : null)
        .summary(summaryText != null ? summaryText.toString() : null)
        .quizId(bulletin.getQuiz() != null ? bulletin.getQuiz().getId() : null)
        .build();
  }
}
