package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.SignedUrlResponse;

class MediaControllerTests {

	@Test
	void getSignedUrl_shouldReturnValidApiResponse() throws Exception {

		// Arrange
		Storage mockStorage = Mockito.mock(Storage.class);
		MediaController controller = new MediaController(mockStorage);

		URL fakeSignedUrl = new URL("https://storage.googleapis.com/fake-upload-url");

		when(mockStorage.signUrl(any(BlobInfo.class), Mockito.eq(15L), Mockito.eq(TimeUnit.MINUTES),
				any(Storage.SignUrlOption.class), any(Storage.SignUrlOption.class))).thenReturn(fakeSignedUrl);

		// Act
		APIResponse response = controller.getSignedUrl("image/jpeg");
		ResponseData data = response.getData();
		SignedUrlResponse mediaUrl = data.getMediaUrl();

		// Assert
		assertEquals(fakeSignedUrl.toString(), mediaUrl.getSignedUrl());
		assertTrue(mediaUrl.getPath().startsWith("gs://hashtaglocalbucket/"));
		assertTrue(mediaUrl.getPath().endsWith(".jpg"));
	}
}
