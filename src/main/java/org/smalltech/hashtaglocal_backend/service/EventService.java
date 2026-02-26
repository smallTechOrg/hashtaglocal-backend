package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service layer for event read and write operations.
 *
 * <p>Acts as the single point of access to {@link EventRepository} for the rest of the application.
 * Controllers and import services call this instead of the repository directly, keeping database
 * logic in one place.
 */
@Service
@RequiredArgsConstructor
public class EventService {

  private final EventRepository eventRepository;

  /**
   * Returns all events in the database.
   *
   * <p>Read-only transaction — no locking overhead for a simple SELECT.
   */
  @Transactional(readOnly = true)
  public List<EventEntity> getAll() {
    return eventRepository.findAll();
  }

  /** Persists a single event and returns the saved entity (with generated id). */
  @Transactional
  public EventEntity save(EventEntity event) {
    return eventRepository.save(event);
  }

  /**
   * Persists a batch of events in a single transaction.
   *
   * <p>Used by {@link EventImportService} to bulk-insert all rows from a CSV file at once, reducing
   * the number of round-trips to the database.
   */
  @Transactional
  public List<EventEntity> saveAll(List<EventEntity> events) {
    return eventRepository.saveAll(events);
  }
}
