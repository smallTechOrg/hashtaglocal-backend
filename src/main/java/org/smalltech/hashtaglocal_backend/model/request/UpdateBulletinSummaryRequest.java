package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Admin edit of a bulletin's AI weather summary. */
@Data
public class UpdateBulletinSummaryRequest {

  @NotBlank private String summary;
}
