package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Floater (optional) holidays in a bundled list (ADR 0007 amendment, 2026-10-10): listed with
 * {@code optional: true} so the page shows them unticked, and never counted as days off unless the
 * user sends them. Fixtures: "Fixture Floater 2027" on Thu 14 Oct 2027 and "Fixture Floater 2026"
 * on Thu 31 Dec 2026.
 */
class FloaterHolidaysApiTest extends ApiIntegrationTest {

  @Test
  void aFloaterIsListedAsOptionalAndFixedHolidaysAreNot() throws Exception {
    Reply r = getJson("/api/holidays?year=2027");

    assertThat(r.status()).isEqualTo(200);
    assertThat((List<Boolean>) r.at("$.holidays[?(@.date=='2027-10-14')].optional"))
        .containsExactly(true);
    assertThat((List<Boolean>) r.at("$.holidays[?(@.date=='2027-03-04')].optional"))
        .containsExactly(false);
    assertThat((String) r.at("$.kind")).isEqualTo("government");
  }

  @Test
  void aFloaterInTheAdjacentYearDoesNotExtendABreakAcross1January() throws Exception {
    // With 31 Dec 2026 counted as a day off, a leave on Fri 1 Jan 2027 would make a 4-day break
    // from Thu 31 Dec. As a floater it isn't a day off, so the break is Fri 1 to Sun 3 Jan.
    Reply r = plan(PlanJson.valid().with("leaveDays", "1"));

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat((String) r.at("$.breaks[0].start")).isEqualTo("2027-01-01");
    assertThat((Integer) r.at("$.breaks[0].days")).isEqualTo(3);
  }
}
