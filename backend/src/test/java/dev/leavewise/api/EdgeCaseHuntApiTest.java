package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * edge-case-hunter, Phase 3 whole-app attack. Each test failed against the code it was written for;
 * the expected values come from the PRD, docs/API.md or the javadoc named in each test.
 */
class EdgeCaseHuntApiTest extends ApiIntegrationTest {

  @Test
  void regressionPastedLineWithANoBreakSpaceAfterTheDateLoads() throws Exception {
    // Lists copied from a web page or PDF often separate columns with U+00A0. The line starts
    // with YYYY-MM-DD, so docs/API.md's "Doesn't start with YYYY-MM-DD" reason does not apply,
    // and HolidayPasteParser's javadoc says the date and name are separated by whitespace.
    Reply r =
        postJson(
            "/api/holidays/parse", "{\"year\":2027,\"text\":\"2027-03-04\\u00a0Fixture Day\"}");

    assertThat(r.status()).isEqualTo(200);
    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .as("a line that starts with a date must not be told to start with a date")
        .isEmpty();
    assertThat((List<Map<String, Object>>) r.at("$.holidays"))
        .containsExactly(Map.of("line", 1, "date", "2027-03-04", "name", "Fixture Day"));
  }

  @Test
  void regressionMalformedHolidayDateExampleUsesTheAppClockNotTheSystemClock() throws Exception {
    // AppConfig: "'Today' for every rule comes from this clock (ADR 0006)". With a bad year the
    // example year falls back to LocalDate.now() on the system clock, so it names a year the
    // server itself would refuse ("Pick 2040 or 2041.").
    clock.set("2040-06-01T06:00:00Z");

    Reply r = plan(PlanJson.valid().with("year", "1999").holidays("26-01-2040", "Fixture Day"));

    assertThat(r.status()).isEqualTo(400);
    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .contains(Map.of("field", "year", "message", "Pick 2040 or 2041."))
        .contains(
            Map.of("field", "holidays[0].date", "message", "Write holiday dates like 2040-01-26."));
  }

  @Test
  void regressionLeaveDaysWrittenAsAWholeDecimalIsAWholeNumber() throws Exception {
    // PRD Validation: the error is for leave days "not a whole number, below 0 or above 60".
    // JSON has one number type; 60.0 and 6e1 are the whole number 60.
    for (String sixty : List.of("60.0", "6e1")) {
      Reply r = plan(PlanJson.valid().with("leaveDays", sixty));
      assertThat(r.status()).as("leaveDays=%s: %s", sixty, r.body()).isEqualTo(200);
      assertThat((Integer) r.at("$.summary.leavesAvailable")).isEqualTo(60);
    }
  }

  @Test
  void regressionPasteLimitCountsCharactersNotUtf16Units() throws Exception {
    // docs/API.md: "`text` is at most 8,000 characters", and for names "counted as characters,
    // so an emoji is one". 50 valid lines with 80-emoji names are 4,599 characters (8,599 UTF-16
    // units), yet the server answers "Paste at most 50 lines (8,000 characters)."
    String name = "\uD83C\uDF89".repeat(80); // U+1F389, one character, two UTF-16 units
    String text = String.join("\n", java.util.Collections.nCopies(50, "2027-03-04 " + name));
    assertThat(text.codePointCount(0, text.length())).isLessThan(8000);

    Reply r =
        postJson(
            "/api/holidays/parse",
            "{\"year\":2027,\"text\":\"" + text.replace("\n", "\\n") + "\"}");

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat((List<Object>) r.at("$.holidays")).hasSize(50);
  }
}
