package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Point;
import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;
import org.smalltech.hashtaglocal_backend.entity.FeedPostContentEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.PeriodicDataEntity;
import org.smalltech.hashtaglocal_backend.event.BulletinPublishedEvent;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.repository.BulletinRepository;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.PeriodicDataRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherProvider;
import org.smalltech.hashtaglocal_backend.service.weather.WeatherSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the daily bulletin for every locality that saved users belong to: weather snapshot →
 * {@code periodic_data}, Groq summary → {@code bulletins}, then a system BULLETIN feed post so the
 * bulletin appears as a chat message in the locality's feed. Idempotent — a re-run skips localities
 * already covered for the day. Nothing here runs on the user request path.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BulletinGenerationService {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final String TYPE_DAILY = "DAILY";
  private static final String DATA_TYPE_WEATHER = "WEATHER";

  private final UserRepository userRepository;
  private final LocationRepository locationRepository;
  private final PeriodicDataRepository periodicDataRepository;
  private final BulletinRepository bulletinRepository;
  private final FeedPostRepository feedPostRepository;
  private final WeatherProvider weatherProvider;
  private final GroqClient groqClient;
  private final FeedService feedService;
  private final ApplicationEventPublisher eventPublisher;
  private final JavaMailSender mailSender;

  @Value("${bulletin.alert.admin-email:${account.deletion.admin-email:}}")
  private String alertAdminEmail;

  @Value("${bulletin.alert.from-email:${account.deletion.from-email:}}")
  private String alertFromEmail;

  /** Runs the daily generation over all saved-user localities. Returns a per-run summary. */
  public GenerationResult generateForAllUserLocalities() {
    LocalDate today = LocalDate.now(IST);
    List<Locality> localities = userRepository.findDistinctUserLocalities();
    log.info(
        "Bulletin generation started for {} user localities (date={})", localities.size(), today);

    int generated = 0;
    int skipped = 0;
    int failed = 0;
    List<String> failures = new ArrayList<>();
    List<String> fallbacks = new ArrayList<>();
    for (Locality locality : localities) {
      try {
        switch (generateForLocality(locality, today)) {
          case GENERATED -> generated++;
          case GENERATED_WITH_FALLBACK -> {
            generated++;
            fallbacks.add(locality.getHashtag());
            log.warn(
                "Bulletin for locality {} ({}) used the fallback summary",
                locality.getId(),
                locality.getHashtag());
          }
          case SKIPPED -> skipped++;
        }
        // Gentle pacing between external API calls (same approach as LocationMetadataUpdateJob).
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Bulletin generation interrupted");
        break;
      } catch (Exception e) {
        failed++;
        failures.add(locality.getHashtag() + ": " + e.getMessage());
        log.error(
            "Bulletin generation failed for locality {} ({}): {}",
            locality.getId(),
            locality.getHashtag(),
            e.getMessage());
      }
    }
    log.info(
        "Bulletin generation finished: {} generated ({} fallback summaries), {} skipped, {} failed",
        generated,
        fallbacks.size(),
        skipped,
        failed);
    GenerationResult result =
        GenerationResult.builder()
            .date(today)
            .totalLocalities(localities.size())
            .generated(generated)
            .skipped(skipped)
            .failed(failed)
            .fallbackSummaries(fallbacks.size())
            .build();
    if (failed > 0 || !fallbacks.isEmpty()) {
      sendFailureAlert(result, failures, fallbacks);
    }
    return result;
  }

  /**
   * Best-effort admin email when one or more localities fail or get the fallback summary — the run
   * itself already succeeded for everything it could, so a broken mail send here must never fail
   * the job.
   */
  private void sendFailureAlert(
      GenerationResult result, List<String> failures, List<String> fallbacks) {
    if (alertAdminEmail == null || alertAdminEmail.isBlank()) {
      log.warn("bulletin.alert.admin-email is not configured; skipping bulletin failure email");
      return;
    }
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      if (alertFromEmail != null && !alertFromEmail.isBlank()) {
        message.setFrom(alertFromEmail);
      }
      message.setTo(alertAdminEmail);
      List<String> problems = new ArrayList<>();
      if (!failures.isEmpty()) {
        problems.add(failures.size() + "/" + result.getTotalLocalities() + " localities failed");
      }
      if (!fallbacks.isEmpty()) {
        problems.add(
            fallbacks.size()
                + "/"
                + result.getTotalLocalities()
                + " localities got the fallback summary");
      }
      message.setSubject(
          "#local bulletin: " + String.join(", ", problems) + " (" + result.getDate() + ")");
      String failureDetail =
          failures.isEmpty()
              ? ""
              : "\n\nFailures:\n"
                  + String.join("\n", failures)
                  + "\n\nRe-run is idempotent: POST /admin/bulletin/generate";
      String fallbackDetail =
          fallbacks.isEmpty()
              ? ""
              : "\n\nFallback summaries (Groq failed, so the plain weather line was used). A re-run"
                  + " does not replace these; edit them in the ops portal:\n"
                  + String.join("\n", fallbacks);
      message.setText(
          "The daily bulletin weather run for "
              + result.getDate()
              + " had "
              + result.getFailed()
              + " failure(s) and "
              + result.getFallbackSummaries()
              + " fallback summary(ies) out of "
              + result.getTotalLocalities()
              + " localities ("
              + result.getGenerated()
              + " generated, "
              + result.getSkipped()
              + " skipped)."
              + failureDetail
              + fallbackDetail);
      mailSender.send(message);
      log.info("Bulletin failure alert emailed to {}", alertAdminEmail);
    } catch (Exception e) {
      log.warn("Failed to send bulletin failure alert email: {}", e.getMessage());
    }
  }

  /** Generates one locality's bulletin for {@code date}. Returns SKIPPED if already covered. */
  @Transactional
  public GenerationOutcome generateForLocality(Locality locality, LocalDate date) {
    if (periodicDataRepository
        .findByLocalityIdAndDateAndDataType(locality.getId(), date, DATA_TYPE_WEATHER)
        .isPresent()) {
      ensureFeedPost(locality, date);
      return GenerationOutcome.SKIPPED;
    }

    double[] latLng = resolveCoordinates(locality);
    WeatherSnapshot weather = weatherProvider.fetchDaily(latLng[0], latLng[1]);

    PeriodicDataEntity periodicData =
        periodicDataRepository.save(
            PeriodicDataEntity.builder()
                .locality(locality)
                .date(date)
                .type(TYPE_DAILY)
                .dataType(DATA_TYPE_WEATHER)
                .data(weather.toDataMap())
                .source(weather.getSource())
                .build());

    List<String> recentSummaries =
        bulletinRepository.findRecentWeatherSummaries(locality.getId(), 5);
    GroqClient.WeatherSummary weatherSummary =
        groqClient.generateWeatherSummary(locality.getName(), weather, recentSummaries);
    String summaryText = weatherSummary.text();
    Map<String, Object> summary = new HashMap<>();
    summary.put("text", summaryText);

    // Upsert: the admin may already have created the shell row when attaching a quiz for today.
    BulletinEntity bulletin =
        bulletinRepository
            .findByLocalityIdAndDate(locality.getId(), date)
            .orElseGet(() -> BulletinEntity.builder().locality(locality).date(date).build());
    bulletin.setPeriodicData(periodicData);
    bulletin.setSummary(summary);
    bulletin = bulletinRepository.save(bulletin);

    publishFeedPost(locality, bulletin, summaryText);
    return weatherSummary.fallback()
        ? GenerationOutcome.GENERATED_WITH_FALLBACK
        : GenerationOutcome.GENERATED;
  }

  /**
   * Publishes the feed post for an already-generated bulletin if it's missing — covers re-runs
   * where weather existed but the feed post step failed.
   */
  private void ensureFeedPost(Locality locality, LocalDate date) {
    bulletinRepository
        .findByLocalityIdAndDate(locality.getId(), date)
        .filter(b -> b.getPeriodicData() != null)
        .filter(b -> !feedPostRepository.existsBulletinPost(b.getId()))
        .ifPresent(
            b -> {
              Object text = b.getSummary() != null ? b.getSummary().get("text") : null;
              publishFeedPost(locality, b, text != null ? text.toString() : null);
            });
  }

  private void publishFeedPost(Locality locality, BulletinEntity bulletin, String summaryText) {
    if (feedPostRepository.existsBulletinPost(bulletin.getId())) {
      return;
    }
    FeedPostContentEntity content =
        FeedPostContentEntity.builder()
            .bulletin(bulletin)
            .title("Daily Bulletin — " + locality.getName())
            .text(summaryText)
            .build();
    feedService.createSystemPost(locality, FeedPostKind.BULLETIN, content);

    // Only today's bulletin is worth a push: a late repair run must not notify about an old one.
    if (bulletin.getDate().equals(LocalDate.now(IST))) {
      eventPublisher.publishEvent(new BulletinPublishedEvent(bulletin.getId(), locality.getId()));
    }
  }

  /** Locality coordinates: boundary centroid, else any saved location inside it. */
  private double[] resolveCoordinates(Locality locality) {
    if (locality.getGeoBoundary() != null) {
      Point centroid = locality.getGeoBoundary().getCentroid();
      if (centroid != null && !centroid.isEmpty()) {
        return new double[] {centroid.getY(), centroid.getX()};
      }
    }
    Optional<org.smalltech.hashtaglocal_backend.entity.Location> location =
        locationRepository.findFirstByLocalityId(locality.getId());
    if (location.isPresent() && location.get().getPoint() != null) {
      Point p = location.get().getPoint();
      return new double[] {p.getY(), p.getX()};
    }
    throw new IllegalStateException(
        "No coordinates resolvable for locality "
            + locality.getId()
            + " ("
            + locality.getHashtag()
            + ")");
  }

  public enum GenerationOutcome {
    GENERATED,
    GENERATED_WITH_FALLBACK,
    SKIPPED
  }

  @Data
  @Builder
  public static class GenerationResult {
    private LocalDate date;
    private int totalLocalities;
    private int generated;
    private int skipped;
    private int failed;
    private int fallbackSummaries;
  }
}
