package org.smalltech.hashtaglocal_backend.model;

/**
 * Enum representing the category/activity type of a civic or community event.
 *
 * <p>Stored as a VARCHAR string in the `events.event_type` column (via {@code EnumType.STRING}), so
 * adding new values here does not require a DB migration.
 *
 * <p>During CSV import, raw strings from the spreadsheet (e.g., "Cleanliness drive", "Trek and
 * plog") are normalised and matched to these values. Unrecognised types fall back to {@link
 * #OTHER}.
 */
public enum EventTypeModel {
  CLEANLINESS_DRIVE,
  BEACH_CLEANUP,
  ROAD_CLEANUP,
  FOREST_CLEANUP,
  TREEPLANTATION,
  TREKANDPLOG,
  VOLUNTEERING,
  WORKSHOP,
  OTHER
}
