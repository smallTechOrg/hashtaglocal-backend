package org.smalltech.hashtaglocal_backend.service.scraper0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.WriteResult;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class Scraper0ClientTest {

  private static final String BASE = "https://scraper0.test";

  private Scraper0Properties properties;
  private MockRestServiceServer server;
  private Scraper0Client client;
  private final List<Integer> templateTimeouts = new ArrayList<>();

  @BeforeEach
  void setUp() {
    properties = new Scraper0Properties();
    properties.setBaseUrl(BASE + "/");
    properties.setApiKey("s0-key");
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client =
        new Scraper0Client(
            properties,
            new ObjectMapper(),
            timeoutS -> {
              templateTimeouts.add(timeoutS);
              return restTemplate;
            });
  }

  private static String json(String s) {
    return s.replace('\'', '"');
  }

  // ---------------------------------------------------------------------------
  // Reads
  // ---------------------------------------------------------------------------

  @Test
  void readItemsSendsOptionsAndParsesTheItems() {
    server
        .expect(requestTo(BASE + "/v2/sites/teameverest-ngo/actions/fetch_events/read"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-API-Key", "s0-key"))
        .andExpect(jsonPath("$.options.event_filter").value("cleanups"))
        .andExpect(jsonPath("$.options.categories[0]").value("OTHER"))
        .andExpect(jsonPath("$.freshness_hours").value(12))
        .andExpect(jsonPath("$.timeout_s").value(60))
        .andRespond(
            withSuccess(
                json(
                    "{'data': {'site_id': 'teameverest-ngo', 'action': 'fetch_events', 'version': 1,"
                        + " 'items': [{'id': 'itm_1', 'updated_at': '2026-11-01T00:00:00Z', 'data':"
                        + " {'name': 'Beach cleanup', 'start_time': '2026-12-01T04:30:00.000Z',"
                        + " 'image': 'https://img/x.jpg', 'image_expires_at':"
                        + " '2026-12-01T06:00:00.000Z', 'category': 'BEACH_CLEANUP',"
                        + " 'is_online': false, 'latitude': 12.9, 'source_url': 'https://src'}}],"
                        + " 'complete': false, 'scraped_at': '2026-11-26T00:00:00Z',"
                        + " 'refresh_job_id': 'job_9', 'health': 'ok', 'ai_error': 'quota',"
                        + " 'unknown_field': 1}}"),
                MediaType.APPLICATION_JSON));

    ReadResult result =
        client.readItems(
            "teameverest-ngo",
            "fetch_events",
            Map.of("event_filter", "cleanups", "categories", List.of("OTHER")),
            12);

    server.verify();
    assertThat(result.complete()).isFalse();
    assertThat(result.refreshJobId()).isEqualTo("job_9");
    assertThat(result.aiError()).isEqualTo("quota");
    assertThat(result.items()).hasSize(1);
    var item = result.items().get(0);
    assertThat(item.id()).isEqualTo("itm_1");
    assertThat(item.data().startTime()).isEqualTo("2026-12-01T04:30:00.000Z");
    assertThat(item.data().isOnline()).isFalse();
    assertThat(item.data().latitude()).isEqualTo(12.9);
    assertThat(item.data().sourceUrl()).isEqualTo("https://src");
    assertThat(item.data().image()).isEqualTo("https://img/x.jpg");
    assertThat(item.data().imageExpiresAt()).isEqualTo("2026-12-01T06:00:00.000Z");
    assertThat(templateTimeouts).containsExactly(60);
  }

  @Test
  void nothingIsSentWithoutAnApiKey() {
    properties.setApiKey(" ");

    assertThat(client.isConfigured()).isFalse();
    assertThatThrownBy(() -> client.listSites("ever"))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(Scraper0Exception.NOT_CONFIGURED);
              assertThat(e.getMessage()).contains("SCRAPER0_API_KEY");
            });
    server.verify();
  }

  @Test
  void v2ErrorsBecomeTypedExceptions() {
    server
        .expect(requestTo(BASE + "/v2/sites/karmana-in/actions/fetch_events/read"))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(
                    json(
                        "{'error': {'code': 'site_not_onboarded', 'message': 'karmana-in is not"
                            + " onboarded'}}")));

    assertThatThrownBy(() -> client.readItems("karmana-in", "fetch_events", Map.of(), 48))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(404);
              assertThat(e.getCode()).isEqualTo("site_not_onboarded");
              assertThat(e.isNotOnboarded()).isTrue();
              assertThat(e.describe()).isEqualTo("site_not_onboarded: karmana-in is not onboarded");
            });
  }

  @Test
  void invalidInputOnReadIs422() {
    server
        .expect(requestTo(BASE + "/v2/sites/a/actions/fetch_events/read"))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("{'error': {'code': 'invalid_input', 'message': 'from: bad date'}}")));

    assertThatThrownBy(() -> client.readItems("a", "fetch_events", Map.of(), 48))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(422);
              assertThat(e.getCode()).isEqualTo("invalid_input");
            });
  }

  @Test
  void aNonJsonErrorKeepsTheHttpStatusAsCode() {
    server
        .expect(requestTo(BASE + "/v2/sites"))
        .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("<html>bad gateway</html>"));

    assertThatThrownBy(() -> client.listSites(null))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(502);
              assertThat(e.getCode()).isEqualTo("http_502");
            });
  }

  @Test
  void aReadTimeoutIsReportedAsTimeout() {
    server
        .expect(requestTo(BASE + "/v2/sites/a/actions/fetch_events/read"))
        .andRespond(withException(new SocketTimeoutException("Read timed out")));

    assertThatThrownBy(() -> client.readItems("a", "fetch_events", Map.of(), 48))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getCode()).isEqualTo(Scraper0Exception.TIMEOUT);
              assertThat(e.getHttpStatus()).isZero();
              assertThat(e.getMessage()).contains("90 s");
            });
  }

  @Test
  void aConnectionFailureIsReportedAsUnreachable() {
    server
        .expect(requestTo(BASE + "/v2/sites"))
        .andRespond(withException(new ConnectException("Connection refused")));

    assertThatThrownBy(() -> client.listSites(""))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> assertThat(e.getCode()).isEqualTo(Scraper0Exception.UNREACHABLE));
  }

  @Test
  void nothingIsSentWhenTheBaseUrlIsEmpty() {
    properties.setBaseUrl(" ");

    assertThat(client.isConfigured()).isFalse();
    assertThatThrownBy(() -> client.listSites(null))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> assertThat(e.getCode()).isEqualTo(Scraper0Exception.NOT_CONFIGURED));
    server.verify();
  }

  @Test
  void restTemplatesUseA10sConnectAndTimeoutPlus30sReadTimeout() {
    RestTemplate template = Scraper0Client.newRestTemplate(60);
    SimpleClientHttpRequestFactory factory =
        (SimpleClientHttpRequestFactory) template.getRequestFactory();

    assertThat(ReflectionTestUtils.getField(factory, "connectTimeout")).isEqualTo(10_000);
    assertThat(ReflectionTestUtils.getField(factory, "readTimeout")).isEqualTo(90_000);
  }

  // ---------------------------------------------------------------------------
  // Lookups
  // ---------------------------------------------------------------------------

  @Test
  void lookupReturnsTheOutput() {
    server
        .expect(requestTo(BASE + "/v2/sites/smartoneblr-com/actions/track_issue/read"))
        .andExpect(jsonPath("$.input.tracking_id").value("21025835"))
        .andExpect(jsonPath("$.auth.username").value("u"))
        .andExpect(jsonPath("$.timeout_s").value(120))
        .andRespond(
            withSuccess(
                json("{'data': {'output': {'status': 'CLOSED', 'meta_data': {'ward': 'x'}}}}"),
                MediaType.APPLICATION_JSON));

    JsonNode output =
        client.lookup(
            "smartoneblr-com",
            "track_issue",
            Map.of("tracking_id", "21025835"),
            Map.of("username", "u", "password", "p"),
            120);

    assertThat(output.path("status").asText()).isEqualTo("CLOSED");
    assertThat(templateTimeouts).containsExactly(120);
  }

  @Test
  void aLookupStillRunningIsARunningException() {
    server
        .expect(requestTo(BASE + "/v2/sites/smartoneblr-com/actions/track_issue/read"))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json("{'data': {'job_id': 'job_7'}}")));

    assertThatThrownBy(
            () -> client.lookup("smartoneblr-com", "track_issue", Map.of(), Map.of(), 120))
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(202);
              assertThat(e.getCode()).isEqualTo(Scraper0Exception.RUNNING);
              assertThat(e.getJobId()).isEqualTo("job_7");
            });
  }

  // ---------------------------------------------------------------------------
  // Writes
  // ---------------------------------------------------------------------------

  private void expectWrite(org.springframework.test.web.client.ResponseCreator response) {
    server
        .expect(requestTo(BASE + "/v2/sites/smartoneblr-com/actions/report_issue/write"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "hashtaglocal-issue-7"))
        .andExpect(header("X-API-Key", "s0-key"))
        .andExpect(jsonPath("$.input.category").value("Roads"))
        .andExpect(jsonPath("$.force").value(false))
        .andExpect(jsonPath("$.timeout_s").value(300))
        .andRespond(response);
  }

  private WriteResult write() {
    return client.write(
        "smartoneblr-com",
        "report_issue",
        Map.of("category", "Roads"),
        Map.of("username", "u", "password", "p"),
        "hashtaglocal-issue-7",
        false,
        300);
  }

  @Test
  void writeSucceeded() {
    expectWrite(
        withSuccess(
            json(
                "{'data': {'status': 'succeeded', 'output': {'tracking_id': 21025835},"
                    + " 'idempotency_key': 'hashtaglocal-issue-7', 'job_id': 'job_1', 'replayed':"
                    + " true}}"),
            MediaType.APPLICATION_JSON));

    WriteResult result = write();

    assertThat(result.succeeded()).isTrue();
    assertThat(result.output().path("tracking_id").asLong()).isEqualTo(21025835L);
    assertThat(result.replayed()).isTrue();
    assertThat(result.jobId()).isEqualTo("job_1");
    assertThat(templateTimeouts).containsExactly(300);
  }

  @Test
  void writeStillRunningIs202Running() {
    expectWrite(
        withStatus(HttpStatus.ACCEPTED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                json(
                    "{'data': {'status': 'running', 'idempotency_key': 'hashtaglocal-issue-7',"
                        + " 'job_id': 'job_2'}}")));

    WriteResult result = write();

    assertThat(result.succeeded()).isFalse();
    assertThat(result.status()).isEqualTo("running");
    assertThat(result.jobId()).isEqualTo("job_2");
  }

  @Test
  void writeOutcomeUnknownIs409() {
    expectWrite(
        withStatus(HttpStatus.CONFLICT)
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                json(
                    "{'error': {'code': 'outcome_unknown', 'message': 'may have been submitted',"
                        + " 'job_id': 'job_3'}}")));

    assertThatThrownBy(this::write)
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(409);
              assertThat(e.getCode()).isEqualTo(Scraper0Exception.OUTCOME_UNKNOWN);
              assertThat(e.getJobId()).isEqualTo("job_3");
            });
  }

  @Test
  void writeActionFailedIs422() {
    expectWrite(
        withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
            .contentType(MediaType.APPLICATION_JSON)
            .body(json("{'error': {'code': 'action_failed', 'message': 'Invalid credentials'}}")));

    assertThatThrownBy(this::write)
        .isInstanceOfSatisfying(
            Scraper0Exception.class,
            e -> {
              assertThat(e.getHttpStatus()).isEqualTo(422);
              assertThat(e.getCode()).isEqualTo(Scraper0Exception.ACTION_FAILED);
              assertThat(e.getMessage()).isEqualTo("Invalid credentials");
            });
  }
}
