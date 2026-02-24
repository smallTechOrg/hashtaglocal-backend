package org.smalltech.hashtaglocal_backend.service.impl;

import java.net.URL;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.smalltech.hashtaglocal_backend.infra.storage.SignedUrlGenerator;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;
import org.smalltech.hashtaglocal_backend.service.MediaService;
import org.smalltech.hashtaglocal_backend.util.FileNamingPolicy;
import org.smalltech.hashtaglocal_backend.util.MimeTypeMapper;
import org.springframework.stereotype.Service;

// service for generating signed URLs for media uploads to Google Cloud Storage, using a file naming policy and MIME type mapping to determine the object path and content type of the uploaded media
@Service
@RequiredArgsConstructor
public class GcsMediaService implements MediaService {

	private final SignedUrlGenerator signedUrlGenerator;
	private final FileNamingPolicy fileNamingPolicy;
	private final CustomProperties.Google googleProperties;

	@Override
	public SignedUrlResponse generateUploadUrl(String contentType) {
		String extension = MimeTypeMapper.toExtension(contentType);
		String objectName = fileNamingPolicy.generate(extension);
		String objectPath = "images/" + objectName;
		var bucketName = googleProperties.getStorage().getBucketName();

		URL signedUrl = signedUrlGenerator.generatePutUrl(bucketName, objectPath, contentType);

		return SignedUrlResponse.builder().signedUrl(signedUrl.toString()).path("gs://" + bucketName + "/" + objectPath)
				.build();
	}
}
