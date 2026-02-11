package org.smalltech.hashtaglocal_backend.service.import_job;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.smalltech.hashtaglocal_backend.dto.BlrPagesIssueDTO;
import org.smalltech.hashtaglocal_backend.dto.BlrPagesResponse;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueImportJob;
import org.smalltech.hashtaglocal_backend.entity.IssueImportStatus;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.Location;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.repository.IssueImportJobRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueImportStatusRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IssueImportServiceTest {

	@Mock
	private IssueImportJobRepository jobRepository;

	@Mock
	private IssueImportStatusRepository statusRepository;

	@Mock
	private IssueRepository issueRepository;

	@Mock
	private MediaRepository mediaRepository;

	@Mock
	private LocationRepository locationRepository;

	@Mock
	private LocalityRepository localityRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private RestTemplate restTemplate;

	@Mock
	private GCSService gcsService;

	private IssueImportService service;

	@BeforeEach
	void setUp() {
		service = new IssueImportService(jobRepository, statusRepository, issueRepository, mediaRepository,
				locationRepository, localityRepository, userRepository, restTemplate, gcsService);

		when(jobRepository.save(any(IssueImportJob.class))).thenAnswer(invocation -> {
			IssueImportJob job = invocation.getArgument(0);
			if (job.getId() == null) {
				job.setId(1L);
			}
			return job;
		});

		when(statusRepository.save(any(IssueImportStatus.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	@DisplayName("Imports and stores blr-pages issues")
	void importsBlrPagesIssues() {
		BlrPagesIssueDTO dto = BlrPagesIssueDTO.builder().uuid("test-uuid-1").lat(12.9).lng(77.6)
				.image("https://example.com/photo.jpg").category(3).createdAt("2025-06-17T17:21:24.077Z").build();

		BlrPagesResponse.Result result = new BlrPagesResponse.Result();
		result.setData(java.util.List.of(dto));
		BlrPagesResponse response = new BlrPagesResponse();
		response.setSuccess(true);
		response.setResult(result);

		when(restTemplate.getForObject(anyString(), eq(BlrPagesResponse.class))).thenReturn(response);

		when(statusRepository.existsBySourceAndSourceIssueId(any(), anyString())).thenReturn(false);

		Locality locality = Locality.builder().id(10L).hashtag("#bengaluru").name("Bengaluru").geoBoundary(null)
				.build();
		when(localityRepository.findByHashtag("#bengaluru")).thenReturn(java.util.Optional.of(locality));
		when(localityRepository.findByHashtag("world")).thenReturn(java.util.Optional.empty());

		Location location = Location.builder().id(5L).name("Bengaluru").build();
		when(locationRepository.save(any(Location.class))).thenReturn(location);

		UserEntity user = UserEntity.builder().id(1L).username("admin").locale("en").build();
		when(userRepository.findById(1L)).thenReturn(java.util.Optional.of(user));

		IssueEntity savedIssue = IssueEntity.builder().id(99L).build();
		when(issueRepository.save(any(IssueEntity.class))).thenReturn(savedIssue);

		ResponseEntity<byte[]> download = new ResponseEntity<>(new byte[]{1, 2, 3}, HttpStatus.OK);
		when(restTemplate.exchange(eq(dto.getImage()), eq(HttpMethod.GET), any(HttpEntity.class), eq(byte[].class)))
				.thenReturn(download);

		when(gcsService.uploadObject(anyString(), any(byte[].class), anyString()))
				.thenReturn("gs://bucket/path/photo.jpg");

		IssueImportJob job = service.importBlrPages();

		assertEquals(IssueImportJob.JobStatus.COMPLETED, job.getStatus());
		assertEquals(1, job.getSuccessCount());
		assertEquals(0, job.getFailureCount());
		assertEquals(0, job.getSkippedCount());

		verify(mediaRepository).save(any(MediaEntity.class));
	}

	@Test
	@DisplayName("Skips duplicate source ids")
	void skipsDuplicateIssues() {
		BlrPagesIssueDTO dto = BlrPagesIssueDTO.builder().uuid("dup-uuid-1").lat(12.9).lng(77.6)
				.image("https://example.com/photo.jpg").category(3).createdAt("2025-06-17T17:21:24.077Z").build();

		BlrPagesResponse.Result result = new BlrPagesResponse.Result();
		result.setData(java.util.List.of(dto));
		BlrPagesResponse response = new BlrPagesResponse();
		response.setSuccess(true);
		response.setResult(result);

		when(restTemplate.getForObject(anyString(), eq(BlrPagesResponse.class))).thenReturn(response);

		when(statusRepository.existsBySourceAndSourceIssueId(any(), anyString())).thenReturn(true);

		IssueImportJob job = service.importBlrPages();

		assertEquals(IssueImportJob.JobStatus.COMPLETED, job.getStatus());
		assertEquals(0, job.getSuccessCount());
		assertEquals(0, job.getFailureCount());
		assertEquals(1, job.getSkippedCount());

		verify(statusRepository, atLeastOnce()).save(any(IssueImportStatus.class));
	}
}
