package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OptionsApiTest extends ApiIntegrationTest {

  @Test
  void optionsHaveTheDocumentedShapeAndValues() throws Exception {
    Reply r = getJson("/api/options");

    assertThat(r.status()).as("GET /api/options status").isEqualTo(200);
    assertThat((String) r.at("$.today")).as("today from the server clock").isEqualTo("2026-10-09");
    assertThat((String) r.at("$.timeZone")).isEqualTo("Asia/Kolkata");
    assertThat((List<Integer>) r.at("$.years"))
        .as("current and next year")
        .containsExactly(2026, 2027);
    assertThat((Integer) r.at("$.leaveDays.min")).as("PRD: leave days from 0").isEqualTo(0);
    assertThat((Integer) r.at("$.leaveDays.max")).as("PRD: leave days to 60").isEqualTo(60);
    assertThat((Integer) r.at("$.leaveDays.default")).as("PRD default leave days").isEqualTo(12);
    assertThat((List<String>) r.at("$.defaultWeeklyOffs"))
        .as("PRD default weekly offs")
        .containsExactly("SATURDAY", "SUNDAY");
    assertThat((String) r.at("$.defaultBreakStyle")).isEqualTo("LONG_WEEKENDS");
    assertThat((Integer) r.at("$.maxPastedLines")).as("PRD: up to 50 lines").isEqualTo(50);
    assertThat((List<Map<String, Object>>) r.at("$.breakStyles"))
        .as("three break styles in order, with PRD labels and minimum lengths")
        .containsExactly(
            Map.of("id", "LONG_WEEKENDS", "label", "Long weekends", "minDays", 3),
            Map.of("id", "SHORT_TRIPS", "label", "Short trips", "minDays", 5),
            Map.of("id", "BIG_TRIPS", "label", "Big trips", "minDays", 9));
  }

  @Test
  void defaultYearFromOctober() throws Exception {
    clock.set("2026-10-09T06:00:00Z");
    assertThat((Integer) getJson("/api/options").at("$.defaultYear"))
        .as("PRD Inputs: next year from October onward; today is 2026-10-09")
        .isEqualTo(2027);

    clock.set("2026-09-30T06:00:00Z");
    Reply september = getJson("/api/options");
    assertThat((Integer) september.at("$.defaultYear"))
        .as("PRD Inputs: current year before October; today is 2026-09-30")
        .isEqualTo(2026);
    assertThat((List<Integer>) september.at("$.years")).containsExactly(2026, 2027);
  }

  @Test
  void firstOfOctoberAlreadyDefaultsToNextYear() throws Exception {
    clock.set("2026-10-01T06:00:00Z");
    assertThat((Integer) getJson("/api/options").at("$.defaultYear"))
        .as("docs/API.md: next year 'from 1 October onward'")
        .isEqualTo(2027);
  }

  @Test
  void todayIsTheDateInKolkataNotInUtc() throws Exception {
    // 20:00 UTC on 30 Sep is 01:30 on 1 Oct in Asia/Kolkata.
    clock.set("2026-09-30T20:00:00Z");
    Reply r = getJson("/api/options");
    assertThat((String) r.at("$.today"))
        .as("docs/API.md: today is in Asia/Kolkata")
        .isEqualTo("2026-10-01");
    assertThat((Integer) r.at("$.defaultYear")).isEqualTo(2027);
  }

  @Test
  void yearsFollowTheClock() throws Exception {
    clock.set("2027-10-09T06:00:00Z");
    assertThat((List<Integer>) getJson("/api/options").at("$.years"))
        .as("years are current and next year")
        .containsExactly(2027, 2028);
  }
}
