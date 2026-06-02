package org.smalltech.hashtaglocal_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FirebaseConfig {

  @Value("${firebase.credentials.path:firebase-key.json}")
  private String credentialsPath;

  @Bean
  public FirebaseMessaging firebaseMessaging() throws IOException {
    if (FirebaseApp.getApps().isEmpty()) {
      try (FileInputStream serviceAccount = new FileInputStream(credentialsPath)) {
        FirebaseOptions options =
            FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();
        FirebaseApp.initializeApp(options);
      }
    }
    return FirebaseMessaging.getInstance();
  }
}
