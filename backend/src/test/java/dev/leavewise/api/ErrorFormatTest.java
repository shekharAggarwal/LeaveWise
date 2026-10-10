package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorFormatTest extends ApiIntegrationTest {

  @Test
  void everyClientErrorIsProblemJsonWithTypeTitleStatusDetailAndErrors() throws Exception {
    Map<String, Reply> responses = new LinkedHashMap<>();
    responses.put("bad leaveDays", plan(PlanJson.valid().with("leaveDays", "99")));
    responses.put("bad year", plan(PlanJson.valid().with("year", "1999")));
    responses.put("bad break style", plan(PlanJson.valid().with("breakStyle", "\"X\"")));
    responses.put(
        "holiday outside year", plan(PlanJson.valid().holidays("2020-01-01", "Fixture Holiday 1")));
    responses.put("holidays year", getJson("/api/holidays?year=1999"));
    responses.put("malformed JSON", postJson("/api/plan", "{nope"));
    responses.put("ics bad body", postJson("/api/plan/ics", "{nope"));

    responses.forEach(
        (name, r) -> {
          assertThat(r.status()).as("%s: status", name).isEqualTo(400);
          assertThat(r.contentType())
              .as("%s: content type", name)
              .startsWith("application/problem+json");
          assertThat((String) r.at("$.type"))
              .as("%s: type", name)
              .matches("urn:leavewise:problem:[a-z-]+");
          assertThat((String) r.at("$.title")).as("%s: title", name).isNotBlank();
          assertThat((Integer) r.at("$.status")).as("%s: status in body", name).isEqualTo(400);
          assertThat((String) r.at("$.detail")).as("%s: detail", name).isNotBlank();
          assertThat((Object) r.at("$.errors"))
              .as("%s: errors is a list", name)
              .isInstanceOf(List.class);
        });
  }

  @Test
  void fieldProblemsListTheFieldAndMessage() throws Exception {
    Reply r = plan(PlanJson.valid().with("leaveDays", "99"));

    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .containsExactly(
            Map.of("field", "leaveDays", "message", "Enter a whole number from 0 to 60."));
  }

  @Test
  void malformedJsonIsUnreadable() throws Exception {
    postJson("/api/plan", "{\"year\": 2027,")
        .assertProblem(
            400, "unreadable", "The request could not be read. Reload the page and try again.");
  }

  @Test
  void unexpectedFailureIs500InternalWithoutAStackTrace() throws Exception {
    Reply r = getJson("/api/test-boom");

    r.assertProblem(500, "internal", "Something went wrong on our side. Try again.");
    assertThat(r.body())
        .as("a 500 must not leak exception text, class names or stack frames")
        .doesNotContain(ApiTestConfig.BOOM_DETAIL)
        .doesNotContain("IllegalStateException")
        .doesNotContain("com.example")
        .doesNotContain("stackTrace")
        .doesNotContain("\tat ");
  }
}
