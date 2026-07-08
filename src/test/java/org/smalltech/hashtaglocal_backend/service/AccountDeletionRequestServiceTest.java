package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.AccountDeletionRequestEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.infra.notification.SlackNotifier;
import org.smalltech.hashtaglocal_backend.model.AccountDeletionRequestStatus;
import org.smalltech.hashtaglocal_backend.repository.AccountDeletionRequestRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Created to unit-test AccountDeletionRequestService in isolation (no Spring context). Covers: new
 * request creation with session revocation and admin email dispatch; idempotent behaviour when a
 * PENDING request already exists (no duplicate save or email); and SMTP failure resilience (the
 * deletion request is still saved if email sending fails).
 */
@ExtendWith(MockitoExtension.class)
class AccountDeletionRequestServiceTest {

  @Mock private AccountDeletionRequestRepository accountDeletionRequestRepository;

  @Mock private UserRepository userRepository;

  @Mock private UserAuthProviderRepository userAuthProviderRepository;

  @Mock private UserAuthSessionRepository userAuthSessionRepository;

  @Mock private JavaMailSender mailSender;

  @Mock private SlackNotifier slackNotifier;

  private AccountDeletionRequestService service;

  @BeforeEach
  void setUp() {
    service =
        new AccountDeletionRequestService(
            accountDeletionRequestRepository,
            userRepository,
            userAuthProviderRepository,
            userAuthSessionRepository,
            mailSender,
            slackNotifier);
    ReflectionTestUtils.setField(service, "adminEmail", "admin@example.com");
    ReflectionTestUtils.setField(service, "fromEmail", "noreply@example.com");
  }

  @Test
  void requestDeletionCreatesRequestRevokesSessionsAndSendsEmail() {
    UserEntity user = UserEntity.builder().id(1L).username("testuser").build();
    UserAuthProviderEntity provider =
        UserAuthProviderEntity.builder().user(user).email("user@example.com").build();

    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(accountDeletionRequestRepository.findByUserIdAndStatus(
            1L, AccountDeletionRequestStatus.PENDING))
        .thenReturn(Optional.empty());
    when(accountDeletionRequestRepository.save(any(AccountDeletionRequestEntity.class)))
        .thenAnswer(
            invocation -> {
              AccountDeletionRequestEntity request = invocation.getArgument(0);
              request.setId(10L);
              return request;
            });
    when(userAuthProviderRepository.findFirstByUserIdOrderByIdAsc(1L))
        .thenReturn(Optional.of(provider));

    var response = service.requestDeletion(1L);

    assertEquals("PENDING", response.getStatus());
    assertNotNull(response.getRequestedAt());
    assertEquals(response.getRequestedAt().plusHours(24), response.getScheduledDeletionAt());
    verify(userAuthSessionRepository).deactivateAllByUserId(1L);

    ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(mailSender).send(mailCaptor.capture());
    assertEquals("#local account deletion request", mailCaptor.getValue().getSubject());
  }

  @Test
  void requestDeletionReturnsExistingPendingRequestAndDoesNotSendDuplicateEmail() {
    UserEntity user = UserEntity.builder().id(1L).username("testuser").build();
    LocalDateTime requestedAt = LocalDateTime.now().minusHours(1);
    AccountDeletionRequestEntity existingRequest =
        AccountDeletionRequestEntity.builder()
            .id(20L)
            .user(user)
            .status(AccountDeletionRequestStatus.PENDING)
            .requestedAt(requestedAt)
            .scheduledDeletionAt(requestedAt.plusHours(24))
            .build();

    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(accountDeletionRequestRepository.findByUserIdAndStatus(
            1L, AccountDeletionRequestStatus.PENDING))
        .thenReturn(Optional.of(existingRequest));

    var response = service.requestDeletion(1L);

    assertEquals(requestedAt, response.getRequestedAt());
    verify(accountDeletionRequestRepository, never()).save(any());
    verify(mailSender, never()).send(any(SimpleMailMessage.class));
    verify(userAuthSessionRepository).deactivateAllByUserId(1L);
  }

  @Test
  void requestDeletionStillSucceedsWhenEmailFails() {
    UserEntity user = UserEntity.builder().id(1L).username("testuser").build();

    when(userRepository.findById(1L)).thenReturn(Optional.of(user));
    when(accountDeletionRequestRepository.findByUserIdAndStatus(
            1L, AccountDeletionRequestStatus.PENDING))
        .thenReturn(Optional.empty());
    when(accountDeletionRequestRepository.save(any(AccountDeletionRequestEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(new MailException("smtp down") {}).when(mailSender).send(any(SimpleMailMessage.class));

    var response = service.requestDeletion(1L);

    assertEquals("PENDING", response.getStatus());
    verify(userAuthSessionRepository).deactivateAllByUserId(1L);
  }
}
