package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.net.URL;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;
import org.smalltech.hashtaglocal_backend.model.response.MediaUploadResponseData;
import org.smalltech.hashtaglocal_backend.service.MediaService;

class MediaControllerTests {

	@Test
	void getSignedUrl_shouldReturnValidApiResponse() throws Exception {
		// Arrange
		MediaService mediaService = Mockito.mock(MediaService.class);
		MediaController controller = new MediaController(mediaService);

		URL fakeSignedUrl = new URL("https://storage.googleapis.com/fake-upload-url");

		when(mediaService.generateUploadUrl(Mockito.anyString())).thenReturn(SignedUrlResponse.builder()
				.signedUrl(fakeSignedUrl.toString()).path("gs://hashtaglocalbucket/test.jpg").build());

		// Act
		NewAPIResponse<MediaUploadResponseData> response = controller.getSignedUrl("image/jpeg");
		MediaUploadResponseData data = response.getData();
		SignedUrlResponse mediaUrl = data.getMediaUrl();

		// Assert-structure
		assertNotNull(response);
		assertNotNull(data);
		assertNotNull(mediaUrl);

		// Assert-values
		assertEquals(fakeSignedUrl.toString(), mediaUrl.getSignedUrl());
		assertTrue(mediaUrl.getPath().startsWith("gs://hashtaglocalbucket/"));
		assertTrue(mediaUrl.getPath().endsWith(".jpg"));
	}
}
