package org.smalltech.hashtaglocal_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.smalltech.hashtaglocal_backend.entity.EventSourceEntity;
import org.smalltech.hashtaglocal_backend.model.EventTypeModel;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Client;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Exception;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.Item;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Models.ReadResult;
import org.smalltech.hashtaglocal_backend.service.scraper0.Scraper0Properties;

/**
 * Opt-in contract check against a running scraper0 (no database needed):
 *
 * <pre>SCRAPER0_LIVE_URL=http://127.0.0.1:5001 SCRAPER0_LIVE_API_KEY=s0_… \   (the key is required)
 *   ./gradlew test --tests '*Scraper0LiveContractTest'</pre>
 *
 * Needs the seeded {@code teameverest-ngo} action and a locked {@code karmana-in fetch_events}.
 */
@EnabledIfEnvironmentVariable(named = "SCRAPER0_LIVE_URL", matches = ".+")
class Scraper0LiveContractTest {

  private final Scraper0Client client = client();

  private static Scraper0Client client() {
    Scraper0Properties p = new Scraper0Properties();
    p.setBaseUrl(System.getenv("SCRAPER0_LIVE_URL"));
    String key = System.getenv("SCRAPER0_LIVE_API_KEY");
    p.setApiKey(key == null ? "" : key);
    p.setReadTimeoutS(90);
    return new Scraper0Client(p, new ObjectMapper());
  }

  @Test
  void listsOnboardedSites() {
    assertThat(client.listSites(null))
        .anySatisfy(s -> assertThat(s.siteId()).isEqualTo("teameverest-ngo"))
        .allSatisfy(s -> assertThat(s.baseUrl()).startsWith("https://"));
    assertThat(client.listSites("karmana")).extracting("siteId").containsExactly("karmana-in");
  }

  @Test
  void readsEventsWithHashtaglocalOptions() {
    Set<String> types =
        Arrays.stream(EventTypeModel.values()).map(Enum::name).collect(Collectors.toSet());
    for (String site : new String[] {"teameverest-ngo", "karmana-in"}) {
      EventSourceEntity source =
          EventSourceEntity.builder().name(site).siteId(site).action("fetch_events").build();
      ReadResult r =
          client.readItems(
              site, "fetch_events", EventSourceService.options(source), source.getFreshnessHours());
      System.out.printf(
          "%s: %d items, complete=%s health=%s ai_error=%s%n",
          site, r.itemsOrEmpty().size(), r.complete(), r.health(), r.aiError());
      assertThat(r.siteId()).isEqualTo(site);
      assertThat(r.health()).isEqualTo("ok");
      for (Item it : r.itemsOrEmpty()) {
        System.out.printf(
            "  %s %s | %s | %s | online=%s%n",
            it.id(),
            it.data().startTime(),
            it.data().name(),
            it.data().category(),
            it.data().isOnline());
        assertThat(it.id()).matches("itm_[0-9a-f]{24}");
        assertThat(it.data().name()).isNotBlank();
        assertThat(it.data().startTime()).isNotBlank();
        assertThat(it.data().isOnline()).isNotEqualTo(Boolean.TRUE); // include_online=false
        // The site's own link, never a scraper0 copy; a signed link says when it expires.
        if (it.data().image() != null) {
          assertThat(it.data().image()).startsWith("http").doesNotContain("/v2/media/");
        }
        if (it.data().imageExpiresAt() != null) {
          assertThat(java.time.Instant.parse(it.data().imageExpiresAt()))
              .isAfter(java.time.Instant.now());
        }
        if (it.data().category() != null) {
          assertThat(types).contains(it.data().category());
        }
      }
    }
  }

  @Test
  void unknownSiteIsNotOnboarded() {
    assertThatThrownBy(() -> client.readItems("nope-example-com", "fetch_events", null, null))
        .isInstanceOfSatisfying(
            Scraper0Exception.class, e -> assertThat(e.isNotOnboarded()).isTrue());
  }
}
