package org.smalltech.hashtaglocal_backend.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enum representing the external portal/platform that an event was sourced from.
 *
 * <p>Stored as a VARCHAR string in the {@code events.portal} column (via {@code EnumType.STRING}).
 *
 * <p>During import the raw portal string from the scrape response (e.g., "Team everest",
 * "mybharat.gov.in") is normalised and matched to these values via {@link #fromString(String)}.
 * Unrecognised portals return {@code null} — callers may decide to skip or store as-is.
 *
 * <p>Portals with a non-null {@code eventFilter} support {@code FETCH_EVENTS} scraping. Set {@code
 * eventFilter} to enable a portal in the ingestion loop.
 */
@Getter
@RequiredArgsConstructor
public enum EventPortalModel {
  TEAMEVEREST(
      "This should be an event where anybody can participate and do something related to cleaning,"
          + " plantation",
      ""),
  IVOLUNTEERIN(
      "Any in-person volunteering activity: cleanliness drive, cleanup, tree plantation,  mural"
          + " painting, food distribution, community engagement, elderly care, animal welfare,"
          + " sanitation. Exclude: remote-only roles like content writing, video editing, social"
          + " media management, UI/UX design, app development, proposal writing, digital marketing,"
          + " and pure internships with no direct community interaction.",
      "environment, education, civic engagement, community service, child welfare, health, hunger,"
          + " disability, animal welfare, elderly care, sanitation"),
  MYBHARATGOVIN("civic volunteering, cleanliness drive, tree plantation", "civic engagement"),
  TWITTER(null, null),
  INSTAGRAM(null, null),
  /**
   * Events created directly by an admin via the ops portal (not scraped from any external source).
   */
  ADMIN(null, null);

  /** The event_filter sent in the FETCH_EVENTS POST body. {@code null} = portal not yet enabled. */
  private final String eventFilter;

  private final String categoryFilter;

  public boolean supportsFetchEvents() {
    return eventFilter != null;
  }

  /**
   * Maps a raw portal string from the scrape response to the corresponding enum value.
   *
   * <p>Normalisation: lower-cased, all dots/spaces/hyphens/underscores removed. Examples:
   *
   * <ul>
   *   <li>"Team everest" → {@link #TEAMEVEREST}
   *   <li>"ivolunteer" / "ivolunteer.in" → {@link #IVOLUNTEERIN}
   *   <li>"mybharat.gov.in" → {@link #MYBHARATGOVIN}
   *   <li>"Twitter" → {@link #TWITTER}
   * </ul>
   *
   * @return the matching enum value, or {@code null} if unrecognised
   */
  public static EventPortalModel fromString(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String normalized = raw.toLowerCase().replaceAll("[\\s.\\-_]+", "");
    return switch (normalized) {
      case "teameverest" -> TEAMEVEREST;
      case "ivolunteer", "ivolunteerin" -> IVOLUNTEERIN;
      case "mybharatgovin" -> MYBHARATGOVIN;
      case "twitter" -> TWITTER;
      case "instagram" -> INSTAGRAM;
      default -> null;
    };
  }
}
