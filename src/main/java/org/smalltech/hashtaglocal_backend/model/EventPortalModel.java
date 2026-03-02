package org.smalltech.hashtaglocal_backend.model;

/**
 * Enum representing the external portal/platform that an event was sourced from.
 *
 * <p>Stored as a VARCHAR string in the {@code events.portal} column (via {@code EnumType.STRING}).
 *
 * <p>During import the raw portal string from the scrape response (e.g., "Team everest",
 * "mybharat.gov.in") is normalised and matched to these values via {@link #fromString(String)}.
 * Unrecognised portals return {@code null} — callers may decide to skip or store as-is.
 */
public enum EventPortalModel {
  TEAMEVEREST,
  IVOLUNTEER,
  MYBHARATGOVIN,
  TWITTER;

  /**
   * Maps a raw portal string from the scrape response to the corresponding enum value.
   *
   * <p>Normalisation: lower-cased, all dots/spaces/hyphens/underscores removed. Examples:
   *
   * <ul>
   *   <li>"Team everest" → {@link #TEAMEVEREST}
   *   <li>"ivolunteer" → {@link #IVOLUNTEER}
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
      case "ivolunteer" -> IVOLUNTEER;
      case "mybharatgovin" -> MYBHARATGOVIN;
      case "twitter" -> TWITTER;
      default -> null;
    };
  }
}
