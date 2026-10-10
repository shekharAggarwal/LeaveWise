package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;

/** Regression tests for the Phase 3 API code review (findings 2, 3, 6, 7 and 8). */
class ReviewFindingsApiTest extends ApiIntegrationTest {

  @Test
  void holidayNameWithAControlCharacterIsRejected() throws Exception {
    plan(PlanJson.valid()
            .with("holidays", "[{\"date\":\"2027-03-04\",\"name\":\"Evil\\rATTACH:x\"}]"))
        .assertProblem(
            400, "invalid-input", "Holiday names can't contain line breaks or control characters.");
  }

  @Test
  void missingHolidayDateAsksForIt() throws Exception {
    plan(PlanJson.valid().with("holidays", "[{\"name\":\"Fixture Holiday\"}]"))
        .assertProblem(400, "invalid-input", "Fill in the holiday date.");
  }

  @Test
  void holidayThatIsNotAnObjectAsksForTheDate() throws Exception {
    plan(PlanJson.valid().with("holidays", "[\"x\"]"))
        .assertProblem(400, "invalid-input", "Fill in the holiday date.");
  }

  @Test
  void malformedHolidayDateShowsTheFormat() throws Exception {
    plan(PlanJson.valid()
            .with("holidays", "[{\"date\":\"2027-1-5\",\"name\":\"Fixture Holiday\"}]"))
        .assertProblem(400, "invalid-input", "Write holiday dates like 2027-01-26.");
  }

  @Test
  void blankHolidayNameAsksForAName() throws Exception {
    plan(PlanJson.valid().with("holidays", "[{\"date\":\"2027-03-04\",\"name\":\"  \"}]"))
        .assertProblem(400, "invalid-input", "Give each holiday a name.");
  }

  @Test
  void nameLengthCountsCharactersNotUtf16Units() throws Exception {
    String eighty = "\\uD83C\\uDF89".repeat(80); // 80 emoji, 160 UTF-16 units
    Reply reply =
        plan(
            PlanJson.valid()
                .with("holidays", "[{\"date\":\"2027-03-04\",\"name\":\"" + eighty + "\"}]"));
    assertThat(reply.status()).isEqualTo(200);
  }

  @Test
  void unacceptableAcceptHeaderIs406NotAServerError() throws Exception {
    Reply reply = send(get("/api/options").header("Accept", "text/calendar"));
    assertThat(reply.status()).isEqualTo(406);
    assertThat(reply.contentType()).startsWith("application/problem+json");
  }

  @Test
  void parseYearGivenAsTextIsRejected() throws Exception {
    postJson("/api/holidays/parse", "{\"year\":\"2027\",\"text\":\"2027-03-04 Fixture\"}")
        .assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }

  @Test
  void parseYearWithAFractionIsRejected() throws Exception {
    postJson("/api/holidays/parse", "{\"year\":2027.9,\"text\":\"2027-03-04 Fixture\"}")
        .assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }

  @Test
  void parseYearThatIsNotANumberIsAPickMessageNotUnreadable() throws Exception {
    postJson("/api/holidays/parse", "{\"year\":\"abc\",\"text\":\"2027-03-04 Fixture\"}")
        .assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }

  @Test
  void parseTextThatIsNotTextIsRejected() throws Exception {
    postJson("/api/holidays/parse", "{\"year\":2027,\"text\":42}")
        .assertProblem(400, "invalid-input", "Paste the holiday list as text.");
  }

  @Test
  void healthBodyIsExactlyStatusUp() throws Exception {
    Reply reply = getJson("/actuator/health");
    assertThat(reply.body()).isEqualTo("{\"status\":\"UP\"}");
  }
}
