package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.dto.AdminCreateEventRequest;
import org.smalltech.hashtaglocal_backend.dto.AdminEditEventRequest;
import org.smalltech.hashtaglocal_backend.dto.EventApproveRequest;
import org.smalltech.hashtaglocal_backend.entity.EventApprovalEntity;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventApprovalStatus;
import org.smalltech.hashtaglocal_backend.model.EventPortalModel;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.EventListResponseData;
import org.smalltech.hashtaglocal_backend.repository.EventApprovalRepository;
import org.smalltech.hashtaglocal_backend.repository.EventRepository;
import org.smalltech.hashtaglocal_backend.service.EventGeocodingService;
import org.smalltech.hashtaglocal_backend.service.EventService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Admin endpoints for reviewing scraped events before they appear on the public site.
 *
 * <p>All routes require {@code ROLE_ADMIN} — enforced by the {@code /admin/**} rule in {@link
 * org.smalltech.hashtaglocal_backend.config.SecurityConfig}.
 *
 * <p>Workflow:
 *
 * <ol>
 *   <li>Events are ingested by the scraper and land in {@code event_approvals} with {@code PENDING}
 *       status.
 *   <li>Admin reviews pending events via {@code GET /admin/event/pending}.
 *   <li>Admin approves (optionally setting a display name) or rejects each event.
 *   <li>Only {@code APPROVED} events are returned by the public {@code GET /api/v1/events}.
 * </ol>
 */
@RestController
@RequestMapping("/admin")
@Tag(
    name = "Admin — Events",
    description = "Admin APIs for approving and rejecting scraped events.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class EventAdminController {

  private final EventService eventService;
  private final EventApprovalRepository eventApprovalRepository;
  private final EventRepository eventRepository;
  private final EventGeocodingService eventGeocodingService;
  private final org.smalltech.hashtaglocal_backend.service.EventImageService eventImageService;

  /**
   * Updates editable fields of an existing event. All fields are optional — only non-null values
   * are applied. If {@code address} changes the event's geocoded location is cleared and geocoding
   * is re-triggered.
   */
  @PutMapping("/event/{eventId}")
  @Operation(
      summary = "Edit an event",
      description =
          "Updates event fields. Null fields are ignored. Address changes clear the geocoded location and trigger re-geocoding.")
  public ResponseEntity<NewAPIResponse<Long>> updateEvent(
      @PathVariable Long eventId, @RequestBody AdminEditEventRequest request) {

    EventEntity event =
        eventRepository
            .findById(eventId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Event not found: " + eventId));

    boolean addressChanged = false;

    if (request.getName() != null && !request.getName().isBlank()) {
      event.setName(request.getName().strip());
    }
    if (request.getOrganisation() != null) {
      event.setOrganisation(
          request.getOrganisation().isBlank() ? null : request.getOrganisation().strip());
    }
    if (request.getAddress() != null && !request.getAddress().isBlank()) {
      String newAddress = request.getAddress().strip();
      if (!newAddress.equals(event.getAddress())) {
        event.setAddress(newAddress);
        event.setLocation(null);
        addressChanged = true;
      }
    }
    if (request.getStartTime() != null) {
      event.setStartTime(request.getStartTime());
    }
    if (request.getEndTime() != null) {
      event.setEndTime(request.getEndTime());
    }
    if (request.getLink() != null && !request.getLink().isBlank()) {
      event.setLink(request.getLink().strip());
    }
    if (request.getType() != null && !request.getType().isBlank()) {
      try {
        event.setType(EventTypeModel.valueOf(request.getType().trim().toUpperCase(Locale.ENGLISH)));
      } catch (IllegalArgumentException ignored) {
        // keep existing type
      }
    }

    eventRepository.save(event);

    // Update display name in the approval row if provided
    if (request.getDisplayName() != null) {
      eventApprovalRepository
          .findById(eventId)
          .ifPresent(
              approval -> {
                approval.setDisplayName(
                    request.getDisplayName().isBlank() ? null : request.getDisplayName().strip());
                eventApprovalRepository.save(approval);
              });
    }

    if (addressChanged) {
      eventGeocodingService.run();
    }

    return ResponseEntity.ok(NewAPIResponse.<Long>builder().data(eventId).build());
  }

  /**
   * Creates an event manually (bypassing the scraper), auto-approves it, and triggers geocoding so
   * it appears on the public site as soon as a location can be resolved.
   */
  @PostMapping("/event/manual")
  @Operation(
      summary = "Manually create an event",
      description =
          "Creates an event directly via the ops portal (portal=ADMIN), immediately approves it,"
              + " and triggers geocoding. The event will appear in GET /api/v1/events once its"
              + " address has been geocoded.")
  public ResponseEntity<NewAPIResponse<Long>> createEventManually(
      @RequestBody AdminCreateEventRequest request) {

    if (request.getName() == null || request.getName().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
    }
    if (request.getAddress() == null || request.getAddress().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "address is required");
    }
    if (request.getStartTime() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "start_time is required");
    }
    if (request.getLink() == null || request.getLink().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "link is required");
    }
    if (request.getImageUrl() == null || request.getImageUrl().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "image_url is required");
    }

    EventTypeModel eventType;
    try {
      eventType =
          request.getType() != null && !request.getType().isBlank()
              ? EventTypeModel.valueOf(request.getType().trim().toUpperCase(Locale.ENGLISH))
              : EventTypeModel.OTHER;
    } catch (IllegalArgumentException e) {
      eventType = EventTypeModel.OTHER;
    }

    org.smalltech.hashtaglocal_backend.entity.MediaEntity media =
        eventImageService.downloadAndStore(request.getImageUrl().strip());
    if (media == null) {
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "Image could not be downloaded or stored — check the URL and try again");
    }

    EventEntity event =
        EventEntity.builder()
            .name(request.getName().strip())
            .organisation(request.getOrganisation())
            .portal(EventPortalModel.ADMIN)
            .type(eventType)
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .address(request.getAddress().strip())
            .link(request.getLink().strip())
            .media(media)
            .active(true)
            .build();

    EventEntity saved = eventService.saveAll(List.of(event)).get(0);

    // Auto-approve immediately — admin is explicitly creating this event
    EventApprovalEntity approval =
        EventApprovalEntity.builder()
            .eventId(saved.getId())
            .status(EventApprovalStatus.APPROVED)
            .reviewedAt(LocalDateTime.now())
            .build();
    eventApprovalRepository.save(approval);

    // Geocode all events without a location (includes the one just created)
    eventGeocodingService.run();

    return ResponseEntity.ok(NewAPIResponse.<Long>builder().data(saved.getId()).build());
  }

  /**
   * Returns all pending events that have a geocoded location, oldest first. Events still awaiting
   * geocoding are excluded — they cannot be shown on the map yet.
   */
  @GetMapping("/event/pending")
  @Operation(
      summary = "List pending events",
      description =
          "Returns all scraped events awaiting admin review that have a resolved location.")
  public ResponseEntity<NewAPIResponse<EventListResponseData>> getPendingEvents() {
    List<EventApprovalEntity> pendingApprovals =
        eventApprovalRepository.findByStatus(EventApprovalStatus.PENDING);

    Map<Long, EventApprovalEntity> approvalMap =
        pendingApprovals.stream()
            .collect(Collectors.toMap(EventApprovalEntity::getEventId, a -> a));

    var pendingEvents =
        eventService.findByIds(approvalMap.keySet()).stream()
            .filter(e -> e.getLocation() != null)
            .map(e -> eventService.toAdminEventData(e, approvalMap.get(e.getId())));

    // Also surface events that have NO approval row at all — otherwise they are invisible
    // everywhere (not public, not pending, not history) and silently lost. Treat them as
    // implicitly pending (toAdminEventData maps a null approval to PENDING).
    var orphanEvents =
        eventRepository.findWithoutApprovalRow().stream()
            .filter(e -> e.getLocation() != null)
            .map(e -> eventService.toAdminEventData(e, null));

    var events = java.util.stream.Stream.concat(pendingEvents, orphanEvents).toList();

    return ResponseEntity.ok(
        NewAPIResponse.<EventListResponseData>builder()
            .data(EventListResponseData.builder().events(events).build())
            .build());
  }

  /**
   * Approves a pending event and optionally sets a display name override.
   *
   * @param eventId ID of the event to approve (must have an existing approval row)
   * @param request optional body containing a {@code displayName} override
   */
  @PutMapping("/event/{eventId}/approve")
  @Operation(
      summary = "Approve an event",
      description =
          "Marks the event as APPROVED so it appears on the public site. "
              + "Optionally accepts a display_name to override the scraped event name.")
  public ResponseEntity<NewAPIResponse<Long>> approveEvent(
      @PathVariable Long eventId, @RequestBody(required = false) EventApproveRequest request) {

    // Upsert: orphan events (imported before approval rows existed) have no row yet, but still
    // show in the pending queue — approving one must create the row rather than 404.
    EventApprovalEntity approval =
        eventApprovalRepository
            .findById(eventId)
            .orElseGet(() -> requireEventApprovalShell(eventId));

    approval.setStatus(EventApprovalStatus.APPROVED);
    approval.setReviewedAt(LocalDateTime.now());

    if (request != null
        && request.getDisplayName() != null
        && !request.getDisplayName().isBlank()) {
      approval.setDisplayName(request.getDisplayName().strip());
    }

    eventApprovalRepository.save(approval);

    // Mark the event itself as active so it appears on the public site
    eventRepository
        .findById(eventId)
        .ifPresent(
            event -> {
              event.setActive(true);
              eventRepository.save(event);
            });

    return ResponseEntity.ok(NewAPIResponse.<Long>builder().data(eventId).build());
  }

  /**
   * Rejects a pending event so it will not appear on the public site.
   *
   * @param eventId ID of the event to reject
   */
  @PutMapping("/event/{eventId}/reject")
  @Operation(
      summary = "Reject an event",
      description = "Marks the event as REJECTED — it will not appear on the public site.")
  public ResponseEntity<NewAPIResponse<Long>> rejectEvent(@PathVariable Long eventId) {

    // Upsert: orphan events have no approval row yet — rejecting one must create it, not 404.
    EventApprovalEntity approval =
        eventApprovalRepository
            .findById(eventId)
            .orElseGet(() -> requireEventApprovalShell(eventId));

    approval.setStatus(EventApprovalStatus.REJECTED);
    approval.setReviewedAt(LocalDateTime.now());
    eventApprovalRepository.save(approval);

    eventRepository
        .findById(eventId)
        .ifPresent(
            event -> {
              event.setActive(false);
              eventRepository.save(event);
            });

    return ResponseEntity.ok(NewAPIResponse.<Long>builder().data(eventId).build());
  }

  /**
   * Returns all events that have already been reviewed (approved or rejected), newest first. Useful
   * for auditing decisions made in the ops portal.
   */
  /**
   * Builds a fresh approval-row shell for an event that has none yet (an orphan surfaced in the
   * pending queue). Verifies the event itself exists so we never create a dangling approval row.
   */
  private EventApprovalEntity requireEventApprovalShell(Long eventId) {
    if (!eventRepository.existsById(eventId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No event found for id " + eventId);
    }
    return EventApprovalEntity.builder().eventId(eventId).build();
  }

  @GetMapping("/event/history")
  @Operation(
      summary = "List reviewed events",
      description =
          "Returns all APPROVED and REJECTED events ordered by review time (newest first).")
  public ResponseEntity<NewAPIResponse<EventListResponseData>> getEventHistory() {
    List<EventApprovalEntity> reviewedApprovals =
        eventApprovalRepository.findByStatusIn(
            List.of(EventApprovalStatus.APPROVED, EventApprovalStatus.REJECTED));

    Map<Long, EventApprovalEntity> approvalMap =
        reviewedApprovals.stream()
            .collect(Collectors.toMap(EventApprovalEntity::getEventId, a -> a));

    var events =
        eventService.findByIds(approvalMap.keySet()).stream()
            .map(e -> eventService.toAdminEventData(e, approvalMap.get(e.getId())))
            // Newest reviewed first
            .sorted(
                (a, b) -> {
                  var aTime =
                      approvalMap.get(a.getId()) != null
                          ? approvalMap.get(a.getId()).getReviewedAt()
                          : null;
                  var bTime =
                      approvalMap.get(b.getId()) != null
                          ? approvalMap.get(b.getId()).getReviewedAt()
                          : null;
                  if (aTime == null && bTime == null) return 0;
                  if (aTime == null) return 1;
                  if (bTime == null) return -1;
                  return bTime.compareTo(aTime);
                })
            .toList();

    return ResponseEntity.ok(
        NewAPIResponse.<EventListResponseData>builder()
            .data(EventListResponseData.builder().events(events).build())
            .build());
  }
}
