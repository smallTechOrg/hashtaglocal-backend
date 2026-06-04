package org.smalltech.hashtaglocal_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

@Configuration
public class FirebaseConfig {

  private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

  @Value("${firebase.credentials.path:firebase-key.json}")
  private String credentialsPath;

  @Bean
  @Nullable
  public FirebaseMessaging firebaseMessaging() {
    File credFile = new File(credentialsPath);
    if (!credFile.exists()) {
      log.warn(
          "Firebase credentials not found at '{}'; FCM push notifications disabled",
          credentialsPath);
      return null;
    }
    try {
      if (FirebaseApp.getApps().isEmpty()) {
        try (FileInputStream serviceAccount = new FileInputStream(credFile)) {
          FirebaseOptions options =
              FirebaseOptions.builder()
                  .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                  .build();
          FirebaseApp.initializeApp(options);
        }
      }
      return FirebaseMessaging.getInstance();
    } catch (IOException e) {
      log.warn(
          "Failed to initialize Firebase from '{}'; FCM push notifications disabled: {}",
          credentialsPath,
          e.getMessage());
      return null;
    }
  }
}
