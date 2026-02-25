package org.smalltech.hashtaglocal_backend.util;

import java.util.Map;
import lombok.experimental.UtilityClass;

// maps MIME types to file extensions for uploaded media, defaults to "bin" if unknown or missing
@UtilityClass
public class MimeTypeMapper {

  private static final Map<String, String> MIME_TO_EXTENSION =
      Map.of("image/jpeg", "jpg", "image/png", "png");

  public static String toExtension(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return "bin";
    }
    return MIME_TO_EXTENSION.getOrDefault(contentType, "bin");
  }
}
