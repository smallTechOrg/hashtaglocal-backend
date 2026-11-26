package org.smalltech.hashtaglocal_backend.service.scraper0;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/**
 * The scraper0 v2 shapes this backend reads (see web-scraper spec/api-v2.md). Field names are
 * camelCase here and snake_case on the wire.
 */
public final class Scraper0Models {

  private Scraper0Models() {}

  /** {@code GET /v2/sites} entry. */
  public record SiteSummary(
      String siteId, String baseUrl, String name, List<ActionSummary> actions) {}

  public record ActionSummary(String name, String kind, String status) {}

  /** {@code POST /v2/sites/{site}/actions/{action}/read} (list action) result. */
  public record ReadResult(
      String siteId,
      String action,
      Integer version,
      List<Item> items,
      boolean complete,
      String scrapedAt,
      String refreshJobId,
      String health,
      String aiError) {

    public List<Item> itemsOrEmpty() {
      return items == null ? List.of() : items;
    }
  }

  public record Item(String id, String updatedAt, EventData data) {}

  /** {@code EventData}; times are ISO-8601 UTC strings. */
  public record EventData(
      String name,
      String startTime,
      String endTime,
      String address,
      String link,
      String organisation,
      String image,
      String category,
      String description,
      Double latitude,
      Double longitude,
      Boolean isOnline,
      String price,
      String sourceUrl) {}

  /**
   * A write that scraper0 accepted: {@code succeeded} (200, with {@code output}) or {@code running}
   * (202, still in progress).
   */
  public record WriteResult(
      String status, JsonNode output, String idempotencyKey, String jobId, boolean replayed) {

    public boolean succeeded() {
      return "succeeded".equals(status);
    }
  }
}
