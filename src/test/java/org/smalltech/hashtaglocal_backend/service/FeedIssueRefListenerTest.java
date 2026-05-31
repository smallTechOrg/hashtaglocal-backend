package org.smalltech.hashtaglocal_backend.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.event.IssueStatusChangedEvent;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.IssueStatusModel;
import org.smalltech.hashtaglocal_backend.repository.FeedPostRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeedIssueRefListenerTest {

  @Mock IssueRepository issueRepository;
  @Mock FeedPostRepository feedPostRepository;
  @Mock FeedService feedService;

  @InjectMocks FeedIssueRefListener listener;

  private Locality locality;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(listener, "autoIssueRefEnabled", true);
    locality = Locality.builder().id(10L).hashtag("#tnagar").name("T Nagar").build();
  }

  private IssueEntity issue(long id) {
    Location loc = Location.builder().locality(locality).name("x").build();
    return IssueEntity.builder().id(id).location(loc).build();
  }

  @Test
  void approvalToOpenPostsOnceWhenNoExistingPost() {
    when(issueRepository.findById(1L)).thenReturn(Optional.of(issue(1L)));
    when(feedPostRepository.countIssueRefPosts(1L)).thenReturn(0L);

    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.OPEN));

    verify(feedService).createSystemPost(eq(locality), eq(FeedPostKind.ISSUE_REF), any());
  }

  @Test
  void approvalToOpenDoesNotDuplicateWhenPostAlreadyExists() {
    when(issueRepository.findById(1L)).thenReturn(Optional.of(issue(1L)));
    when(feedPostRepository.countIssueRefPosts(1L)).thenReturn(1L);

    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.OPEN));

    verify(feedService, never()).createSystemPost(any(), any(), any());
  }

  @Test
  void resolvedAlwaysPostsAgain() {
    when(issueRepository.findById(1L)).thenReturn(Optional.of(issue(1L)));

    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.RESOLVED));

    // Resolution announcement posts regardless of an existing OPEN post.
    verify(feedService).createSystemPost(eq(locality), eq(FeedPostKind.ISSUE_REF), any());
    verify(feedPostRepository, never()).countIssueRefPosts(anyLong());
  }

  @Test
  void rejectedHidesPostsAndPostsNothing() {
    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.REJECTED));

    verify(feedPostRepository).hideIssueRefPosts(1L);
    verify(feedService, never()).createSystemPost(any(), any(), any());
    verifyNoInteractions(issueRepository);
  }

  @Test
  void onHoldHidesPosts() {
    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.ONHOLD));

    verify(feedPostRepository).hideIssueRefPosts(1L);
    verify(feedService, never()).createSystemPost(any(), any(), any());
  }

  @Test
  void disabledFlagDoesNothing() {
    ReflectionTestUtils.setField(listener, "autoIssueRefEnabled", false);

    listener.onIssueStatusChanged(new IssueStatusChangedEvent(1L, IssueStatusModel.OPEN));

    verifyNoInteractions(issueRepository, feedPostRepository, feedService);
  }
}
