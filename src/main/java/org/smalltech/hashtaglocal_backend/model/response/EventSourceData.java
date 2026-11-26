package org.smalltech.hashtaglocal_backend.model.response;

import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;

/** An event source as the admin API returns it (snake_case on the wire). */
public record EventSourceData(
    Long id,
    String name,
    String siteId,
    String action,
    String eventFilter,
    String categoryFilter,
    boolean includeOnline,
    int freshnessHours,
    boolean enabled,
    LocalDateTime lastRunAt,
    String lastStatus,
    String lastError,
    Integer lastItemCount,
    Integer lastImportedCount,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {

  public static EventSourceData from(EventSourceEntity e) {
    return new EventSourceData(
        e.getId(),
        e.getName(),
        e.getSiteId(),
        e.getAction(),
        e.getEventFilter(),
        e.getCategoryFilter(),
        e.isIncludeOnline(),
        e.getFreshnessHours(),
        e.isEnabled(),
        e.getLastRunAt(),
        e.getLastStatus(),
        e.getLastError(),
        e.getLastItemCount(),
        e.getLastImportedCount(),
        e.getCreatedAt(),
        e.getUpdatedAt());
  }
}
