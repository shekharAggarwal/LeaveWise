package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class IcsApiTest extends ApiIntegrationTest {

  private static PlanJson twoBreaks() {
    return PlanJson.valid()
        .with("leaveDays", "2")
        .holidays("2027-03-04", "Fixture Holiday 1", "2027-08-27", "Fixture Holiday 2");
  }

  private static int count(String text, String needle) {
    return text.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
  }

  @Test
  void downloadHasTheCalendarContentTypeAndAnAttachmentFilename() throws Exception {
    Reply r = postJson("/api/plan/ics", twoBreaks().json());

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat(r.contentType().replace(" ", "").toLowerCase())
        .as("docs/API.md: Content-Type: text/calendar; charset=utf-8")
        .isEqualTo("text/calendar;charset=utf-8");
    assertThat(r.header("Content-Disposition"))
        .as("docs/API.md: attachment with the year in the file name")
        .isEqualTo("attachment; filename=\"leavewise-2027.ics\"");
  }

  @Test
  void oneEventPerBreakOfTheSamePlan() throws Exception {
    Reply plan = plan(twoBreaks());
    Reply ics = postJson("/api/plan/ics", twoBreaks().json());

    List<Object> breaks = plan.at("$.breaks");
    assertThat(breaks).as("the fixture request must produce breaks").isNotEmpty();
    assertThat(count(ics.body(), "BEGIN:VEVENT"))
        .as("PRD: one all-day event per break")
        .isEqualTo(breaks.size());
    assertThat(count(ics.body(), "END:VEVENT")).isEqualTo(breaks.size());
  }

  @Test
  void eventDatesAreAllDayWithAnExclusiveEnd() throws Exception {
    Reply plan = plan(twoBreaks());
    Reply ics = postJson("/api/plan/ics", twoBreaks().json());

    int n = ((List<?>) plan.at("$.breaks")).size();
    for (int i = 0; i < n; i++) {
      LocalDate start = LocalDate.parse(plan.at("$.breaks[" + i + "].start"));
      LocalDate end = LocalDate.parse(plan.at("$.breaks[" + i + "].end"));
      assertThat(ics.body())
          .as("break %d starts on its first day", i)
          .contains("DTSTART;VALUE=DATE:" + start.toString().replace("-", ""))
          .as("break %d ends the day AFTER its last day (RFC 5545 all-day)", i)
          .contains("DTEND;VALUE=DATE:" + end.plusDays(1).toString().replace("-", ""));
    }
  }

  @Test
  void calendarHeaderAndCrlfLineEndings() throws Exception {
    String body = postJson("/api/plan/ics", twoBreaks().json()).body();

    assertThat(body).startsWith("BEGIN:VCALENDAR\r\n").contains("VERSION:2.0\r\n");
    assertThat(body).contains("PRODID:-//LeaveWise//Leave plan//EN").contains("CALSCALE:GREGORIAN");
    assertThat(body.replace("\r\n", "")).as("every line ends with CRLF").doesNotContain("\n");
  }

  @Test
  void noBreaksGivesAValidCalendarWithoutEvents() throws Exception {
    Reply r = postJson("/api/plan/ics", PlanJson.valid().with("leaveDays", "0").json());

    assertThat(r.status()).isEqualTo(200);
    assertThat(r.body()).contains("BEGIN:VCALENDAR").contains("END:VCALENDAR");
    assertThat(r.body())
        .as("docs/API.md: with no breaks the file has no events")
        .doesNotContain("BEGIN:VEVENT");
  }

  @Test
  void invalidRequestGetsTheSameProblemAsThePlanEndpoint() throws Exception {
    postJson("/api/plan/ics", PlanJson.valid().with("leaveDays", "61").json())
        .assertProblem(400, "invalid-input", "Enter a whole number from 0 to 60.");
  }
}
