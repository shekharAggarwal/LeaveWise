package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HolidaysApiTest extends ApiIntegrationTest {

  @Test
  void bundledListIsReturnedWithItsSource() throws Exception {
    Reply r = getJson("/api/holidays?year=2027");

    assertThat(r.status()).isEqualTo(200);
    assertThat((Integer) r.at("$.year")).isEqualTo(2027);
    assertThat((Boolean) r.at("$.available")).as("a fixture file exists for 2027").isTrue();
    assertThat((String) r.at("$.list")).isEqualTo("Fixture list 2027");
    assertThat((String) r.at("$.source.publisher")).isEqualTo("Fixture publisher");
    assertThat((String) r.at("$.source.title")).isEqualTo("Fixture memorandum 2027");
    assertThat((String) r.at("$.source.url")).isEqualTo("https://fixture.gov.in/holidays-2027.pdf");
    assertThat((String) r.at("$.source.retrievedOn")).isEqualTo("2026-10-20");
    assertThat((List<String>) r.at("$.holidays[*].date"))
        .as("one entry per date, in date order (docs/API.md example)")
        .containsExactly("2027-03-04", "2027-06-10", "2027-08-27", "2027-10-14");
  }

  @Test
  void twoHolidaysOnTheSameDateAreReturnedOnceWithBothNames() throws Exception {
    Reply r = getJson("/api/holidays?year=2027");

    List<Map<String, Object>> onThatDay = r.at("$.holidays[?(@.date=='2027-08-27')]");
    assertThat(onThatDay).as("PRD: 'Kept once, showing both names'").hasSize(1);
    assertThat((List<String>) onThatDay.get(0).get("names"))
        .containsExactlyInAnyOrder("Fixture Holiday 2", "Fixture Holiday 3");
  }

  @Test
  void reminderPresent() throws Exception {
    Reply r = getJson("/api/holidays?year=2026");

    assertThat((String) r.at("$.reminder"))
        .as("PRD: the app reminds users to check their company's official list")
        .containsIgnoringCase("company")
        .containsIgnoringCase("official");
  }

  @Test
  void yearWithoutABundledFileIsAvailableFalseWithNoSource() throws Exception {
    clock.set("2027-10-09T06:00:00Z"); // years 2027 and 2028; the fixtures cover 2026 and 2027 only

    Reply r = getJson("/api/holidays?year=2028");

    assertThat(r.status()).as("docs/API.md: 200 so the user can still plan").isEqualTo(200);
    assertThat((Boolean) r.at("$.available")).isFalse();
    assertThat((Object) r.at("$.source")).as("source is null when there is no file").isNull();
    assertThat((List<Object>) r.at("$.holidays")).isEmpty();
  }

  @Test
  void yearOutsideTheOptionsIsRejectedWithPickMessage() throws Exception {
    getJson("/api/holidays?year=2029").assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
    getJson("/api/holidays?year=2025").assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }

  @Test
  void missingYearIsRejectedWithPickMessage() throws Exception {
    getJson("/api/holidays").assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
  }
}
