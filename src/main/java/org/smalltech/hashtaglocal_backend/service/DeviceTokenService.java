package org.smalltech.hashtaglocal_backend.service;

import jakarta.transaction.Transactional;
import org.smalltech.hashtaglocal_backend.entity.DeviceTokenEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.smalltech.hashtaglocal_backend.repository.DeviceTokenRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final UserRepository userRepository;

  public DeviceTokenService(
      DeviceTokenRepository deviceTokenRepository, UserRepository userRepository) {
    this.deviceTokenRepository = deviceTokenRepository;
    this.userRepository = userRepository;
  }

  /**
   * Upserts a device token for the authenticated user. If the token already exists (re-used
   * device), its user_id is updated to the current user.
   */
  @Transactional
  public void register(Long userId, String token, Platform platform) {
    UserEntity user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

    deviceTokenRepository
        .findByToken(token)
        .ifPresentOrElse(
            existing -> existing.setUser(user),
            () ->
                deviceTokenRepository.save(
                    DeviceTokenEntity.builder().user(user).token(token).platform(platform).build()));
  }

  /** Deletes all device tokens for the authenticated user on the given platform. */
  @Transactional
  public void remove(Long userId, Platform platform) {
    deviceTokenRepository.deleteByUserIdAndPlatform(userId, platform);
  }
}
