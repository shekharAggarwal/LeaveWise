package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.api.ApiIntegrationTest;
import dev.leavewise.api.Reply;
import org.junit.jupiter.api.Test;

class SpaForwardingTest extends ApiIntegrationTest {

  @Test
  void unknownClientRouteForwardsToIndexHtml() throws Exception {
    Reply r = getJson("/some/client/route");

    assertThat(r.response().getForwardedUrl())
        .as("a deep link (e.g. a share link) must load the single page app")
        .isEqualTo("/index.html");
  }

  @Test
  void unknownApiPathIs404ProblemJsonNotTheHtmlPage() throws Exception {
    Reply r = getJson("/api/unknown");

    assertThat(r.status()).as("unknown API path").isEqualTo(404);
    assertThat(r.response().getForwardedUrl()).as("must not forward to index.html").isNull();
    assertThat(r.contentType()).startsWith("application/problem+json");
    assertThat((String) r.at("$.type")).startsWith("urn:leavewise:problem:");
    assertThat((Integer) r.at("$.status")).isEqualTo(404);
  }
}
