package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.entity.GovPortalEntity;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.exception.DownstreamServiceException;
import org.smalltech.hashtaglocal_backend.model.PortalEnum;
import org.smalltech.hashtaglocal_backend.repository.GovPortalRepository;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Properties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.web.client.RestTemplate;

/** BBMP report through scraper0 v2: idempotency key, and the 200/202/409/422 mapping. */
class PortalComplaintReportingServiceTest {

  private static final String WRITE_URL =
      "https://scraper0.test/v2/sites/smartoneblr-com/actions/report_issue/write";

  private Scraper0Properties properties;
  private MockRestServiceServer server;
  private GovPortalRepository govPortalRepository;
  private PortalComplaintReportingService service;
  private final IssueEntity issue = IssueEntity.builder().id(42L).build();

  @BeforeEach
  void setUp() {
    properties = new Scraper0Properties();
    properties.setBaseUrl("https://scraper0.test");
    properties.setApiKey("s0-key");
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    Scraper0Client client = new Scraper0Client(properties, new ObjectMapper(), t -> restTemplate);

    UserRepository users = mock(UserRepository.class);
    IssueRepository issues = mock(IssueRepository.class);
    LocalityRepository localities = mock(LocalityRepository.class);
    govPortalRepository = mock(GovPortalRepository.class);
    when(users.findById(1L)).thenReturn(Optional.of(UserEntity.builder().id(1L).build()));
    when(issues.findById(42L)).thenReturn(Optional.of(issue));
    when(localities.findByHashtag(any())).thenReturn(Optional.empty());
    when(localities.findContainingLocality(anyDouble(), anyDouble()))
        .thenReturn(Optional.of(Locality.builder().name("Bengaluru").build()));
    when(govPortalRepository.findByIssueEntityId(42L)).thenReturn(List.of());

    service =
        new PortalComplaintReportingService(client, users, issues, localities, govPortalRepository);
  }

  private static ReportComplaintRequestDTO request() {
    return ReportComplaintRequestDTO.builder()
        .source("GOV_ISSUE_PORTAL")
        .context(
            ReportComplaintRequestDTO.Context.builder()
                .portal("SMARTONEBLR")
                .action(
                    ReportComplaintRequestDTO.Action.builder()
                        .type("REPORT_ISSUE")
                        .data(
                            ReportComplaintRequestDTO.Data.builder()
                                .category("Road Engineering")
                                .subCategory("Potholes")
                                .description("Broken road near signal")
                                .mediaUrl("https://example.com/image.jpg")
                                .latitude("12.9716")
                                .longitude("77.5946")
                                .build())
                        .build())
                .auth(
                    ReportComplaintRequestDTO.Auth.builder()
                        .username("portal_user")
                        .password("portal_pass")
                        .build())
                .build())
        .build();
  }

  private ReportComplaintResponseDTO report() {
    return service.reportComplaint("report_issue", 42L, 1L, request());
  }

  private ResponseActions expectWrite() {
    return server
        .expect(requestTo(WRITE_URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "hashtaglocal-issue-42"))
        .andExpect(header("X-API-Key", "s0-key"));
  }

  @Test
  void successSendsTheIdempotencyKeyAndStoresTheTrackingId() {
    expectWrite()
        .andExpect(jsonPath("$.input.category").value("Road Engineering"))
        .andExpect(jsonPath("$.input.sub_category").value("Potholes"))
        .andExpect(jsonPath("$.input.media_url").value("https://example.com/image.jpg"))
        .andExpect(jsonPath("$.input.latitude").value("12.9716"))
        .andExpect(jsonPath("$.auth.username").value("portal_user"))
        .andExpect(jsonPath("$.auth.password").value("portal_pass"))
        .andExpect(jsonPath("$.force").value(false))
        .andExpect(jsonPath("$.timeout_s").value(300))
        .andRespond(
            withSuccess(
                "{\"data\":{\"status\":\"succeeded\",\"output\":{\"tracking_id\":21025835},"
                    + "\"idempotency_key\":\"hashtaglocal-issue-42\",\"job_id\":\"j1\",\"replayed\":false}}",
                MediaType.APPLICATION_JSON));

    ReportComplaintResponseDTO response = report();

    server.verify();
    assertThat(response.getTrackingId()).isEqualTo(21025835L);
    ArgumentCaptor<GovPortalEntity> saved = ArgumentCaptor.forClass(GovPortalEntity.class);
    verify(govPortalRepository).save(saved.capture());
    assertThat(saved.getValue().getTrackingId()).isEqualTo("21025835");
    assertThat(saved.getValue().getPortal()).isEqualTo(PortalEnum.SMARTONEBLR);
    assertThat(saved.getValue().getStatus()).isEqualTo("OPEN");
    assertThat(saved.getValue().getIssueEntity()).isSameAs(issue);
  }

  @Test
  void aReplayedWriteDoesNotStoreTheTrackingIdTwice() {
    when(govPortalRepository.findByIssueEntityId(42L))
        .thenReturn(List.of(GovPortalEntity.builder().trackingId("21025835").build()));
    expectWrite()
        .andRespond(
            withSuccess(
                "{\"data\":{\"status\":\"succeeded\",\"output\":{\"tracking_id\":21025835},"
                    + "\"replayed\":true}}",
                MediaType.APPLICATION_JSON));

    assertThat(report().getTrackingId()).isEqualTo(21025835L);
    verify(govPortalRepository, never()).save(any());
  }

  @Test
  void stillRunningAsksToTryAgainShortly() {
    expectWrite()
        .andRespond(
            withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"data\":{\"status\":\"running\",\"job_id\":\"j2\"}}"));

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
              assertThat(e.getType()).isEqualTo("PORTAL_PENDING");
              assertThat(e.getMessage()).contains("try again shortly");
            });
    verify(govPortalRepository, never()).save(any());
  }

  @Test
  void outcomeUnknownAsksToCheckThePortal() {
    expectWrite()
        .andRespond(
            withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"error\":{\"code\":\"outcome_unknown\",\"message\":\"may have been"
                        + " submitted\",\"job_id\":\"j3\"}}"));

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
              assertThat(e.getType()).isEqualTo("OUTCOME_UNKNOWN");
              assertThat(e.getMessage())
                  .isEqualTo("The complaint may have been filed; check the portal before retrying");
            });
    verify(govPortalRepository, never()).save(any());
  }

  @Test
  void actionFailedReturnsThePortalMessage() {
    expectWrite()
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"error\":{\"code\":\"action_failed\",\"message\":\"Invalid credentials\"}}"));

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
              assertThat(e.getMessage()).isEqualTo("Invalid credentials");
            });
  }

  @Test
  void otherScraper0ErrorsAre502() {
    expectWrite()
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    "{\"error\":{\"code\":\"action_not_available\",\"message\":\"not locked\"}}"));

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
              assertThat(e.getMessage()).isEqualTo("action_not_available: not locked");
            });
  }

  @Test
  void aMissingTrackingIdIs502() {
    expectWrite()
        .andRespond(
            withSuccess(
                "{\"data\":{\"status\":\"succeeded\",\"output\":{}}}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
  }

  @Test
  void notConfiguredIsAConfigurationError() {
    properties.setBaseUrl("");

    assertThatThrownBy(this::report)
        .isInstanceOfSatisfying(
            DownstreamServiceException.class,
            e -> {
              assertThat(e.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
              assertThat(e.getType()).isEqualTo("CONFIGURATION");
            });
    server.verify();
  }

  @Test
  void theKeyIsPerIssue() {
    assertThat(PortalComplaintReportingService.idempotencyKey(1234L))
        .isEqualTo("hashtaglocal-issue-1234");
  }
}
