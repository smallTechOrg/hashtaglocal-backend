package org.smalltech.hashtaglocal_backend.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.springframework.data.jpa.repository.JpaRepository;
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

  /** Returns all events sourced from the given platform (e.g., "mybharat.gov.in"). */
  List<EventEntity> findByPlatform(String platform);

  /** Returns all events matching the given activity type. */
  List<EventEntity> findByEventType(EventTypeModel eventType);

  /** Returns all events whose start time is on or after the given date-time. */
  List<EventEntity> findByStartTimeGreaterThanEqual(LocalDateTime dateTime);
}
