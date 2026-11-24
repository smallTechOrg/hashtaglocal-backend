package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.entity.Locality;

class EventServiceHashtagsTest {

  private static Locality locality(long id, String hashtag, Locality parent) {
    Locality l = new Locality();
    l.setId(id);
    l.setHashtag(hashtag);
    l.setParent(parent);
    return l;
  }

  @Test
  void ownHashtagFirstThenTheIndiaRoot() {
    Locality india = locality(1L, "#india", null);
    Locality bijnor = locality(2L, "#bijnor", india);
    assertEquals(List.of("#bijnor", "#india"), EventService.hashtagsWithAncestors(bijnor));
  }

  @Test
  void theRootItselfHasOnlyItsOwnHashtag() {
    Locality india = locality(1L, "#india", null);
    assertEquals(List.of("#india"), EventService.hashtagsWithAncestors(india));
  }

  @Test
  void aCycleInParentIdStops() {
    Locality a = locality(1L, "#a", null);
    Locality b = locality(2L, "#b", a);
    a.setParent(b);
    assertEquals(List.of("#a", "#b"), EventService.hashtagsWithAncestors(a));
  }
}
