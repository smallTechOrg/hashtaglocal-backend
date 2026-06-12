package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceTokenService {

  private final UserAuthSessionRepository userAuthSessionRepository;

  public DeviceTokenService(UserAuthSessionRepository userAuthSessionRepository) {
    this.userAuthSessionRepository = userAuthSessionRepository;
  }

  @Transactional
  public void register(String accessToken, String notificationToken, Platform platform) {
    UserAuthSessionEntity session =
        userAuthSessionRepository
            .findByAccessToken(accessToken)
            .orElseThrow(() -> new RuntimeException("Session not found"));

    if (notificationToken != null && (platform == Platform.ANDROID || platform == Platform.IOS)) {
      userAuthSessionRepository.clearNotificationTokenByUserIdAndPlatform(
          session.getUser().getId(), platform);
    }

    session.setNotificationToken(notificationToken);
    session.setPlatform(platform);
    userAuthSessionRepository.save(session);
  }

  @Transactional
  public void remove(String accessToken) {
    UserAuthSessionEntity session =
        userAuthSessionRepository
            .findByAccessToken(accessToken)
            .orElseThrow(() -> new RuntimeException("Session not found"));
    session.setNotificationToken(null);
    session.setIsActive(false);
    userAuthSessionRepository.save(session);
  }
}
