package org.smalltech.hashtaglocal_backend.util;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

// generates unique file names for uploaded media based on UTC timestamp and file extension, e.g.
// "2024-06-01-12-30-45-123.jpg"
@Component
public class FileNamingPolicy {

  public String generate(String extension) {
    String timestamp =
        LocalDateTime.now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS"));
    return timestamp + "." + extension;
  }
}
