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

  /**
   * Orders versions such as "2.4.1" by their numeric parts, so "2.10.0" is newer than "2.9.3". A
   * missing part counts as 0 and a part's leading digits are its number ("1-beta" is 1); a part
   * with no digits counts as -1. Strings whose numbers tie ("2.4" and "2.4.0") are ordered
   * alphabetically, so the order is total and consistent with equals.
   */
  public static int compare(String a, String b) {
    String[] pa = a.split("\\.");
    String[] pb = b.split("\\.");
    for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
      long na = i < pa.length ? leadingNumber(pa[i]) : 0;
      long nb = i < pb.length ? leadingNumber(pb[i]) : 0;
      if (na != nb) {
        return Long.compare(na, nb);
      }
    }
    return a.compareTo(b);
  }

  private static long leadingNumber(String part) {
    int end = 0;
    while (end < part.length() && end < 18 && Character.isDigit(part.charAt(end))) {
      end++;
    }
    return end == 0 ? -1 : Long.parseLong(part.substring(0, end));
  }
}
