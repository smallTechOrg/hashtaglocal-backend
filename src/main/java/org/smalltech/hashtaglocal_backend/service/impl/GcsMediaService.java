package org.smalltech.hashtaglocal_backend.service.impl;

import java.net.URL;
import org.smalltech.hashtaglocal_backend.config.CustomProperties;
import org.smalltech.hashtaglocal_backend.infra.storage.SignedUrlGenerator;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;
import org.smalltech.hashtaglocal_backend.policy.FileNamingPolicy;
import org.smalltech.hashtaglocal_backend.service.MediaService;
import org.smalltech.hashtaglocal_backend.util.MimeTypeMapper;
import org.springframework.stereotype.Service;

// service for generating signed URLs for media uploads to Google Cloud Storage, using a file naming policy and MIME type mapping to determine the object path and content type of the uploaded media
@Service
public class GcsMediaService implements MediaService {

	private final SignedUrlGenerator signedUrlGenerator;
	private final FileNamingPolicy fileNamingPolicy;
	private final CustomProperties customProperties;

	public GcsMediaService(SignedUrlGenerator signedUrlGenerator, FileNamingPolicy fileNamingPolicy,
			CustomProperties customProperties) {
		this.signedUrlGenerator = signedUrlGenerator;
		this.fileNamingPolicy = fileNamingPolicy;
		this.customProperties = customProperties;
	}

	@Override
	public SignedUrlResponse generateUploadUrl(String contentType) {
		String extension = MimeTypeMapper.toExtension(contentType);
		String objectPath = fileNamingPolicy.generate(extension);

		URL signedUrl = signedUrlGenerator.generatePutUrl(customProperties.getStorage().getBucketName(), objectPath,
				contentType);

		var bucketName = customProperties.getStorage().getBucketName();

		return SignedUrlResponse.builder().signedUrl(signedUrl.toString()).path("gs://" + bucketName + "/" + objectPath)
				.build();
	}
}
