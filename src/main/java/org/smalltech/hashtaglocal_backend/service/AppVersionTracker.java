package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import java.time.temporal.IsoFields;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.repository.AppVersionSeenRepository;
import org.springframework.stereotype.Service;

/**
 * Logs the app version a session is on, once per session per ISO week, for the weekly version
 * metrics on the Ops Dashboard. Called at login and on every token refresh.
 */
@Service
@RequiredArgsConstructor
public class AppVersionTracker {

  private final AppVersionSeenRepository appVersionSeenRepository;

  /** Does nothing for a session with no version (web logins, builds before version tracking). */
  public void record(UserAuthSessionEntity session) {
    if (session.getId() == null || session.getUser() == null || session.getAppVersion() == null) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    appVersionSeenRepository.upsert(
        session.getId(),
        session.getUser().getId(),
        session.getPlatform() != null ? session.getPlatform().name() : null,
        session.getAppVersion(),
        now.get(IsoFields.WEEK_BASED_YEAR),
        now.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
        now);
  }
}
