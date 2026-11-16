package org.smalltech.hashtaglocal_backend.util;

public final class AppVersionUtil {

  /** Must match the length of user_auth_sessions.app_version. */
  public static final int MAX_LENGTH = 50;

  private AppVersionUtil() {}

  /**
   * Returns the trimmed version, or null if it is missing, blank or too long to store. Version is
   * client-supplied metadata, so a bad value is dropped rather than failing login or refresh.
   */
  public static String sanitize(String appVersion) {
    if (appVersion == null) {
      return null;
    }
    String trimmed = appVersion.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_LENGTH) {
      return null;
    }
    return trimmed;
  }
}
