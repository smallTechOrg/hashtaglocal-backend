package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.mapper.FeedPostMapper;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FeedQueryServiceTest {

  @Mock FeedPostRepository feedPostRepository;
  @Mock LocalityRepository localityRepository;
  @Mock FeedPostMapper feedPostMapper;

  @InjectMocks FeedQueryService feedQueryService;

  private Locality india;

  @BeforeEach
  void setUp() {
    india = Locality.builder().id(1L).hashtag("#india").name("India").build();
    when(localityRepository.findByHashtagFlexible("india")).thenReturn(Optional.of(india));
  }

  @Test
  void aggregateTrueUsesAggregatedQueries() {
    when(feedPostRepository.findAggregatedTimelineFirstPage(
            eq(1L), eq(FeedPostStatus.PUBLISHED), any(), any(), any(Pageable.class)))
        .thenReturn(List.of());
    when(feedPostRepository.findAggregatedPinned(eq(1L), eq(FeedPostStatus.PUBLISHED), any()))
        .thenReturn(List.of());

    feedQueryService.getTimeline("india", null, 30, true, null);

    verify(feedPostRepository)
        .findAggregatedTimelineFirstPage(eq(1L), eq(FeedPostStatus.PUBLISHED), any(), any(), any());
    verify(feedPostRepository).findAggregatedPinned(eq(1L), eq(FeedPostStatus.PUBLISHED), any());
    verify(feedPostRepository, never())
        .findTimelineFirstPage(anyLong(), any(), any(), any(), any());
  }

  @Test
  void aggregateFalseUsesSingleLocalityQueries() {
    when(feedPostRepository.findTimelineFirstPage(
            eq(1L), eq(FeedPostStatus.PUBLISHED), any(), any(), any(Pageable.class)))
        .thenReturn(List.of());
    when(feedPostRepository.findPinned(eq(1L), eq(FeedPostStatus.PUBLISHED), any()))
        .thenReturn(List.of());

    feedQueryService.getTimeline("india", null, 30, false, null);

    verify(feedPostRepository)
        .findTimelineFirstPage(eq(1L), eq(FeedPostStatus.PUBLISHED), any(), any(), any());
    verify(feedPostRepository, never())
        .findAggregatedTimelineFirstPage(anyLong(), any(), any(), any(), any());
  }

  @Test
  void viewerUserIdIsPassedToTimelineQuery() {
    when(feedPostRepository.findTimelineFirstPage(
            eq(1L), eq(FeedPostStatus.PUBLISHED), any(), eq(42L), any(Pageable.class)))
        .thenReturn(List.of());
    when(feedPostRepository.findPinned(eq(1L), eq(FeedPostStatus.PUBLISHED), any()))
        .thenReturn(List.of());

    feedQueryService.getTimeline("india", null, 30, false, 42L);

    // The viewer's id flows into the query so their own under-review posts are included.
    verify(feedPostRepository)
        .findTimelineFirstPage(eq(1L), eq(FeedPostStatus.PUBLISHED), any(), eq(42L), any());
  }
}
