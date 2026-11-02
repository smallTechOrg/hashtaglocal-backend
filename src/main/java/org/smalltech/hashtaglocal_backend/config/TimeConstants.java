package org.smalltech.hashtaglocal_backend.config;

import java.time.ZoneId;

/**
 * The one time zone the app's "today" is computed in, defined once so every scheduler, generator
 * and listener agrees on which day it is.
 */
public final class TimeConstants {

  private TimeConstants() {}

  /** Zone id as a string, for annotations that need a compile-time constant (e.g. @Scheduled). */
  public static final String APP_ZONE_ID = "Asia/Kolkata";

  /** The same zone as a {@link ZoneId}, for {@code LocalDate.now(APP_ZONE)} and friends. */
  public static final ZoneId APP_ZONE = ZoneId.of(APP_ZONE_ID);
}
