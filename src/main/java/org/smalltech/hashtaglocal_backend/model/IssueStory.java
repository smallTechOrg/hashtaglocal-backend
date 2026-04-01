package org.smalltech.hashtaglocal_backend.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IssueStory {

  private Issue issue;
  private List<TimelineEvent> timeline;
  private int resolutionDays;
}
