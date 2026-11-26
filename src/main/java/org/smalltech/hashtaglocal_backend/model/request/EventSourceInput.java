package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /admin/event-sources} and {@code PUT /admin/event-sources/{id}} (snake_case
 * on the wire). {@code action} defaults to {@code fetch_events}, {@code include_online} to false,
 * {@code freshness_hours} to {@code scraper0.freshness-hours} and {@code enabled} to true.
 */
public record EventSourceInput(
    @NotBlank(message = "name is required") @Size(max = 200) String name,
    @NotBlank(message = "site_id is required") @Size(max = 255) String siteId,
    @Size(max = 100) String action,
    String eventFilter,
    String categoryFilter,
    Boolean includeOnline,
    @Min(value = 0, message = "freshness_hours must be 0 or more")
        @Max(value = 8760, message = "freshness_hours must be at most 8760")
        Integer freshnessHours,
    Boolean enabled) {}
