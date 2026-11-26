package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.config.TimeConstants;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.model.request.EventSourceInput;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceData;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceTestResult;
import org.smalltech.hashtaglocal_backend.repository.EventSourceRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.EventData;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.SiteSummary;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Properties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scraper0 site actions that event ingestion reads ({@code event_sources}): CRUD for the Ops
 * Dashboard, the read each ingestion run makes, a dry-run test, and the per-source run record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventSourceService {

  static final int TEST_ITEM_LIMIT = 20;
  static final int MAX_ERROR_LENGTH = 2000;

  private final EventSourceRepository repository;
  private final Scraper0Client scraper0;
  private final Scraper0Properties properties;

  /** Two sources with the same {@code (site_id, action)}. */
  public static class DuplicateSourceException extends RuntimeException {
    public DuplicateSourceException(String siteId, String action) {
      super("An event source for " + siteId + " / " + action + " already exists");
    }
  }

  /** No source with this id. */
  public static class SourceNotFoundException extends RuntimeException {
    public SourceNotFoundException(Long id) {
      super("Event source not found: " + id);
    }
  }

  // ---------------------------------------------------------------------------
  // CRUD
  // ---------------------------------------------------------------------------

  public List<EventSourceData> list() {
    return repository.findAllByOrderByIdAsc().stream().map(EventSourceData::from).toList();
  }

  @Transactional
  public EventSourceData create(EventSourceInput input) {
    EventSourceEntity source = new EventSourceEntity();
    apply(source, input, true);
    if (repository.existsBySiteIdAndAction(source.getSiteId(), source.getAction())) {
      throw new DuplicateSourceException(source.getSiteId(), source.getAction());
    }
    return EventSourceData.from(repository.save(source));
  }

  @Transactional
  public EventSourceData update(Long id, EventSourceInput input) {
    EventSourceEntity source = find(id);
    apply(source, input, false);
    if (repository.existsBySiteIdAndActionAndIdNot(source.getSiteId(), source.getAction(), id)) {
      throw new DuplicateSourceException(source.getSiteId(), source.getAction());
    }
    return EventSourceData.from(repository.save(source));
  }

  @Transactional
  public void delete(Long id) {
    repository.delete(find(id));
  }

  /** Reads the source now and returns what came back; nothing is imported or recorded. */
  public EventSourceTestResult test(Long id) {
    ReadResult result = read(find(id));
    List<EventSourceTestResult.Item> items =
        result.itemsOrEmpty().stream()
            .limit(TEST_ITEM_LIMIT)
            .map(
                item -> {
                  EventData d = item.data();
                  return d == null
                      ? new EventSourceTestResult.Item(
                          item.id(), null, null, null, null, null, null, null, null)
                      : new EventSourceTestResult.Item(
                          item.id(),
                          d.name(),
                          d.startTime(),
                          d.endTime(),
                          d.address(),
                          d.link(),
                          d.image(),
                          d.category(),
                          d.isOnline());
                })
            .toList();
    return new EventSourceTestResult(
        result.itemsOrEmpty().size(),
        result.complete(),
        result.scrapedAt(),
        result.health(),
        result.aiError(),
        items);
  }

  /** {@code GET /v2/sites?q=} from scraper0. */
  public List<SiteSummary> scraper0Sites(String q) {
    return scraper0.listSites(q);
  }

  // ---------------------------------------------------------------------------
  // Ingestion
  // ---------------------------------------------------------------------------

  public List<EventSourceEntity> enabledSources() {
    return repository.findAllByEnabledTrueOrderByIdAsc();
  }

  /**
   * Reads the source's action with its options: {@code event_filter}, {@code category_filter},
   * {@code categories} (every {@link EventTypeModel} name), {@code include_online} and {@code from}
   * = today in Asia/Kolkata.
   */
  public ReadResult read(EventSourceEntity source) {
    return scraper0.readItems(
        source.getSiteId(), source.getAction(), options(source), source.getFreshnessHours());
  }

  static Map<String, Object> options(EventSourceEntity source) {
    Map<String, Object> options = new LinkedHashMap<>();
    if (notBlank(source.getEventFilter())) {
      options.put("event_filter", source.getEventFilter());
    }
    if (notBlank(source.getCategoryFilter())) {
      options.put("category_filter", source.getCategoryFilter());
    }
    options.put("categories", Arrays.stream(EventTypeModel.values()).map(Enum::name).toList());
    options.put("include_online", source.isIncludeOnline());
    options.put("from", LocalDate.now(TimeConstants.APP_ZONE).toString());
    return options;
  }

  /** Stores how the latest run of this source went. */
  @Transactional
  public void recordRun(
      Long sourceId, String status, String error, Integer itemCount, Integer importedCount) {
    repository
        .findById(sourceId)
        .ifPresent(
            source -> {
              source.setLastRunAt(LocalDateTime.now());
              source.setLastStatus(status);
              source.setLastError(truncate(error));
              source.setLastItemCount(itemCount);
              source.setLastImportedCount(importedCount);
              repository.save(source);
            });
  }

  // ---------------------------------------------------------------------------

  private EventSourceEntity find(Long id) {
    return repository.findById(id).orElseThrow(() -> new SourceNotFoundException(id));
  }

  private void apply(EventSourceEntity source, EventSourceInput input, boolean creating) {
    source.setName(input.name().trim());
    source.setSiteId(input.siteId().trim());
    source.setAction(
        notBlank(input.action()) ? input.action().trim() : EventSourceEntity.DEFAULT_ACTION);
    source.setEventFilter(blankToNull(input.eventFilter()));
    source.setCategoryFilter(blankToNull(input.categoryFilter()));
    if (input.includeOnline() != null) {
      source.setIncludeOnline(input.includeOnline());
    } else if (creating) {
      source.setIncludeOnline(false);
    }
    if (input.freshnessHours() != null) {
      source.setFreshnessHours(input.freshnessHours());
    } else if (creating) {
      source.setFreshnessHours(properties.getFreshnessHours());
    }
    if (input.enabled() != null) {
      source.setEnabled(input.enabled());
    } else if (creating) {
      source.setEnabled(true);
    }
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String blankToNull(String s) {
    return notBlank(s) ? s.trim() : null;
  }

  private static String truncate(String s) {
    return s == null || s.length() <= MAX_ERROR_LENGTH ? s : s.substring(0, MAX_ERROR_LENGTH);
  }
}
