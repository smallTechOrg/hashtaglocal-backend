package org.smalltech.hashtaglocal_backend.model;

/**
 * Enum representing the external portal/platform that an event was sourced from.
 *
 * <p>Stored as a VARCHAR string in the {@code events.portal} column (via {@code EnumType.STRING}),
 * so the values stay even when nothing produces them any more: existing rows use them.
 *
 * <p>Scraped events come from the event sources in the {@code event_sources} table (scraper0
 * sites). The three original portals keep their own value ({@link #fromSiteId(String)}); every
 * other scraper0 site is {@link #WEB}, with the site id in {@code events.source_site_id}.
 */
public enum EventPortalModel {
  TEAMEVEREST,
  IVOLUNTEERIN,
  MYBHARATGOVIN,
  TWITTER,
  INSTAGRAM,
  /**
   * Events created directly by an admin via the ops portal (not scraped from any external source).
   */
  ADMIN,
  /** Any other scraper0 site (an event source added from the Ops Dashboard). */
  WEB;

  /**
   * The portal for a scraper0 {@code site_id}: the original three keep their value, any other site
   * is {@link #WEB}.
   */
  public static EventPortalModel fromSiteId(String siteId) {
    if (siteId == null) {
      return WEB;
    }
    return switch (siteId) {
      case "teameverest-ngo" -> TEAMEVEREST;
      case "mybharat-gov-in" -> MYBHARATGOVIN;
      case "ivolunteer-in" -> IVOLUNTEERIN;
      default -> WEB;
    };
  }
}
