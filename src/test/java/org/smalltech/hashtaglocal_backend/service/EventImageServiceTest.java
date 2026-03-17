package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.model.MediaTypeModel;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.util.FileNamingPolicy;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Unit tests for {@link EventImageService}.
 *
 * <p>Extension-detection and skip-scenario cases are driven from {@code
 * event-image-service-test-cases.json} so new URL patterns can be added without touching Java code.
 *
 * <p>Note: the "event already exists → image is not downloaded" invariant is enforced one layer up
 * in {@link EventImportService} via its duplicate check before calling this service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventImageService")
class EventImageServiceTest {

  @Mock private GCSService gcsService;
  @Mock private RestTemplate restTemplate;
  @Mock private MediaRepository mediaRepository;
  @Mock private FileNamingPolicy fileNamingPolicy;

  @InjectMocks private EventImageService eventImageService;

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final byte[] VALID_BYTES = new byte[] {1, 2, 3};
  private static final String GCS_PATH = "gs://bucket/events/2026-03-17-10-00-00-000.jpg";

  // ---------------------------------------------------------------------------
  // Extension detection (parameterised from JSON)
  // ---------------------------------------------------------------------------

  static Stream<Arguments> extensionDetectionCases() throws IOException {
    JsonNode root =
        MAPPER.readTree(
            EventImageServiceTest.class.getResourceAsStream(
                "/event-image-service-test-cases.json"));
    return Stream.iterate(0, i -> i + 1)
        .limit(root.get("extension_detection").size())
        .map(
            i -> {
              JsonNode node = root.get("extension_detection").get(i);
              return Arguments.of(
                  node.get("url").asText(),
                  node.get("expected_ext").asText(),
                  node.get("expected_content_type").asText(),
                  node.get("description").asText());
            });
  }

  @ParameterizedTest(name = "{3}")
  @MethodSource("extensionDetectionCases")
  @DisplayName("Correct extension and content-type derived from image URL")
  void detectsCorrectExtensionAndContentType(
      String imageUrl, String expectedExt, String expectedContentType, String description) {

    when(restTemplate.getForObject(imageUrl, byte[].class)).thenReturn(VALID_BYTES);
    when(fileNamingPolicy.generate(expectedExt))
        .thenReturn("2026-03-17-10-00-00-000." + expectedExt);
    when(gcsService.uploadObject(anyString(), eq(VALID_BYTES), eq(expectedContentType)))
        .thenReturn(GCS_PATH);
    when(mediaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    eventImageService.downloadAndStore(imageUrl);

    ArgumentCaptor<String> objectNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> contentTypeCaptor = ArgumentCaptor.forClass(String.class);
    verify(gcsService).uploadObject(objectNameCaptor.capture(), any(), contentTypeCaptor.capture());

    assertTrue(
        objectNameCaptor.getValue().endsWith("." + expectedExt),
        "Object name should end with ." + expectedExt + " but was: " + objectNameCaptor.getValue());
    assertEquals(expectedContentType, contentTypeCaptor.getValue());
  }

  // ---------------------------------------------------------------------------
  // Skip scenarios (parameterised from JSON)
  // ---------------------------------------------------------------------------

  static Stream<Arguments> skipScenarioCases() throws IOException {
    JsonNode root =
        MAPPER.readTree(
            EventImageServiceTest.class.getResourceAsStream(
                "/event-image-service-test-cases.json"));
    return Stream.iterate(0, i -> i + 1)
        .limit(root.get("skip_scenarios").size())
        .map(
            i -> {
              JsonNode node = root.get("skip_scenarios").get(i);
              boolean throwsEx = node.get("throws").asBoolean();
              boolean nullBytes = node.get("bytes").isNull() || node.get("bytes").size() == 0;
              return Arguments.of(throwsEx, nullBytes, node.get("description").asText());
            });
  }

  @ParameterizedTest(name = "{2}")
  @MethodSource("skipScenarioCases")
  @DisplayName("No GCS upload when download fails or returns empty")
  void noGcsUploadOnDownloadFailure(
      boolean throwsException, boolean nullBytes, String description) {
    String url = "https://cdn.example.com/image.jpg";

    if (throwsException) {
      when(restTemplate.getForObject(url, byte[].class))
          .thenThrow(
              HttpClientErrorException.NotFound.create(
                  "Not Found",
                  org.springframework.http.HttpStatus.NOT_FOUND,
                  "Not Found",
                  null,
                  null,
                  null));
    } else {
      when(restTemplate.getForObject(url, byte[].class)).thenReturn(nullBytes ? null : new byte[0]);
    }

    MediaEntity result = eventImageService.downloadAndStore(url);

    assertNull(result, description + " → should return null");
    verifyNoInteractions(gcsService);
    verifyNoInteractions(mediaRepository);
  }

  // ---------------------------------------------------------------------------
  // Successful full flow
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Successful flow — GCS path stored in returned MediaEntity")
  void successfulDownloadAndStore() {
    String imageUrl = "https://cdn.example.com/event.jpg";
    String gcsPath = "gs://bucket/events/2026-03-17-10-00-00-000.jpg";

    when(restTemplate.getForObject(imageUrl, byte[].class)).thenReturn(VALID_BYTES);
    when(fileNamingPolicy.generate("jpg")).thenReturn("2026-03-17-10-00-00-000.jpg");
    when(gcsService.uploadObject(anyString(), any(), any())).thenReturn(gcsPath);
    when(mediaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    MediaEntity result = eventImageService.downloadAndStore(imageUrl);

    assertNotNull(result);
    assertEquals(gcsPath, result.getUrl());
    assertEquals(MediaTypeModel.PHOTO, result.getType());
  }

  @Test
  @DisplayName("GCS object path is under events/ prefix")
  void objectNameHasEventsPrefix() {
    String imageUrl = "https://cdn.example.com/event.jpg";

    when(restTemplate.getForObject(imageUrl, byte[].class)).thenReturn(VALID_BYTES);
    when(fileNamingPolicy.generate("jpg")).thenReturn("2026-03-17-10-00-00-000.jpg");
    when(gcsService.uploadObject(anyString(), any(), any())).thenReturn(GCS_PATH);
    when(mediaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    eventImageService.downloadAndStore(imageUrl);

    ArgumentCaptor<String> objectNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(gcsService).uploadObject(objectNameCaptor.capture(), any(), any());
    assertTrue(
        objectNameCaptor.getValue().startsWith("events/"),
        "Object name must be under events/ prefix");
  }

  // ---------------------------------------------------------------------------
  // DB save failure — orphan cleanup
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("GCS object is deleted when DB save fails — no orphan left")
  void deletesGcsObjectWhenDbSaveFails() {
    String imageUrl = "https://cdn.example.com/event.jpg";

    when(restTemplate.getForObject(imageUrl, byte[].class)).thenReturn(VALID_BYTES);
    when(fileNamingPolicy.generate("jpg")).thenReturn("2026-03-17-10-00-00-000.jpg");
    when(gcsService.uploadObject(anyString(), any(), any())).thenReturn(GCS_PATH);
    when(mediaRepository.save(any())).thenThrow(new RuntimeException("DB constraint violation"));

    MediaEntity result = eventImageService.downloadAndStore(imageUrl);

    assertNull(result);
    verify(gcsService).deleteObject(GCS_PATH);
  }

  @Test
  @DisplayName("Returns null and does not call deleteObject when download itself fails")
  void doesNotCallDeleteWhenDownloadFails() {
    String imageUrl = "https://cdn.example.com/event.jpg";
    when(restTemplate.getForObject(imageUrl, byte[].class))
        .thenThrow(new RuntimeException("Network error"));

    MediaEntity result = eventImageService.downloadAndStore(imageUrl);

    assertNull(result);
    verify(gcsService, never()).deleteObject(any());
    verifyNoInteractions(mediaRepository);
  }
}
