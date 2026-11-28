package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.smalltech.hashtaglocal_backend.dto.TrackIssueScrapeResponseDTO;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Properties;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/** BBMP tracking through scraper0 v2 {@code track_issue/read}. */
class PortalIssueScrapeClientTest {

  private static final String URL =
      "https://scraper0.test/v2/sites/smartoneblr-com/actions/track_issue/read";

  private MockRestServiceServer server;
  private PortalIssueScrapeClient client;

  @BeforeEach
  void setUp() {
    Scraper0Properties properties = new Scraper0Properties();
    properties.setBaseUrl("https://scraper0.test");
    properties.setApiKey("s0-key");
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    ObjectMapper mapper = new ObjectMapper();
    client =
        new PortalIssueScrapeClient(
            new Scraper0Client(properties, mapper, t -> restTemplate), mapper, "bbmp", "secret");
  }

  @Test
  void sendsTheTrackingIdAndAuthAndMapsTheOutput() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.input.tracking_id").value("21025835"))
        .andExpect(jsonPath("$.auth.username").value("bbmp"))
        .andExpect(jsonPath("$.auth.password").value("secret"))
        .andRespond(
            withSuccess(
                "{\"data\":{\"site_id\":\"smartoneblr-com\",\"action\":\"track_issue\",\"version\":1,"
                    + "\"output\":{\"status\":\"CLOSED\",\"meta_data\":{\"ward\":\"Shivajinagar\"}}}}",
                MediaType.APPLICATION_JSON));

    TrackIssueScrapeResponseDTO response = client.trackIssue("SMARTONEBLR", "21025835");

    server.verify();
    assertThat(response.getData().getStatus()).isEqualTo("CLOSED");
    assertThat(response.getData().getMetaData()).containsEntry("ward", "Shivajinagar");
  }

  @Test
  void aLookupStillRunningFailsThisCycle() {
    server
        .expect(requestTo(URL))
        .andRespond(
            withStatus(HttpStatus.ACCEPTED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"data\":{\"job_id\":\"j1\"}}"));

    assertThatThrownBy(() -> client.trackIssue("SMARTONEBLR", "21025835"))
        .isInstanceOf(Scraper0Exception.class);
  }
}
