package org.smalltech.hashtaglocal_backend.model.response;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.smalltech.hashtaglocal_backend.model.IssueStory;

@Data
@Builder
public class IssueStoriesResponseData {

  private List<IssueStory> stories;
}
