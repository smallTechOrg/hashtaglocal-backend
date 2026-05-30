package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeedBackfillServiceTest {

  @Mock IssueRepository issueRepository;
  @Mock FeedService feedService;
  @Mock ObjectProvider<FeedBackfillService> selfProvider;

  FeedBackfillService service;

  private Locality locality;

  @BeforeEach
  void setUp() {
    service = new FeedBackfillService(issueRepository, feedService, selfProvider);
    // Self-provider returns the real instance so processBatch runs (proxy is irrelevant in a unit
    // test).
    lenient().when(selfProvider.getObject()).thenReturn(service);
    locality = Locality.builder().id(10L).hashtag("#tnagar").name("T Nagar").build();
  }

  private IssueEntity issue(long id, LocalDateTime createdAt) {
    Location loc = Location.builder().locality(locality).name("x").build();
    return IssueEntity.builder().id(id).location(loc).createdAt(createdAt).build();
  }

  @Test
  void backfillsEachIssueOnceAndBackdatesToIssueTime() {
    LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 9, 0);
    // First page returns 2 issues, second page empty → loop stops.
    when(issueRepository.findIssuesWithoutFeedRef(any(Pageable.class)))
        .thenReturn(List.of(issue(1L, t1), issue(2L, t1.plusDays(1))))
        .thenReturn(List.of());

    FeedBackfillService.BackfillResult result = service.backfillIssueRefs(0);

    assertEquals(2, result.created());
    assertEquals(0, result.skipped());

    ArgumentCaptor<LocalDateTime> when = ArgumentCaptor.forClass(LocalDateTime.class);
    verify(feedService, times(2))
        .createBackfilledSystemPost(
            eq(locality), eq(FeedPostKind.ISSUE_REF), any(), when.capture());
    // Backdated to the issues' own timestamps, not now.
    assertEquals(List.of(t1, t1.plusDays(1)), when.getAllValues());
  }

  @Test
  void emptyBacklogCreatesNothing() {
    when(issueRepository.findIssuesWithoutFeedRef(any(Pageable.class))).thenReturn(List.of());
    FeedBackfillService.BackfillResult result = service.backfillIssueRefs(0);
    assertEquals(0, result.created());
    verifyNoInteractions(feedService);
  }

  @Test
  void issueWithoutLocalityIsSkippedNotPosted() {
    IssueEntity noLocality = IssueEntity.builder().id(3L).location(null).build();
    when(issueRepository.findIssuesWithoutFeedRef(any(Pageable.class)))
        .thenReturn(List.of(noLocality));

    FeedBackfillService.BackfillResult result = service.backfillIssueRefs(0);

    assertEquals(0, result.created());
    assertEquals(1, result.skipped());
    verifyNoInteractions(feedService);
  }
}
