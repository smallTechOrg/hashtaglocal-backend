package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;

/**
 * Request body for {@code POST /api/v1/feed} (user) and {@code POST /admin/feed} (admin). Locality
 * resolution differs by role (see FEED_DESIGN.md §5.1):
 *
 * <ul>
 *   <li><b>USER</b> — must send {@code lat}/{@code lng}; locality is resolved server-side.
 *   <li><b>ADMIN</b> — sends {@code hashtag} directly; no location required, any hashtag allowed.
 * </ul>
 *
 * Validation of the role-specific requirements happens in the service, not here, because it depends
 * on the authenticated principal's role.
 */
@Data
public class CreateFeedPostRequest {

  @NotNull(message = "kind is required")
  private FeedPostKind kind;

  // USER path: coordinates → locality (validated in service)
  @DecimalMin(value = "-90.0", message = "lat must be between -90 and 90")
  @DecimalMax(value = "90.0", message = "lat must be between -90 and 90")
  private Double lat;

  @DecimalMin(value = "-180.0", message = "lng must be between -180 and 180")
  @DecimalMax(value = "180.0", message = "lng must be between -180 and 180")
  private Double lng;

  // ADMIN path: target channel by hashtag
  private String hashtag;

  @Size(max = 4000, message = "text must be at most 4000 characters")
  private String text;

  @JsonProperty("link_url")
  @Size(max = 5000, message = "link_url must be at most 5000 characters")
  private String linkUrl;

  /** A single media id (reuses the existing media upload flow). */
  @JsonProperty("media_id")
  private Long mediaId;

  @JsonProperty("issue_id")
  private Long issueId;

  @JsonProperty("event_id")
  private Long eventId;
}
