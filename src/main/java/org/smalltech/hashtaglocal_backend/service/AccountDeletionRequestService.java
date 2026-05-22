package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import org.smalltech.hashtaglocal_backend.entity.AccountDeletionRequestEntity;
import org.smalltech.hashtaglocal_backend.entity.UserAuthProviderEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.AccountDeletionRequestStatus;
import org.smalltech.hashtaglocal_backend.model.response.AccountDeletionRequestResponseData;
import org.smalltech.hashtaglocal_backend.repository.AccountDeletionRequestRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthProviderRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Created to orchestrate the full account deletion request flow: 1. Creates (or returns an
 * existing) PENDING deletion request for the authenticated user. 2. Immediately revokes all active
 * login sessions so the user is logged out on every device. 3. Sends a one-time admin notification
 * email so the account can be manually removed within 24 hours
 */
@Service
public class AccountDeletionRequestService {

  private static final String EMAIL_SUBJECT = "#local account deletion request";

  private final AccountDeletionRequestRepository accountDeletionRequestRepository;
  private final UserRepository userRepository;
  private final UserAuthProviderRepository userAuthProviderRepository;
  private final UserAuthSessionRepository userAuthSessionRepository;
  private final JavaMailSender mailSender;

  @Value("${account.deletion.admin-email:}")
  private String adminEmail;

  @Value("${account.deletion.from-email:}")
  private String fromEmail;

  public AccountDeletionRequestService(
      AccountDeletionRequestRepository accountDeletionRequestRepository,
      UserRepository userRepository,
      UserAuthProviderRepository userAuthProviderRepository,
      UserAuthSessionRepository userAuthSessionRepository,
      JavaMailSender mailSender) {
    this.accountDeletionRequestRepository = accountDeletionRequestRepository;
    this.userRepository = userRepository;
    this.userAuthProviderRepository = userAuthProviderRepository;
    this.userAuthSessionRepository = userAuthSessionRepository;
    this.mailSender = mailSender;
  }

  @Transactional
  public AccountDeletionRequestResponseData requestDeletion(Long userId) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

    var existingRequest =
        accountDeletionRequestRepository.findByUserIdAndStatus(
            userId, AccountDeletionRequestStatus.PENDING);

    AccountDeletionRequestEntity deletionRequest;
    boolean newlyCreated = existingRequest.isEmpty();

    // Keep the endpoint idempotent so repeated taps/calls return the same pending request.
    if (existingRequest.isPresent()) {
      deletionRequest = existingRequest.get();
    } else {
      LocalDateTime requestedAt = LocalDateTime.now();
      deletionRequest =
          accountDeletionRequestRepository.save(
              AccountDeletionRequestEntity.builder()
                  .user(user)
                  .status(AccountDeletionRequestStatus.PENDING)
                  .requestedAt(requestedAt)
                  .scheduledDeletionAt(requestedAt.plusHours(24))
                  .build());
    }

    // Force logout on every deletion request by invalidating all active sessions for the user.
    int deactivatedSessions = userAuthSessionRepository.deactivateAllByUserId(userId);
    System.out.println(
        "Account deletion requested for user "
            + userId
            + "; deactivated sessions="
            + deactivatedSessions);

    // Send only once per new request; retries should not spam the admin inbox.
    if (newlyCreated) {
      sendAdminEmail(deletionRequest);
    }

    return mapToResponse(deletionRequest);
  }

  public boolean hasPendingDeletionRequest(Long userId) {
    return accountDeletionRequestRepository
        .findByUserIdAndStatus(userId, AccountDeletionRequestStatus.PENDING)
        .isPresent();
  }

  private void sendAdminEmail(AccountDeletionRequestEntity deletionRequest) {
    if (adminEmail == null || adminEmail.isBlank()) {
      System.out.println(
          "ACCOUNT_DELETION_ADMIN_EMAIL is not configured; skipping account deletion email.");
      return;
    }

    UserEntity user = deletionRequest.getUser();
    String providerEmail =
        userAuthProviderRepository
            .findFirstByUserIdOrderByIdAsc(user.getId())
            .map(UserAuthProviderEntity::getEmail)
            .orElse("N/A");

    try {
      // Admin notification is best-effort: the deletion request remains saved even if SMTP fails.
      SimpleMailMessage message = new SimpleMailMessage();
      if (fromEmail != null && !fromEmail.isBlank()) {
        message.setFrom(fromEmail);
      }
      message.setTo(adminEmail);
      message.setSubject(EMAIL_SUBJECT);
      message.setText(
          "A user requested account deletion.\n\n"
              + "User ID: "
              + user.getId()
              + "\nUsername: "
              + user.getUsername()
              + "\nProvider email: "
              + providerEmail
              + "\nStatus: "
              + deletionRequest.getStatus().name()
              + "\nRequested at: "
              + deletionRequest.getRequestedAt()
              + "\nScheduled deletion at: "
              + deletionRequest.getScheduledDeletionAt()
              + "\n\nPlease complete deletion within 24 hours.");

      mailSender.send(message);
      System.out.println("Account deletion email sent for user " + user.getId());
    } catch (Exception e) {
      System.out.println(
          "Failed to send account deletion email for user " + user.getId() + ": " + e.getMessage());
    }
  }

  private AccountDeletionRequestResponseData mapToResponse(
      AccountDeletionRequestEntity deletionRequest) {
    return AccountDeletionRequestResponseData.builder()
        .status(deletionRequest.getStatus().name())
        .requestedAt(deletionRequest.getRequestedAt())
        .scheduledDeletionAt(deletionRequest.getScheduledDeletionAt())
        .build();
  }
}
