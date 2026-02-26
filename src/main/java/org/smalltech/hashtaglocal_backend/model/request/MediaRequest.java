package org.smalltech.hashtaglocal_backend.model.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class MediaRequest {

  @NotBlank(message = "type is required")
  private String type; // PHOTO / VIDEO

  @NotBlank(message = "url is required")
  private String url;

  @Valid private LocationRequest location; // optional — geo-fence skipped when absent

  private String description;
}
