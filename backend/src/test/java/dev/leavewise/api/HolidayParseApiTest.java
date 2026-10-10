package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HolidayParseApiTest extends ApiIntegrationTest {

  private static String body(int year, String text) {
    return "{\"year\":" + year + ",\"text\":\"" + text.replace("\n", "\\n") + "\"}";
  }

  private Reply parse(String text) throws Exception {
    return postJson("/api/holidays/parse", body(2027, text));
  }

  @Test
  void goodLinesLoadAndBadLinesAreReportedWithLineNumberAndReason() throws Exception {
    Reply r =
        parse(
            "2027-03-04 Example Holiday A\n2027-02-30 Bad date\n\n2026-12-25 Wrong year\nnot a line");

    assertThat(r.status()).isEqualTo(200);
    assertThat((List<Map<String, Object>>) r.at("$.holidays"))
        .as("PRD: all other lines still load")
        .containsExactly(Map.of("line", 1, "date", "2027-03-04", "name", "Example Holiday A"));
    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .as("PRD: the line number and the reason; blank line 3 is skipped but counted")
        .containsExactly(
            Map.of("line", 2, "reason", "2027-02-30 is not a real date."),
            Map.of("line", 4, "reason", "2026-12-25 is not in 2027."),
            Map.of(
                "line", 5, "reason", "Start the line with a date like 2027-01-26, then the name."));
  }

  @Test
  void lineWithNoNameIsReported() throws Exception {
    Reply r = parse("2027-03-04");
    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .containsExactly(Map.of("line", 1, "reason", "Add a name after the date."));
    assertThat((List<Object>) r.at("$.holidays")).isEmpty();
  }

  @Test
  void nameOverEightyCharactersIsReportedButEightyIsAccepted() throws Exception {
    Reply r = parse("2027-03-04 " + "a".repeat(81) + "\n2027-03-05 " + "b".repeat(80));

    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .containsExactly(Map.of("line", 1, "reason", "Keep the name to 80 characters or fewer."));
    assertThat((List<String>) r.at("$.holidays[*].date"))
        .as("an 80-character name is the longest allowed")
        .containsExactly("2027-03-05");
  }

  @Test
  void lineFiftyOneAndLaterAreNotLoaded() throws Exception {
    StringBuilder text = new StringBuilder();
    for (int i = 0; i < 51; i++) {
      text.append(LocalDate.of(2027, 1, 1).plusDays(i)).append(" Fixture Holiday ").append(i);
      if (i < 50) {
        text.append('\n');
      }
    }

    Reply r = parse(text.toString());

    assertThat((List<Object>) r.at("$.holidays")).as("lines 1 to 50 still load").hasSize(50);
    assertThat((List<Map<String, Object>>) r.at("$.errors"))
        .containsExactly(
            Map.of("line", 51, "reason", "Only 50 lines can be pasted. This line was not loaded."));
  }

  @Test
  void textOverEightThousandCharactersIsRejectedButExactlyEightThousandIsNot() throws Exception {
    String longText = "x".repeat(8001);
    postJson("/api/holidays/parse", body(2027, longText))
        .assertProblem(400, "invalid-input", "Paste at most 50 lines (8,000 characters).");

    Reply atLimit = postJson("/api/holidays/parse", body(2027, "x".repeat(8000)));
    assertThat(atLimit.status()).as("8,000 characters is allowed").isEqualTo(200);
  }

  @Test
  void yearOutsideTheOptionsIsRejected() throws Exception {
    postJson("/api/holidays/parse", body(2030, "2030-01-26 Fixture Holiday"))
        .assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }

  @Test
  void unreadableBodyIsRejectedAsUnreadable() throws Exception {
    postJson("/api/holidays/parse", "{not json")
        .assertProblem(
            400, "unreadable", "The request could not be read. Reload the page and try again.");
  }

  @Test
  void missingTextIsReportedAsAFieldToFillIn() throws Exception {
    postJson("/api/holidays/parse", "{\"year\":2027}")
        .assertProblem(400, "invalid-input", "Fill in text.");
  }
}
