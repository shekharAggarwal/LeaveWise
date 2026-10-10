package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.api.ApiIntegrationTest;
import dev.leavewise.api.PlanJson;
import dev.leavewise.api.Reply;
import org.junit.jupiter.api.Test;

class RequestSizeLimitTest extends ApiIntegrationTest {

  private static final String TOO_LARGE =
      "The request is too large. Paste at most 50 holiday lines.";

  /** Valid JSON padded with leading spaces to exactly {@code bytes} bytes. */
  private static String padded(String json, int bytes) {
    return " ".repeat(bytes - json.length()) + json;
  }

  @Test
  void bodyOver32KbGets413ProblemJson() throws Exception {
    String parse = "{\"year\":2027,\"text\":\"x\"}";

    postJson("/api/holidays/parse", padded(parse, 32 * 1024 + 1))
        .assertProblem(413, "too-large", TOO_LARGE);
  }

  @Test
  void planEndpointAlsoEnforcesTheLimit() throws Exception {
    postJson("/api/plan", padded(PlanJson.valid().json(), 40_000))
        .assertProblem(413, "too-large", TOO_LARGE);
    postJson("/api/plan/ics", padded(PlanJson.valid().json(), 40_000))
        .assertProblem(413, "too-large", TOO_LARGE);
  }

  @Test
  void bodyOfExactly32KbIsStillAccepted() throws Exception {
    String parse = "{\"year\":2027,\"text\":\"x\"}";

    Reply r = postJson("/api/holidays/parse", padded(parse, 32 * 1024));

    assertThat(r.status()).as("docs/API.md: only bodies OVER 32 KB get 413").isEqualTo(200);
  }
}
