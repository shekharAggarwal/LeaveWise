package dev.leavewise.api;

import org.junit.jupiter.api.Test;

/** Regression tests for the Phase 4 security review. */
class SecurityReviewApiTest extends ApiIntegrationTest {

  @Test
  void duplicateJsonKeysAreRejectedNotSilentlyOverwritten() throws Exception {
    String body =
        "{\"year\":2026,\"year\":2027,\"leaveDays\":2,\"weeklyOffs\":[\"SATURDAY\",\"SUNDAY\"],"
            + "\"breakStyle\":\"LONG_WEEKENDS\",\"holidays\":[]}";
    send(jsonPost("/api/plan", body))
        .assertProblem(
            400, "unreadable", "The request could not be read. Reload the page and try again.");
  }

  @Test
  void duplicateKeysInsideAHolidayAreRejected() throws Exception {
    plan(PlanJson.valid()
            .with(
                "holidays",
                "[{\"date\":\"2027-03-04\",\"name\":\"Fixture A\",\"name\":\"Fixture B\"}]"))
        .assertProblem(
            400, "unreadable", "The request could not be read. Reload the page and try again.");
  }
}
