package org.smalltech.hashtaglocal_backend.service.scraper0;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntFunction;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.SiteSummary;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Calls the scraper0 v2 API (web-scraper spec/api-v2.md): list sites, read a site action (event
 * lists and lookups such as BBMP tracking) and run idempotent writes (BBMP report).
 *
 * <p>Uses its own RestTemplate per {@code timeout_s}: connect timeout 10 s, read timeout {@code
 * timeout_s + 30 s}, so scraper0 always answers (200, or 202 for a write that is still running)
 * before this side gives up. Every failure is a {@link Scraper0Exception} carrying the v2 error
 * code and HTTP status.
 */
@Component
@Slf4j
public class Scraper0Client {

  static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  static final int READ_TIMEOUT_MARGIN_S = 30;

  private final Scraper0Properties properties;
  private final ObjectMapper mapper;
  private final IntFunction<RestTemplate> templateFactory;
  private final Map<Integer, RestTemplate> templates = new ConcurrentHashMap<>();

  @Autowired
  public Scraper0Client(Scraper0Properties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, Scraper0Client::newRestTemplate);
  }

  /** For tests: {@code templateFactory} gets the {@code timeout_s} of the call. */
  public Scraper0Client(
      Scraper0Properties properties,
      ObjectMapper objectMapper,
      IntFunction<RestTemplate> templateFactory) {
    this.properties = properties;
    // scraper0 speaks snake_case regardless of how this app's own JSON is configured.
    this.mapper =
        objectMapper
            .copy()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    this.templateFactory = templateFactory;
  }

  static RestTemplate newRestTemplate(int timeoutS) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT);
    factory.setReadTimeout(Duration.ofSeconds((long) timeoutS + READ_TIMEOUT_MARGIN_S));
    return new RestTemplate(factory);
  }

  public boolean isConfigured() {
    return properties.isConfigured();
  }

  /** Says at startup when the base URL is set without an API key (every call would be refused). */
  @PostConstruct
  void checkConfiguration() {
    if (properties.hasBaseUrl() && !properties.hasApiKey()) {
      log.error(
          "[SCRAPER0] SCRAPER0_BASE_URL is set but SCRAPER0_API_KEY is empty: event ingestion and"
              + " portal issues are off until the key is set");
    } else if (!properties.hasBaseUrl()) {
      log.info("[SCRAPER0] SCRAPER0_BASE_URL is empty: event ingestion and portal issues are off");
    }
  }

  /** {@code GET /v2/sites?q=}. */
  public List<SiteSummary> listSites(String q) {
    UriComponentsBuilder uri = base().path("/v2/sites");
    if (q != null && !q.isBlank()) {
      uri.queryParam("q", q);
    }
    Response response = send(HttpMethod.GET, uri, null, null, properties.getReadTimeoutS());
    JsonNode data = expectData(response);
    return convert(
        data, mapper.getTypeFactory().constructCollectionType(List.class, SiteSummary.class));
  }

  /**
   * Reads a list action ({@code fetch_events}): {@code POST /v2/sites/{site}/actions/{action}/read}
   * with {@code options}, {@code freshness_hours} and {@code timeout_s} = {@code
   * scraper0.read-timeout-s}.
   */
  public ReadResult readItems(
      String siteId, String action, Map<String, Object> options, Integer freshnessHours) {
    int timeoutS = properties.getReadTimeoutS();
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("options", options == null ? Map.of() : options);
    if (freshnessHours != null) {
      body.put("freshness_hours", freshnessHours);
    }
    body.put("timeout_s", timeoutS);
    Response response =
        send(HttpMethod.POST, actionUri(siteId, action, "read"), body, null, timeoutS);
    return convert(expectData(response), mapper.constructType(ReadResult.class));
  }

  /**
   * Reads a lookup action ({@code track_issue}) and returns its {@code output}. A 202 (the lookup
   * timed out and is still running) is a {@link Scraper0Exception} with code {@code running}.
   */
  public JsonNode lookup(
      String siteId,
      String action,
      Map<String, Object> input,
      Map<String, Object> auth,
      int timeoutS) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("input", input);
    if (auth != null) {
      body.put("auth", auth);
    }
    body.put("timeout_s", timeoutS);
    Response response =
        send(HttpMethod.POST, actionUri(siteId, action, "read"), body, null, timeoutS);
    if (response.status() == 202) {
      String jobId = response.body().path("data").path("job_id").asText(null);
      throw new Scraper0Exception(
          202, Scraper0Exception.RUNNING, "the lookup is still running (job " + jobId + ")", jobId);
    }
    JsonNode output = expectData(response).get("output");
    if (output == null || output.isNull()) {
      throw invalid(response.status(), "missing data.output");
    }
    return output;
  }

  /**
   * Runs an idempotent write ({@code report_issue}) with the given {@code Idempotency-Key}.
   *
   * <p>200 returns {@code succeeded} with the output; 202 returns {@code running} (retrying with
   * the same key returns the same job). 409 {@code outcome_unknown} and every other error are a
   * {@link Scraper0Exception}.
   */
  public WriteResult write(
      String siteId,
      String action,
      Map<String, Object> input,
      Map<String, Object> auth,
      String idempotencyKey,
      boolean force,
      int timeoutS) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("input", input);
    if (auth != null) {
      body.put("auth", auth);
    }
    body.put("force", force);
    body.put("timeout_s", timeoutS);
    Response response =
        send(HttpMethod.POST, actionUri(siteId, action, "write"), body, idempotencyKey, timeoutS);
    JsonNode data = expectData(response);
    String status = data.path("status").asText(response.status() == 202 ? "running" : null);
    return new WriteResult(
        status,
        data.get("output"),
        data.path("idempotency_key").asText(idempotencyKey),
        data.path("job_id").asText(null),
        data.path("replayed").asBoolean(false));
  }

  // ---------------------------------------------------------------------------

  private record Response(int status, JsonNode body) {}

  private UriComponentsBuilder base() {
    if (!properties.hasBaseUrl()) {
      throw new Scraper0Exception(
          0, Scraper0Exception.NOT_CONFIGURED, "scraper0.base-url (SCRAPER0_BASE_URL) is not set");
    }
    if (!properties.hasApiKey()) {
      throw new Scraper0Exception(
          0, Scraper0Exception.NOT_CONFIGURED, "scraper0.api-key (SCRAPER0_API_KEY) is not set");
    }
    String baseUrl = properties.getBaseUrl().trim().replaceAll("/+$", "");
    return UriComponentsBuilder.fromUriString(baseUrl);
  }

  private UriComponentsBuilder actionUri(String siteId, String action, String verb) {
    return base().pathSegment("v2", "sites", siteId, "actions", action, verb);
  }

  private Response send(
      HttpMethod method,
      UriComponentsBuilder uriBuilder,
      Object body,
      String idempotencyKey,
      int timeoutS) {
    URI uri = uriBuilder.encode().build().toUri();
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    headers.set("X-API-Key", properties.getApiKey());
    if (idempotencyKey != null) {
      headers.set("Idempotency-Key", idempotencyKey);
    }
    String json = null;
    if (body != null) {
      headers.setContentType(MediaType.APPLICATION_JSON);
      try {
        json = mapper.writeValueAsString(body);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot serialise the scraper0 request", e);
      }
    }

    ResponseEntity<String> entity;
    try {
      entity =
          templates
              .computeIfAbsent(timeoutS, templateFactory::apply)
              .exchange(uri, method, new HttpEntity<>(json, headers), String.class);
    } catch (RestClientResponseException e) {
      throw toException(e.getStatusCode().value(), e.getResponseBodyAsString());
    } catch (ResourceAccessException e) {
      boolean timedOut =
          e.getCause() instanceof SocketTimeoutException
              || e.getCause() instanceof HttpTimeoutException;
      log.warn("[SCRAPER0] {} {} failed: {}", method, uri.getPath(), e.getMessage());
      throw new Scraper0Exception(
          0,
          timedOut ? Scraper0Exception.TIMEOUT : Scraper0Exception.UNREACHABLE,
          timedOut
              ? "scraper0 did not answer within " + (timeoutS + READ_TIMEOUT_MARGIN_S) + " s"
              : "scraper0 is unreachable: " + e.getMessage());
    } catch (RestClientException e) {
      throw new Scraper0Exception(0, Scraper0Exception.UNREACHABLE, e.getMessage());
    }

    int status = entity.getStatusCode().value();
    if (status >= 300) {
      throw toException(status, entity.getBody());
    }
    return new Response(status, parse(status, entity.getBody()));
  }

  private Scraper0Exception toException(int status, String rawBody) {
    String code = "http_" + status;
    String message = "scraper0 answered HTTP " + status;
    String jobId = null;
    try {
      JsonNode error =
          rawBody == null || rawBody.isBlank() ? null : mapper.readTree(rawBody).get("error");
      if (error != null && error.isObject()) {
        code = error.path("code").asText(code);
        message = error.path("message").asText(message);
        jobId = error.path("job_id").asText(null);
      }
    } catch (IOException ignored) {
      // Not JSON (e.g. a proxy error page): keep the HTTP status as the code.
    }
    log.warn("[SCRAPER0] HTTP {} {}: {}", status, code, message);
    return new Scraper0Exception(status, code, message, jobId);
  }

  private JsonNode parse(int status, String rawBody) {
    try {
      return rawBody == null || rawBody.isBlank()
          ? mapper.createObjectNode()
          : mapper.readTree(rawBody);
    } catch (IOException e) {
      throw invalid(status, "the body is not JSON");
    }
  }

  private JsonNode expectData(Response response) {
    JsonNode data = response.body().get("data");
    if (data == null || data.isNull()) {
      throw invalid(response.status(), "missing data");
    }
    return data;
  }

  private <T> T convert(JsonNode node, com.fasterxml.jackson.databind.JavaType type) {
    try {
      return mapper.convertValue(node, type);
    } catch (IllegalArgumentException e) {
      throw invalid(200, e.getMessage());
    }
  }

  private static Scraper0Exception invalid(int status, String detail) {
    return new Scraper0Exception(
        status, Scraper0Exception.INVALID_RESPONSE, "unexpected scraper0 response: " + detail);
  }
}
