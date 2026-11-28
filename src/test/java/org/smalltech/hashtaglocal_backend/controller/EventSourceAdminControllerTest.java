package org.smalltech.hashtaglocal_backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.config.SecurityConfig;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.job.EventIngestionRunner;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.model.request.EventSourceInput;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceData;
import org.smalltech.hashtaglocal_backend.model.response.EventSourceTestResult;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.security.AccessTokenAuthFilter;
import org.smalltech.hashtaglocal_backend.service.EventSourceService;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ActionSummary;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.SiteSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** {@code /admin/event-sources}: admin-only, snake_case JSON, and the error mapping. */
@WebMvcTest(EventSourceAdminController.class)
@Import({SecurityConfig.class, AccessTokenAuthFilter.class})
class EventSourceAdminControllerTest {

  private static final String ADMIN = "Bearer admin-token";
  private static final String BODY =
      "{\"name\":\"Karmana\",\"site_id\":\"karmana-in\",\"action\":\"fetch_events\","
          + "\"event_filter\":\"cleanups\",\"category_filter\":null,\"include_online\":true,"
          + "\"freshness_hours\":24,\"enabled\":true}";

  @Autowired private MockMvc mvc;
  @MockitoBean private EventSourceService service;
  @MockitoBean private EventIngestionRunner runner;
  @MockitoBean private UserAuthSessionRepository sessions;

  private final EventSourceData source =
      new EventSourceData(
          7L,
          "Karmana",
          "karmana-in",
          "fetch_events",
          "cleanups",
          null,
          true,
          24,
          true,
          LocalDateTime.of(2026, 11, 26, 10, 0),
          "partial",
          "scraper0 was still refreshing at the timeout",
          12,
          3,
          LocalDateTime.of(2026, 11, 1, 0, 0),
          LocalDateTime.of(2026, 11, 26, 10, 0));

  @BeforeEach
  void setUp() {
    session("admin-token", 1L, UserRole.ADMIN);
    session("user-token", 2L, UserRole.USER);
  }

  private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request) {
    return request
        .header("Authorization", ADMIN)
        .contentType(MediaType.APPLICATION_JSON)
        .content(BODY);
  }

  @Test
  void onlyAdminsGetIn() throws Exception {
    mvc.perform(get("/admin/event-sources")).andExpect(status().isUnauthorized());
    mvc.perform(get("/admin/event-sources").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/admin/event-sources/run").header("Authorization", "Bearer user-token"))
        .andExpect(status().isForbidden());
    verify(service, never()).list();
    verify(runner, never()).runAsync();
  }

  @Test
  void listReturnsSnakeCaseSources() throws Exception {
    when(service.list()).thenReturn(List.of(source));

    mvc.perform(get("/admin/event-sources").header("Authorization", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(7))
        .andExpect(jsonPath("$[0].site_id").value("karmana-in"))
        .andExpect(jsonPath("$[0].event_filter").value("cleanups"))
        .andExpect(jsonPath("$[0].include_online").value(true))
        .andExpect(jsonPath("$[0].freshness_hours").value(24))
        .andExpect(jsonPath("$[0].last_status").value("partial"))
        .andExpect(jsonPath("$[0].last_item_count").value(12))
        .andExpect(jsonPath("$[0].last_imported_count").value(3))
        .andExpect(jsonPath("$[0].last_run_at").exists())
        .andExpect(jsonPath("$[0].created_at").exists());
  }

  @Test
  void createReturns201() throws Exception {
    when(service.create(any())).thenReturn(source);

    mvc.perform(json(post("/admin/event-sources")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(7));
    verify(service)
        .create(
            new EventSourceInput(
                "Karmana", "karmana-in", "fetch_events", "cleanups", null, true, 24, true));
  }

  @Test
  void createDuplicateIs409WithMessage() throws Exception {
    when(service.create(any()))
        .thenThrow(new EventSourceService.DuplicateSourceException("karmana-in", "fetch_events"));

    mvc.perform(json(post("/admin/event-sources")))
        .andExpect(status().isConflict())
        .andExpect(
            jsonPath("$.message")
                .value("An event source for karmana-in / fetch_events already exists"));
  }

  @Test
  void createWithoutSiteIdIs400() throws Exception {
    mvc.perform(
            post("/admin/event-sources")
                .header("Authorization", ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"x\"}"))
        .andExpect(status().isBadRequest());
    verify(service, never()).create(any());
  }

  @Test
  void updateAndDelete() throws Exception {
    when(service.update(eq(7L), any())).thenReturn(source);

    mvc.perform(json(put("/admin/event-sources/7")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Karmana"));
    mvc.perform(delete("/admin/event-sources/7").header("Authorization", ADMIN))
        .andExpect(status().isNoContent());
    verify(service).delete(7L);
  }

  @Test
  void unknownSourceIs404() throws Exception {
    doThrow(new EventSourceService.SourceNotFoundException(9L)).when(service).delete(9L);

    mvc.perform(delete("/admin/event-sources/9").header("Authorization", ADMIN))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Event source not found: 9"));
  }

  @Test
  void testReturnsTheTestResult() throws Exception {
    when(service.test(7L))
        .thenReturn(
            new EventSourceTestResult(
                1,
                false,
                "2026-11-26T00:00:00Z",
                "ok",
                "AI down",
                List.of(
                    new EventSourceTestResult.Item(
                        "itm_1",
                        "Beach cleanup",
                        "2026-12-01T04:30:00.000Z",
                        null,
                        "Juhu",
                        "https://l",
                        "https://img",
                        null,
                        "BEACH_CLEANUP",
                        false))));

    mvc.perform(post("/admin/event-sources/7/test").header("Authorization", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.item_count").value(1))
        .andExpect(jsonPath("$.complete").value(false))
        .andExpect(jsonPath("$.scraped_at").value("2026-11-26T00:00:00Z"))
        .andExpect(jsonPath("$.ai_error").value("AI down"))
        .andExpect(jsonPath("$.items[0].start_time").value("2026-12-01T04:30:00.000Z"))
        .andExpect(jsonPath("$.items[0].is_online").value(false))
        .andExpect(jsonPath("$.items[0].category").value("BEACH_CLEANUP"));
  }

  @Test
  void scraper0ErrorsAre502WithCodeAndMessage() throws Exception {
    when(service.test(7L))
        .thenThrow(new Scraper0Exception(422, "invalid_input", "from: not a date"));

    mvc.perform(post("/admin/event-sources/7/test").header("Authorization", ADMIN))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("invalid_input: from: not a date"));
  }

  @Test
  void notOnboardedAsksTheAdminToOnboardFirst() throws Exception {
    when(service.test(7L))
        .thenThrow(new Scraper0Exception(404, "site_not_onboarded", "karmana-in is unknown"));

    mvc.perform(post("/admin/event-sources/7/test").header("Authorization", ADMIN))
        .andExpect(status().isBadGateway())
        .andExpect(
            jsonPath("$.message").value("site_not_onboarded: Onboard this site in scraper0 first"));
  }

  @Test
  void runStartsIngestionInTheBackground() throws Exception {
    mvc.perform(post("/admin/event-sources/run").header("Authorization", ADMIN))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.started").value(true));
    verify(runner).runAsync();
  }

  @Test
  void scraper0SitesAreProxied() throws Exception {
    when(service.scraper0Sites("karm"))
        .thenReturn(
            List.of(
                new SiteSummary(
                    "karmana-in",
                    "https://karmana.in",
                    "Karmana",
                    List.of(new ActionSummary("fetch_events", "read", "locked")))));

    mvc.perform(
            get("/admin/event-sources/scraper0-sites")
                .param("q", "karm")
                .header("Authorization", ADMIN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].site_id").value("karmana-in"))
        .andExpect(jsonPath("$[0].base_url").value("https://karmana.in"))
        .andExpect(jsonPath("$[0].actions[0].status").value("locked"));
  }

  @Test
  void scraper0SitesUnreachableIs502() throws Exception {
    when(service.scraper0Sites(any()))
        .thenThrow(new Scraper0Exception(0, "unreachable", "connection refused"));

    mvc.perform(get("/admin/event-sources/scraper0-sites").header("Authorization", ADMIN))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.message").value("unreachable: connection refused"));
  }

  private void session(String token, Long userId, UserRole role) {
    UserEntity user = UserEntity.builder().id(userId).username("u" + userId).role(role).build();
    UserAuthSessionEntity session =
        UserAuthSessionEntity.builder()
            .user(user)
            .accessToken(token)
            .accessTokenExpiryTs(Instant.now().getEpochSecond() + 3600)
            .build();
    when(sessions.findByAccessToken(token)).thenReturn(Optional.of(session));
  }
}
