package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link EventEntity}.
 *
 * <p>Provides standard CRUD via {@link JpaRepository} plus named query methods for common filters.
 * Method names are translated to SQL automatically by Spring Data — no manual query writing needed.
 */
@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {

  /** Returns all events organised by the given organisation name (case-sensitive). */
  List<EventEntity> findByOrganisation(String organisation);

  /** Returns all events sourced from the given portal. */
  List<EventEntity> findByPortal(EventPortalModel portal);

  /** Returns all events matching the given activity type. */
  List<EventEntity> findByType(EventTypeModel type);

  /** Returns all events whose start time is on or after the given date-time. */
  List<EventEntity> findByStartTimeGreaterThanEqual(LocalDateTime dateTime);

  /** Returns active events that have a raw address but no geocoded location yet. */
  List<EventEntity> findByLocationIsNullAndAddressIsNotNullAndActiveTrue();

  /**
   * Returns {@code true} if an event with the same name and start time already exists.
   *
   * <p>Used for deduplication during import — prevents re-inserting events that were already
   * imported from a previous scrape of the same portal.
   */
  boolean existsByNameAndStartTime(String name, LocalDateTime startTime);

  /**
   * Returns events that have NO approval row at all. Such events are invisible to both the public
   * API (which needs APPROVED) and the ops review queue (which queries by approval status), so they
   * must be surfaced as implicitly-pending in ops to avoid silently lost events. (Normally every
   * imported event gets an approval row; orphans arise from events imported before that logic, or a
   * failed import.)
   */
  @Query(
      "SELECT e FROM EventEntity e "
          + "WHERE NOT EXISTS (SELECT 1 FROM EventApprovalEntity a WHERE a.eventId = e.id) "
          + "AND e.active = true")
  List<EventEntity> findWithoutApprovalRow();
}
