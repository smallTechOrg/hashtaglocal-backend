package org.smalltech.hashtaglocal_backend.model.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

/** Admin approval of the pending quizzes they reviewed; only those ids are approved. */
@Data
public class ApproveQuizzesRequest {

  @NotEmpty private List<Long> quizIds;
}
