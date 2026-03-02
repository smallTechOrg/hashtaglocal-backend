package org.smalltech.hashtaglocal_backend.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.EventEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Handles importing events from a UTF-8 CSV file exported from the events Excel sheet.
 *
 * <p>Expected CSV column order (first row is a header and is skipped):
 *
 * <pre>
 *   event_name | city | organisation | platform | event_type | start_time | end_time | address | link
 * </pre>
 *
 * <p>Key behaviours:
 *
 * <ul>
 *   <li>Rows with a blank {@code event_name} or {@code organisation} are silently skipped.
 *   <li>Any row that throws an unexpected exception is logged and skipped — one bad row does not
 *       abort the whole import.
 *   <li>The {@code city} column in the CSV is ignored — city is derived from the geocoded locality
 *       after {@code POST /admin/events/geocode} is run.
 *   <li>Start/end dates are expected in "Month Day" format (e.g., "February 21"). They are parsed
 *       as the current year at midnight (00:00:00). Unparseable values are stored as null.
 *   <li>Event type strings are normalised (lowercased, spaces/hyphens removed) before being mapped
 *       to {@link EventTypeModel}. Unrecognised strings map to {@link EventTypeModel#OTHER}.
 *   <li>Geocoding (address → lat/lng) is NOT performed here. The {@code location_id} field is left
 *       null; only the raw {@code address} string is saved.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventImportService {

  // Parses date strings like "February 21" or "March 1" from the spreadsheet.
  private static final DateTimeFormatter MONTH_DAY_FMT =
      DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);

  // Parses full datetime strings — handles both padded (05:00:00) and unpadded (5:00:00) values,
  // both slash and dash separators, and an optional comma after the year
  // e.g. "21/02/2026 05:00:00", "21-02-2026 5:00:00", or "21/02/2026, 05:00".
  private static final List<DateTimeFormatter> FULL_DATETIME_FMTS =
      List.of(
          DateTimeFormatter.ofPattern("d/M/yyyy H:mm:ss", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d-M-yyyy H:mm:ss", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d/M/yyyy H:mm", Locale.ENGLISH),
          DateTimeFormatter.ofPattern("d/M/yyyy, H:mm", Locale.ENGLISH));

  private final EventService eventService;

  /**
   * Reads the uploaded CSV file, parses each data row into an {@link EventEntity}, and bulk-saves
   * all valid rows in a single transaction.
   *
   * @param file the CSV file uploaded via multipart form
   * @return the number of events successfully imported
   * @throws IOException if the file cannot be read or the CSV structure is invalid
   */
  public int importFromCsv(MultipartFile file) throws IOException {
    List<EventEntity> events = new ArrayList<>();

    try (CSVReader reader =
        new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

      String[] header = reader.readNext(); // first row is always the header — discard it
      if (header == null) {
        return 0;
      }

      String[] row;
      int lineNumber = 1;
      while ((row = reader.readNext()) != null) {
        lineNumber++;
        try {
          EventEntity event = parseRow(row);
          if (event != null) {
            events.add(event);
          }
        } catch (Exception e) {
          // Log and continue — one bad row should not abort the entire import
          log.warn("Skipping row {} due to parse error: {}", lineNumber, e.getMessage());
        }
      }
    } catch (CsvValidationException e) {
      // Wrap as IOException so callers don't need to know about the OpenCSV type
      throw new IOException("CSV parsing failed: " + e.getMessage(), e);
    }

    eventService.saveAll(events);
    log.info("Imported {} events", events.size());
    return events.size();
  }

  /**
   * Maps one CSV row (a String array) to an {@link EventEntity}.
   *
   * @return a built entity, or null if the row should be skipped (e.g., blank event name)
   */
  private EventEntity parseRow(String[] row) {
    if (row.length < 1 || row[0].isBlank()) {
      return null;
    }

    // Extract columns by fixed index — matches the expected header order
    // Column index 1 (city) is intentionally skipped — city is derived from geocoded locality
    String eventName = get(row, 0);
    String organisation = get(row, 2);
    String platform = get(row, 3);
    String eventTypeRaw = get(row, 4);
    String startRaw = get(row, 5);
    String endRaw = get(row, 6);
    String address = get(row, 7);
    String link = get(row, 8);

    if (eventName.isBlank() || organisation.isBlank()) {
      return null;
    }

    return EventEntity.builder()
        .eventName(eventName)
        .organisation(organisation)
        .platform(platform.isBlank() ? null : platform)
        .eventType(parseEventType(eventTypeRaw))
        .startTime(parseDateTime(startRaw))
        .endTime(parseDateTime(endRaw))
        .address(address.isBlank() ? null : address)
        .link(link.isBlank() ? null : link)
        .build();
  }

  /** Safely reads a column by index, returning an empty string if the column is missing. */
  private String get(String[] row, int index) {
    return (index < row.length && row[index] != null) ? row[index].trim() : "";
  }

  /**
   * Parses a "Month Day" string (e.g., "February 21") into a {@link LocalDateTime} at midnight of
   * the current year. Returns null if the string is blank or cannot be parsed.
   */
  private LocalDateTime parseDateTime(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    String trimmed = raw.trim();
    // Try full datetime formats first e.g. "21/02/2026 05:00:00"
    for (DateTimeFormatter fmt : FULL_DATETIME_FMTS) {
      try {
        return LocalDateTime.parse(trimmed, fmt);
      } catch (DateTimeParseException ignored) {
      }
    }
    // Fall back to "February 21" (stored at midnight of the current year)
    try {
      MonthDay md = MonthDay.parse(trimmed, MONTH_DAY_FMT);
      return md.atYear(Year.now().getValue()).atStartOfDay();
    } catch (DateTimeParseException e) {
      log.warn("Could not parse date '{}', skipping", raw);
      return null;
    }
  }

  /**
   * Converts a free-text event type string from the spreadsheet into an {@link EventTypeModel} enum
   * value. The input is normalised (lowercased, whitespace/hyphens removed) before matching.
   * Anything unrecognised maps to {@link EventTypeModel#OTHER}.
   */
  private EventTypeModel parseEventType(String raw) {
    if (raw == null || raw.isBlank()) {
      return EventTypeModel.OTHER;
    }
    String normalized = raw.toLowerCase(Locale.ENGLISH).replaceAll("[\\s\\-_]+", "");
    return switch (normalized) {
      case "cleanlinessdrive" -> EventTypeModel.CLEANLINESS_DRIVE;
      case "beachcleanup" -> EventTypeModel.BEACH_CLEANUP;
      case "roadcleanup" -> EventTypeModel.ROAD_CLEANUP;
      case "forestcleanup" -> EventTypeModel.FOREST_CLEANUP;
      case "treeplantation" -> EventTypeModel.TREEPLANTATION;
      case "trekandplog", "trekplog" -> EventTypeModel.TREKANDPLOG;
      case "volunteering" -> EventTypeModel.VOLUNTEERING;
      case "workshop" -> EventTypeModel.WORKSHOP;
      default -> EventTypeModel.OTHER;
    };
  }
}
