package org.smalltech.hashtaglocal_backend.model;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewerContext {
  private boolean upvote;

  /**
   * {@code true} when the authenticated viewer is the owner (reporter) of the issue. Clients should
   * use this flag to show owner-only controls and ONHOLD / pending-verification content.
   */
  private boolean isOwner;
}
