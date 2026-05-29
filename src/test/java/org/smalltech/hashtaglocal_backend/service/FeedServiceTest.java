package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.FeedPostEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.event.FeedPostCreatedEvent;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.FeedPostKind;
import org.smalltech.hashtaglocal_backend.model.FeedPostStatus;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.model.request.CreateFeedPostRequest;
import org.smalltech.hashtaglocal_backend.repository.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

  @Mock FeedPostRepository feedPostRepository;
  @Mock LocalityRepository localityRepository;
  @Mock UserRepository userRepository;
  @Mock MediaRepository mediaRepository;
  @Mock IssueRepository issueRepository;
  @Mock EventRepository eventRepository;
  @Mock ApplicationEventPublisher eventPublisher;

  @InjectMocks FeedService feedService;

  private UserEntity user;
  private UserEntity admin;
  private Locality locality;

  @BeforeEach
  void setUp() {
    user = UserEntity.builder().id(1L).username("alice").role(UserRole.USER).build();
    admin = UserEntity.builder().id(2L).username("root").role(UserRole.ADMIN).build();
    locality = Locality.builder().id(10L).hashtag("tnagar").name("T Nagar").build();
    // save returns its argument with an id stamped
    lenient()
        .when(feedPostRepository.save(any(FeedPostEntity.class)))
        .thenAnswer(
            inv -> {
              FeedPostEntity p = inv.getArgument(0);
              p.setId(99L);
              return p;
            });
  }

  @Test
  void userPostWithoutLocationIsRejected422() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.TEXT);
    req.setText("hello");

    DownstreamServiceException ex =
        assertThrows(DownstreamServiceException.class, () -> feedService.create(1L, req));
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
    assertEquals("GEO", ex.getType());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void userPostOutsideAnyLocalityIsRejected422() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(localityRepository.findContainingLocality(10.0, 20.0)).thenReturn(Optional.empty());
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.TEXT);
    req.setText("hello");
    req.setLat(10.0);
    req.setLng(20.0);

    DownstreamServiceException ex =
        assertThrows(DownstreamServiceException.class, () -> feedService.create(1L, req));
    assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
  }

  @Test
  void userPostWithLocationIsPendingAiAndPublishesModerationEvent() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(localityRepository.findContainingLocality(10.0, 20.0)).thenReturn(Optional.of(locality));
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.TEXT);
    req.setText("hello neighbours");
    req.setLat(10.0);
    req.setLng(20.0);

    FeedPostEntity post = feedService.create(1L, req);

    assertEquals(FeedPostStatus.PENDING_AI, post.getStatus());
    assertEquals(locality, post.getLocality());
    assertNull(post.getPublishedAt());

    ArgumentCaptor<FeedPostCreatedEvent> captor =
        ArgumentCaptor.forClass(FeedPostCreatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    FeedPostCreatedEvent event = captor.getValue();
    assertEquals(99L, event.feedPostId());
    assertTrue(event.needsModeration());
    assertFalse(event.needsScrape());
  }

  @Test
  void adminPostPublishesDirectlyByHashtagWithoutLocationOrModeration() {
    when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
    when(localityRepository.findByHashtag("tnagar")).thenReturn(Optional.of(locality));
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.TEXT);
    req.setText("official notice");
    req.setHashtag("tnagar");

    FeedPostEntity post = feedService.create(2L, req);

    assertEquals(FeedPostStatus.PUBLISHED, post.getStatus());
    assertNotNull(post.getPublishedAt());
    // Admin text post needs neither scrape nor moderation → no side-effect event.
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void adminPostWithoutHashtagIsRejected400() {
    when(userRepository.findById(2L)).thenReturn(Optional.of(admin));
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.TEXT);
    req.setText("oops");

    DownstreamServiceException ex =
        assertThrows(DownstreamServiceException.class, () -> feedService.create(2L, req));
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void linkPostEventRequestsBothScrapeAndModeration() {
    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(localityRepository.findContainingLocality(10.0, 20.0)).thenReturn(Optional.of(locality));
    CreateFeedPostRequest req = new CreateFeedPostRequest();
    req.setKind(FeedPostKind.LINK);
    req.setLinkUrl("https://example.com/article");
    req.setLat(10.0);
    req.setLng(20.0);

    FeedPostEntity post = feedService.create(1L, req);

    assertEquals("https://example.com/article", post.getContent().getUrl());
    assertNotNull(post.getContent().getCanonicalUrl());

    ArgumentCaptor<FeedPostCreatedEvent> captor =
        ArgumentCaptor.forClass(FeedPostCreatedEvent.class);
    verify(eventPublisher).publishEvent(captor.capture());
    FeedPostCreatedEvent event = captor.getValue();
    assertTrue(event.needsScrape());
    assertTrue(event.needsModeration());
  }
}
