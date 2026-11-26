package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;

/**
 * Result of {@code POST /admin/event-sources/{id}/test}: what a read returns right now (nothing is
 * imported). {@code items} holds the first 20.
 */
public record EventSourceTestResult(
    int itemCount,
    boolean complete,
    String scrapedAt,
    String health,
    String aiError,
    List<Item> items) {

  public record Item(
      String id,
      String name,
      String startTime,
      String endTime,
      String address,
      String link,
      String image,
      String category,
      Boolean isOnline) {}
}
