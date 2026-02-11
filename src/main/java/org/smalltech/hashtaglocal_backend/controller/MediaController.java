package org.smalltech.hashtaglocal_backend.controller;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/media")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Media", description = "Media upload and signed URL APIs")
public class MediaController {

	private static final String BUCKET_NAME = "hashtaglocalbucket";

	private final Storage storage;

	public MediaController(Storage storage) {
		this.storage = storage;
	}

	@GetMapping("/upload-url")
	@Operation(summary = "Generate signed upload URL", description = "Generates a V4 signed URL for uploading media files directly to Google Cloud Storage using HTTP PUT.")
	@ApiResponse(responseCode = "200", description = "Signed URL generated successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))

	public APIResponse getSignedUrl(
			@Parameter(description = "MIME type of the file to be uploaded (e.g. image/jpeg, image/png)", required = true, example = "image/jpeg") @RequestParam("content_type") String contentType) {

		// 1. Generate time-based path
		String extension = extractExtension(contentType);
		String objectPath = generateTimeBasedPath(extension);

		// 2. Create blob metadata
		BlobInfo blobInfo = BlobInfo.newBuilder(BUCKET_NAME, objectPath).setContentType(contentType).build();

		// 3. Generate signed URL
		URL signedUrl = storage.signUrl(blobInfo, 15, TimeUnit.MINUTES,
				Storage.SignUrlOption.httpMethod(HttpMethod.PUT), Storage.SignUrlOption.withV4Signature());

		// 4. Build response
		SignedUrlResponse mediaUrl = SignedUrlResponse.builder().signedUrl(signedUrl.toString())
				.path("gs://" + BUCKET_NAME + "/" + objectPath).build();

		ResponseData data = ResponseData.builder().mediaUrl(mediaUrl).build();

		return APIResponse.builder().data(data).build();
	}

	private String generateTimeBasedPath(String extension) {
		String timestamp = LocalDateTime.now(ZoneOffset.UTC)
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS"));
		return String.format("%s.%s", timestamp, extension);
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
