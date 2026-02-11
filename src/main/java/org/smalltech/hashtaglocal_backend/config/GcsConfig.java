package org.smalltech.hashtaglocal_backend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.FileInputStream;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GcsConfig {

	@Value("${gcs.credentials.path:gcs-key.json}")
	private String credentialsPath;

	@Value("${gcs.project.id:}")
	private String projectId;

	@Bean
	public Storage storage() throws IOException {
		// First check if GOOGLE_APPLICATION_CREDENTIALS is set in environment
		String envCredentials = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
		if (envCredentials != null && !envCredentials.isEmpty()) {
			credentialsPath = envCredentials;
		}

		// Load credentials from file
		GoogleCredentials credentials;
		try (FileInputStream serviceAccountStream = new FileInputStream(credentialsPath)) {
			credentials = GoogleCredentials.fromStream(serviceAccountStream);
		}

		// Build storage options
		StorageOptions.Builder builder = StorageOptions.newBuilder().setCredentials(credentials);

		// Set project ID if provided
		if (projectId != null && !projectId.isEmpty()) {
			builder.setProjectId(projectId);
		}

		return builder.build().getService();
	}
}
