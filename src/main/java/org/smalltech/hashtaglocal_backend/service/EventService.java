package org.smalltech.hashtaglocal_backend.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.response.EventData;
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
  private final GCSService gcsService;

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

  /**
   * Returns all events that have a resolved location, mapped to {@link EventData}.
   *
   * <p>Events without a linked {@link Location} (geocoding still pending) are excluded.
   */
  @Transactional(readOnly = true)
  public List<EventData> getAllAsEventData() {
    return eventRepository.findAll().stream()
        .filter(e -> e.getLocation() != null)
        .map(this::toEventData)
        .toList();
  }

  /**
   * Converts a database {@link EventEntity} into the API response model {@link EventData}.
   *
   * <p>Extracts lat/lng from the PostGIS Point stored in the linked {@link Location}. If no
   * Location has been linked yet (geocoding pending), the {@code location} field in the response is
   * left null.
   */
  public EventData toEventData(EventEntity entity) {
    Location loc = entity.getLocation();
    Locality locality = loc.getLocality();
    EventData.LocalityData localityData =
        locality != null
            ? EventData.LocalityData.builder().hashtags(List.of(locality.getHashtag())).build()
            : null;
    EventData.LocationData locationData =
        EventData.LocationData.builder()
            .lat(loc.getPoint().getY())
            .lng(loc.getPoint().getX())
            .name(loc.getName())
            .locality(localityData)
            .build();

    // Generate a fresh signed URL each time — GCS paths in DB never expire, but signed URLs do.
    MediaEntity media = entity.getMedia();
    String imageUrl = media != null ? gcsService.generateSignedUrl(media.getUrl()) : null;

    return EventData.builder()
        .id(entity.getId())
        .name(entity.getName())
        .organisation(entity.getOrganisation())
        .imageUrl(imageUrl)
        .portal(entity.getPortal() != null ? entity.getPortal().name() : null)
        .type(entity.getType() != null ? entity.getType().name() : null)
        .startTime(entity.getStartTime())
        .endTime(entity.getEndTime())
        .location(locationData)
        .address(entity.getAddress())
        .link(entity.getLink())
        .metaData(entity.getMetaData())
        .build();
  }
}
