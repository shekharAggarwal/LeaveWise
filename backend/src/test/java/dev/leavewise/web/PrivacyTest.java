package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.api.ApiIntegrationTest;
import dev.leavewise.api.PlanJson;
import dev.leavewise.api.Reply;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * PRD Privacy: no tracking cookies, and inputs are not logged (even at the verbose DEBUG level).
 */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(
    properties = {"logging.level.root=DEBUG", "spring.mvc.log-request-details=true"})
class PrivacyTest extends ApiIntegrationTest {

  private static final String MARKER = "zq7-private-marker-4431";

  @Test
  void noResponseSetsACookie() throws Exception {
    Map<String, Reply> responses = new LinkedHashMap<>();
    responses.put("options", getJson("/api/options"));
    responses.put("holidays", getJson("/api/holidays?year=2027"));
    responses.put("plan", plan(PlanJson.valid()));
    responses.put("plan 400", plan(PlanJson.valid().with("leaveDays", "-5")));
    responses.put("ics", postJson("/api/plan/ics", PlanJson.valid().json()));
    responses.put("page", getJson("/"));
    responses.put("health", getJson("/actuator/health"));

    responses.forEach(
        (what, r) ->
            assertThat(r.response().getHeaders("Set-Cookie"))
                .as("PRD: no tracking cookies; %s set one", what)
                .isEmpty());
  }

  @Test
  void requestInputsNeverAppearInTheLog(CapturedOutput output) throws Exception {
    plan(PlanJson.valid().holidays("2027-03-04", MARKER));
    plan(PlanJson.valid().holidays("2027-03-04", MARKER + "x".repeat(80)));
    plan(PlanJson.valid().holidays("2026-01-01", MARKER));
    postJson("/api/plan", "{\"year\":2027,\"holidays\":[{\"name\":\"" + MARKER + "\",");
    postJson("/api/plan/ics", PlanJson.valid().holidays("2027-03-04", MARKER).json());
    postJson(
        "/api/holidays/parse",
        "{\"year\":2027,\"text\":\"2027-03-04 " + MARKER + "\\nnot a line " + MARKER + "\"}");
    postJson(
        "/api/holidays/parse", "{\"year\":2027,\"text\":\"" + (MARKER + " ").repeat(1000) + "\"}");

    assertThat(output.getAll())
        .as("PRD: inputs are not logged. The marker came from a request body")
        .doesNotContain(MARKER);
  }
}
