package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.leavewise.api.ApiIntegrationTest;
import dev.leavewise.api.PlanJson;
import dev.leavewise.api.Reply;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecurityHeadersTest extends ApiIntegrationTest {

  private static final String CSP =
      "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
          + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  private void assertAlwaysOnHeaders(String what, Reply r) {
    assertThat(r.header("Content-Security-Policy")).as("%s: CSP", what).isEqualTo(CSP);
    assertThat(r.header("X-Content-Type-Options")).as("%s: nosniff", what).isEqualTo("nosniff");
    assertThat(r.header("Referrer-Policy"))
        .as("%s: referrer policy", what)
        .isEqualTo("no-referrer");
    assertThat(r.header("Permissions-Policy"))
        .as("%s: permissions policy", what)
        .isEqualTo("camera=(), microphone=(), geolocation=()");
  }

  @Test
  void allSecurityHeadersAreOnApiSuccessAndErrorResponses() throws Exception {
    Map<String, Reply> responses = new LinkedHashMap<>();
    responses.put("GET /api/options", getJson("/api/options"));
    responses.put("POST /api/plan 200", plan(PlanJson.valid()));
    responses.put("POST /api/plan 400", plan(PlanJson.valid().with("leaveDays", "61")));
    responses.put("POST /api/plan malformed", postJson("/api/plan", "{nope"));
    responses.put("GET /api/unknown 404", getJson("/api/unknown"));
    responses.put("POST /api/plan/ics", postJson("/api/plan/ics", PlanJson.valid().json()));

    responses.forEach((what, r) -> assertAlwaysOnHeaders(what, r));
  }

  @Test
  void allSecurityHeadersAreOnTheSinglePageAppResponse() throws Exception {
    assertAlwaysOnHeaders("GET /", getJson("/"));
    assertAlwaysOnHeaders("GET /some/client/route", getJson("/some/client/route"));
  }

  @Test
  void apiResponsesAreNeverCached() throws Exception {
    Map<String, Reply> responses = new LinkedHashMap<>();
    responses.put("GET /api/options", getJson("/api/options"));
    responses.put("GET /api/holidays", getJson("/api/holidays?year=2027"));
    responses.put("POST /api/plan", plan(PlanJson.valid()));
    responses.put("POST /api/plan/ics", postJson("/api/plan/ics", PlanJson.valid().json()));
    responses.put("400 error", plan(PlanJson.valid().with("leaveDays", "61")));

    responses.forEach(
        (what, r) ->
            assertThat(r.header("Cache-Control"))
                .as("%s: docs/API.md says Cache-Control: no-store on /api/**", what)
                .isEqualTo("no-store"));
  }

  @Test
  void strictTransportSecurityIsSentOnlyOverHttps() throws Exception {
    Reply https = send(get("/api/options").secure(true));
    Reply http = send(get("/api/options"));

    assertThat(https.header("Strict-Transport-Security"))
        .as("HTTPS requests get HSTS")
        .isEqualTo("max-age=31536000");
    assertThat(http.header("Strict-Transport-Security"))
        .as("plain HTTP must not get HSTS (browsers ignore it, and it breaks local http)")
        .isNull();
  }

  @Test
  void tooLargeResponsesAlsoCarryTheHeaders() throws Exception {
    Reply r = postJson("/api/plan", " ".repeat(40_000) + PlanJson.valid().json());

    assertThat(r.status()).isEqualTo(413);
    assertAlwaysOnHeaders("413", r);
  }
}
