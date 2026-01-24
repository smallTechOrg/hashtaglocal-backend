package org.smalltech.hashtaglocal_backend.service;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class GCSService {

	private static final String BUCKET_NAME = "hashtaglocalbucket";
	private static final long SIGNED_URL_DURATION_MINUTES = 60;

	private final Storage storage;

	public GCSService(Storage storage) {
		this.storage = storage;
	}

	/**
	 * Generates a signed URL for a given GCS path.
	 *
	 * @param gcsPath
	 *            the GCS path in format "gs://bucket-name/object-path" or just
	 *            "object-path"
	 * @return a signed URL string
	 */
	public String generateSignedUrl(String gcsPath) {
		// Extract object path from GCS path
		String objectPath = extractObjectPath(gcsPath);

		// Create blob metadata
		BlobInfo blobInfo = BlobInfo.newBuilder(BUCKET_NAME, objectPath).build();

		// Generate signed URL with 60 minute expiration
		URL signedUrl = storage.signUrl(blobInfo, SIGNED_URL_DURATION_MINUTES, TimeUnit.MINUTES,
				Storage.SignUrlOption.httpMethod(HttpMethod.GET), Storage.SignUrlOption.withV4Signature());

		return signedUrl.toString();
	}

	/**
	 * Extracts the object path from a GCS path.
	 *
	 * @param gcsPath
	 *            the GCS path in format "gs://bucket-name/object-path" or just
	 *            "object-path"
	 * @return the object path
	 */
	private String extractObjectPath(String gcsPath) {
		if (gcsPath == null || gcsPath.isEmpty()) {
			throw new IllegalArgumentException("GCS path cannot be null or empty");
		}

		// If path starts with gs://, extract the object path
		if (gcsPath.startsWith("gs://")) {
			// Format: gs://bucket-name/object-path
			String withoutScheme = gcsPath.substring(5); // Remove "gs://"
			int slashIndex = withoutScheme.indexOf('/');
			if (slashIndex > 0) {
				return withoutScheme.substring(slashIndex + 1); // Return object-path
			}
		}

		// Otherwise assume it's already the object path
		return gcsPath;
	}
}
