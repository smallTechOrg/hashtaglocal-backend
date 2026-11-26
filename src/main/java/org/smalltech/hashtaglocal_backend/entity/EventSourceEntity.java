package org.smalltech.hashtaglocal_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * A scraper0 site action that event ingestion reads, editable from the Ops Dashboard (Event
 * sources). Each enabled source is read on every ingestion run; the {@code last_*} columns record
 * how the latest run went.
 *
 * <p>Seeded with the three original portals by {@code db/migration/V3__event_sources.sql}.
 */
@Entity
@Table(
    name = "event_sources",
    uniqueConstraints =
        @UniqueConstraint(
            name = "event_sources_site_action_key",
            columnNames = {"site_id", "action"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EventSourceEntity {

  public static final String DEFAULT_ACTION = "fetch_events";
  public static final String STATUS_OK = "ok";
  public static final String STATUS_PARTIAL = "partial";
  public static final String STATUS_ERROR = "error";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String name;

  /** scraper0 site id, e.g. {@code teameverest-ngo}. */
  @Column(name = "site_id", nullable = false)
  private String siteId;

  /** scraper0 read action, normally {@code fetch_events}. */
  @Builder.Default
  @Column(nullable = false, length = 100)
  private String action = DEFAULT_ACTION;

  /** AI relevance filter sent as {@code options.event_filter}. */
  @Column(name = "event_filter", columnDefinition = "text")
  private String eventFilter;

  /** Extra context for the filter, sent as {@code options.category_filter}. */
  @Column(name = "category_filter", columnDefinition = "text")
  private String categoryFilter;

  @Builder.Default
  @Column(name = "include_online", nullable = false)
  private boolean includeOnline = false;

  @Builder.Default
  @Column(name = "freshness_hours", nullable = false)
  private int freshnessHours = 48;

  @Builder.Default
  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "last_run_at")
  private LocalDateTime lastRunAt;

  /** {@code ok}, {@code partial}, {@code error}, or null before the first run. */
  @Column(name = "last_status", length = 20)
  private String lastStatus;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "last_item_count")
  private Integer lastItemCount;

  @Column(name = "last_imported_count")
  private Integer lastImportedCount;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;
}
