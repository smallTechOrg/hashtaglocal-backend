package org.smalltech.hashtaglocal_backend.controller;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/v1/media")
public class GetSignedUrl {

	private static final String BUCKET_NAME = "hashtaglocalbucket";

	private final Storage storage = StorageOptions.getDefaultInstance().getService();
	// ✅ ADD IT HERE
    @PostConstruct
    public void checkCreds() throws Exception {
        GoogleCredentials creds = GoogleCredentials.getApplicationDefault();
        System.out.println("GCP credentials class = " + creds.getClass().getName());
    }

	@GetMapping("/signed-url")
	public APIResponse getSignedUrl(@RequestParam("content_type") String contentType) {

		// 1. Generate time-based path
		String extension = extractExtension(contentType);
		String objectPath = generateTimeBasedPath(extension);

		// 2. Create blob metadata
		BlobInfo blobInfo = BlobInfo.newBuilder(BUCKET_NAME, objectPath).setContentType(contentType).build();

		// 3. Generate signed URL
		URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
				Storage.SignUrlOption.httpMethod(HttpMethod.PUT), Storage.SignUrlOption.withV4Signature());

		// 4. Build response
		ResponseData data = new ResponseData();
		data.setSignedUrl(signedUrl.toString());
		data.setPath("gs://" + BUCKET_NAME + "/" + objectPath);

		APIResponse response = new APIResponse();
		response.setData(data);

		return response;
	}

	// ---------- helpers ----------

	private String generateTimeBasedPath(String extension) {
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

		return String.format("posts/%04d/%02d/%02d/%02d/%s.%s", now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
				now.getHour(), UUID.randomUUID(), extension);
	}

	private String extractExtension(String contentType) {
		if (contentType == null)
			return "bin";
		return switch (contentType) {
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			default -> "bin";
		};
	}
}
