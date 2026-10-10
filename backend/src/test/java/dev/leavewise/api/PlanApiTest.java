package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlanApiTest extends ApiIntegrationTest {

  private static final String LEAVE_DAYS_MESSAGE = "Enter a whole number from 0 to 60.";
  private static final String THURSDAY = "2027-03-04";

  /** One holiday on Thursday 4 Mar 2027 and one leave: the PRD's 4-day example. */
  private static PlanJson thursdayHoliday(int leaves) {
    return PlanJson.valid()
        .with("leaveDays", String.valueOf(leaves))
        .holidays(THURSDAY, "Fixture Holiday 1");
  }

  // ---- response shape -------------------------------------------------------------------------

  @Test
  void summary() throws Exception {
    Reply r = plan(thursdayHoliday(1));

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat((Integer) r.at("$.year")).isEqualTo(2027);
    assertThat((String) r.at("$.planningFrom"))
        .as("a future year is planned from 1 Jan")
        .isEqualTo("2027-01-01");
    assertThat((String) r.at("$.planningTo")).isEqualTo("2027-12-31");
    assertThat((String) r.at("$.breakStyle")).isEqualTo("LONG_WEEKENDS");
    assertThat((Integer) r.at("$.minBreakDays")).as("Long weekends = 3+ days").isEqualTo(3);
    assertThat((Integer) r.at("$.summary.leavesAvailable")).isEqualTo(1);
    assertThat((Integer) r.at("$.summary.leavesUsed")).isEqualTo(1);
    assertThat((Integer) r.at("$.summary.leavesUnused")).isEqualTo(0);
    assertThat((Integer) r.at("$.summary.totalDaysOff"))
        .as("PRD example: a Thursday holiday plus 1 leave on Friday is a 4-day break")
        .isEqualTo(4);
    assertThat((Integer) r.at("$.summary.breakCount")).isEqualTo(1);
    assertThat((List<Object>) r.at("$.notes")).as("no note when nothing unusual happens").isEmpty();
  }

  @Test
  void breakFields() throws Exception {
    Reply r = plan(thursdayHoliday(1));

    assertThat((String) r.at("$.breaks[0].start")).isEqualTo("2027-03-04");
    assertThat((String) r.at("$.breaks[0].end")).isEqualTo("2027-03-07");
    assertThat((Integer) r.at("$.breaks[0].days")).isEqualTo(4);
    assertThat((List<String>) r.at("$.breaks[0].leaveDates")).containsExactly("2027-03-05");
    assertThat((List<Map<String, Object>>) r.at("$.breaks[0].holidays"))
        .containsExactly(Map.of("date", THURSDAY, "names", List.of("Fixture Holiday 1")));
    assertThat(((Number) r.at("$.breaks[0].daysOffPerLeave")).doubleValue())
        .as("4 days off for 1 leave")
        .isEqualTo(4.0);
  }

  @Test
  void daysOffPerLeaveIsRoundedToOneDecimal() throws Exception {
    // 3 leaves can build at most one 5-day break (a 4-day one needs 2 leaves, too short for 5+).
    Reply r = plan(PlanJson.valid().with("leaveDays", "3").with("breakStyle", "\"SHORT_TRIPS\""));

    assertThat((Integer) r.at("$.summary.breakCount")).as(r.body()).isEqualTo(1);
    assertThat((Integer) r.at("$.breaks[0].days")).isEqualTo(5);
    assertThat((List<String>) r.at("$.breaks[0].leaveDates")).hasSize(3);
    assertThat(((Number) r.at("$.breaks[0].daysOffPerLeave")).doubleValue())
        .as("docs/API.md: days / leaveDates.length rounded to one decimal; 5 / 3 = 1.7")
        .isEqualTo(1.7);
  }

  @Test
  void freeLongWeekendsAreListedButNotCounted() throws Exception {
    Reply r =
        plan(PlanJson.valid().with("leaveDays", "0").holidays("2027-08-27", "Fixture Holiday 2"));

    assertThat((List<Map<String, Object>>) r.at("$.freeLongWeekends"))
        .as("PRD: 3 or more days off in a row that need no leave")
        .containsExactly(
            Map.of(
                "start",
                "2027-08-27",
                "end",
                "2027-08-29",
                "days",
                3,
                "holidays",
                List.of(Map.of("date", "2027-08-27", "names", List.of("Fixture Holiday 2")))));
    assertThat((Integer) r.at("$.summary.totalDaysOff"))
        .as("PRD: free long weekends 'don't count toward the plan'")
        .isEqualTo(0);
  }

  @Test
  void daysHasOneEntryPerDateOfTheYear() throws Exception {
    Reply r = plan(thursdayHoliday(1));

    assertThat((List<Object>) r.at("$.days")).as("2027 has 365 days").hasSize(365);
    assertThat((String) r.at("$.days[0].date")).isEqualTo("2027-01-01");
    assertThat((String) r.at("$.days[364].date")).isEqualTo("2027-12-31");
  }

  @Test
  void daysCarryKindPlannedHolidayNamesAndBreakIndex() throws Exception {
    Reply r = plan(thursdayHoliday(1));

    Map<String, Object> holiday = day(r, THURSDAY);
    assertThat(holiday).containsEntry("kind", "HOLIDAY").containsEntry("planned", true);
    assertThat(holiday.get("holidayNames")).isEqualTo(List.of("Fixture Holiday 1"));
    assertThat(holiday.get("breakIndex")).as("points into breaks[]").isEqualTo(0);

    Map<String, Object> leave = day(r, "2027-03-05");
    assertThat(leave)
        .as("the Friday leave")
        .containsEntry("kind", "LEAVE")
        .containsEntry("breakIndex", 0);

    assertThat(day(r, "2027-03-06"))
        .containsEntry("kind", "WEEKLY_OFF")
        .containsEntry("breakIndex", 0);

    Map<String, Object> workday = day(r, "2027-03-10");
    assertThat(workday).containsEntry("kind", "WORKING");
    assertThat(workday.get("breakIndex")).isNull();
    assertThat(workday.get("freeLongWeekendIndex")).isNull();
  }

  @Test
  void aHolidayOnAWeeklyOffIsKindHoliday() throws Exception {
    Reply r =
        plan(PlanJson.valid().with("leaveDays", "0").holidays("2027-03-06", "Fixture Holiday 1"));

    assertThat(day(r, "2027-03-06"))
        .as("docs/API.md: a holiday on a weekly off is HOLIDAY")
        .containsEntry("kind", "HOLIDAY");
  }

  @Test
  void freeLongWeekendDaysPointIntoTheFreeLongWeekendList() throws Exception {
    Reply r =
        plan(PlanJson.valid().with("leaveDays", "0").holidays("2027-08-27", "Fixture Holiday 2"));

    assertThat(day(r, "2027-08-28")).containsEntry("freeLongWeekendIndex", 0);
  }

  @Test
  void leapYearHas366Days() throws Exception {
    clock.set("2027-10-09T06:00:00Z"); // years 2027 and 2028
    Reply r = plan(PlanJson.valid().with("year", "2028"));

    assertThat((List<Object>) r.at("$.days")).as("PRD edge case: leap year 2028").hasSize(366);
    assertThat((String) r.at("$.days[59].date")).as("29 Feb exists").isEqualTo("2028-02-29");
  }

  @Test
  void currentYearIsPlannedFromTomorrow() throws Exception {
    Reply r = plan(PlanJson.valid().with("year", "2026"));

    assertThat((String) r.at("$.planningFrom"))
        .as("PRD: only days after today (9 Oct) are planned")
        .isEqualTo("2026-10-10");
    assertThat((String) r.at("$.planningTo")).isEqualTo("2026-12-31");
    assertThat((Boolean) day(r, "2026-10-09").get("planned")).as("today is not planned").isFalse();
    assertThat((Boolean) day(r, "2026-01-01").get("planned")).isFalse();
    assertThat((Boolean) day(r, "2026-10-10").get("planned")).isTrue();
    assertThat((List<Object>) r.at("$.days")).hasSize(365);
  }

  // ---- notes ----------------------------------------------------------------------------------

  @Test
  void zeroLeavesNote() throws Exception {
    Reply r =
        plan(PlanJson.valid().with("leaveDays", "0").holidays("2027-08-27", "Fixture Holiday 2"));

    assertThat((List<Map<String, Object>>) r.at("$.notes"))
        .as("PRD: 0 leave days gives 'a note that no leave is used'")
        .containsExactly(
            Map.of(
                "code",
                "NO_LEAVE_USED",
                "message",
                "No leave used. These are the long weekends you already get."));
    assertThat((List<Object>) r.at("$.breaks")).isEmpty();
  }

  @Test
  void leftOverNote() throws Exception {
    // Only Sundays are working days (52 in 2027): 60 leaves cover them all, 8 are left over.
    Reply r =
        plan(
            PlanJson.valid()
                .with("leaveDays", "60")
                .with(
                    "weeklyOffs",
                    "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\"]"));

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat((Integer) r.at("$.summary.leavesUsed"))
        .as("PRD: a plan using every working day")
        .isEqualTo(52);
    assertThat((Integer) r.at("$.summary.leavesUnused")).isEqualTo(8);
    assertThat((List<Map<String, Object>>) r.at("$.notes"))
        .as("PRD: a note on how many leaves were left over")
        .containsExactly(
            Map.of(
                "code",
                "LEAVES_LEFT_OVER",
                "message",
                "8 leaves left over: every working day is already a leave day."));
  }

  @Test
  void noBreakPossibleNoteSuggestsAShorterStyleWhenTheStyleIsNotLongWeekends() throws Exception {
    // 2 leaves give at most a 4-day break with Saturday and Sunday off, never 9.
    Reply r = plan(PlanJson.valid().with("leaveDays", "2").with("breakStyle", "\"BIG_TRIPS\""));

    assertThat((List<Map<String, Object>>) r.at("$.notes"))
        .as("PRD: the reason, plus a suggestion to try a shorter break style")
        .containsExactly(
            Map.of(
                "code", "NO_BREAK_POSSIBLE",
                "message",
                    "No break of 9+ days fits 2 leaves with these holidays and weekly offs. Try a shorter break style."));
    assertThat((Integer) r.at("$.summary.breakCount")).isEqualTo(0);
  }

  @Test
  void noBreakPossibleNoteHasNoSuggestionForLongWeekends() throws Exception {
    // No weekly offs and no holidays: 2 leaves make a 2-day run, never 3.
    Reply r = plan(PlanJson.valid().with("leaveDays", "2").with("weeklyOffs", "[]"));

    assertThat((List<Map<String, Object>>) r.at("$.notes"))
        .containsExactly(
            Map.of(
                "code", "NO_BREAK_POSSIBLE",
                "message",
                    "No break of 3+ days fits 2 leaves with these holidays and weekly offs."));
  }

  @Test
  void noDaysLeftNote() throws Exception {
    clock.set("2026-12-31T06:00:00Z");
    Reply r = plan(PlanJson.valid().with("year", "2026").with("leaveDays", "5"));

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    assertThat((List<Map<String, Object>>) r.at("$.notes"))
        .containsExactly(
            Map.of("code", "NO_DAYS_LEFT", "message", "No days left to plan in 2026. Pick 2027."));
    assertThat((Object) r.at("$.planningFrom")).as("null when no day is left").isNull();
    assertThat((Object) r.at("$.planningTo")).isNull();
    assertThat((List<Object>) r.at("$.breaks")).isEmpty();
    assertThat((List<Object>) r.at("$.freeLongWeekends")).isEmpty();
    assertThat((Integer) r.at("$.summary.leavesAvailable")).isEqualTo(5);
    assertThat((Integer) r.at("$.summary.leavesUnused")).isEqualTo(5);
    assertThat((Integer) r.at("$.summary.leavesUsed")).isEqualTo(0);
    assertThat((Integer) r.at("$.summary.totalDaysOff")).isEqualTo(0);
    assertThat((Integer) r.at("$.summary.breakCount")).isEqualTo(0);
    assertThat((List<Boolean>) r.at("$.days[*].planned")).hasSize(365).containsOnly(false);
  }

  // ---- validation (PRD Validation and errors) -------------------------------------------------

  @Test
  void leaveDaysValidation() throws Exception {
    for (String bad : List.of("12.5", "\"12\"", "-1", "61")) {
      Reply r = plan(PlanJson.valid().with("leaveDays", bad));
      r.assertProblem(400, "invalid-input", LEAVE_DAYS_MESSAGE);
      assertThat((String) r.at("$.errors[0].field")).as("leaveDays=" + bad).isEqualTo("leaveDays");
      assertThat((String) r.at("$.errors[0].message"))
          .as("leaveDays=" + bad)
          .isEqualTo(LEAVE_DAYS_MESSAGE);
    }
  }

  @Test
  void leaveDaysBoundariesAreAccepted() throws Exception {
    assertThat(plan(PlanJson.valid().with("leaveDays", "0")).status())
        .as("0 is allowed")
        .isEqualTo(200);
    assertThat(plan(PlanJson.valid().with("leaveDays", "60")).status())
        .as("60 is allowed")
        .isEqualTo(200);
  }

  @Test
  void missingLeaveDaysAsksToFillItIn() throws Exception {
    Reply r = plan(PlanJson.valid().without("leaveDays"));
    r.assertProblem(400, "invalid-input", "Fill in leaveDays.");
    assertThat((String) r.at("$.errors[0].field")).isEqualTo("leaveDays");
  }

  @Test
  void everyRequiredFieldMissingOrNullAsksToFillItIn() throws Exception {
    for (String field : List.of("year", "leaveDays", "weeklyOffs", "holidays")) {
      plan(PlanJson.valid().without(field))
          .assertProblem(400, "invalid-input", "Fill in " + field + ".");
      plan(PlanJson.valid().with(field, "null"))
          .assertProblem(400, "invalid-input", "Fill in " + field + ".");
    }
  }

  @Test
  void allSevenWeeklyOffs() throws Exception {
    Reply r =
        plan(
            PlanJson.valid()
                .with(
                    "weeklyOffs",
                    "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SUNDAY\"]"));

    r.assertProblem(400, "invalid-input", "Keep at least one working day in the week.");
  }

  @Test
  void sevenEntriesWithRepeatsButOnlySixDistinctDaysAreAccepted() throws Exception {
    Reply r =
        plan(
            PlanJson.valid()
                .with(
                    "weeklyOffs",
                    "[\"MONDAY\",\"TUESDAY\",\"WEDNESDAY\",\"THURSDAY\",\"FRIDAY\",\"SATURDAY\",\"SATURDAY\"]"));

    assertThat(r.status()).as("repeats collapse before the working-day check").isEqualTo(200);
  }

  @Test
  void emptyWeeklyOffsAccepted() throws Exception {
    Reply r = plan(PlanJson.valid().with("weeklyOffs", "[]"));

    assertThat(r.status()).as("docs/API.md: empty is allowed (no weekly off)").isEqualTo(200);
  }

  @Test
  void repeatedAndReorderedWeeklyOffsGiveTheSamePlan() throws Exception {
    String normal = plan(thursdayHoliday(1)).body();
    String repeated =
        plan(thursdayHoliday(1).with("weeklyOffs", "[\"SATURDAY\",\"SUNDAY\",\"SATURDAY\"]"))
            .body();
    String reordered =
        plan(thursdayHoliday(1).with("weeklyOffs", "[\"SUNDAY\",\"SATURDAY\"]")).body();

    assertThat(repeated).as("a repeated day is collapsed into one").isEqualTo(normal);
    assertThat(reordered).as("order of weekly offs doesn't matter").isEqualTo(normal);
  }

  @Test
  void unknownWeekdayNameIsRejected() throws Exception {
    plan(PlanJson.valid().with("weeklyOffs", "[\"FUNDAY\"]"))
        .assertProblem(400, "invalid-input", "Weekly offs must be day names, like SATURDAY.");
  }

  @Test
  void yearOutsideTheCurrentAndNextYearIsRejected() throws Exception {
    for (String year : List.of("2025", "2028")) {
      Reply r = plan(PlanJson.valid().with("year", year));
      r.assertProblem(400, "invalid-input", "Pick 2026 or 2027.");
      assertThat((String) r.at("$.errors[0].field")).as("year=" + year).isEqualTo("year");
    }
  }

  @Test
  void unknownOrMissingBreakStyleIsRejected() throws Exception {
    String message = "Pick a break style: Long weekends, Short trips or Big trips.";
    Reply unknown = plan(PlanJson.valid().with("breakStyle", "\"WEEKEND\""));
    unknown.assertProblem(400, "invalid-input", message);
    assertThat((String) unknown.at("$.errors[0].field")).isEqualTo("breakStyle");
    plan(PlanJson.valid().without("breakStyle")).assertProblem(400, "invalid-input", message);
  }

  @Test
  void holidayOutsideTheChosenYearIsRejected() throws Exception {
    plan(PlanJson.valid().holidays("2026-12-25", "Fixture Holiday 1"))
        .assertProblem(400, "invalid-input", "Holiday 2026-12-25 is not in 2027.");
  }

  @Test
  void holidayNameOverEightyCharactersIsRejectedButEightyIsAccepted() throws Exception {
    plan(PlanJson.valid().holidays(THURSDAY, "n".repeat(81)))
        .assertProblem(400, "invalid-input", "Keep holiday names to 80 characters or fewer.");
    assertThat(plan(PlanJson.valid().holidays(THURSDAY, "n".repeat(80))).status())
        .as("80 characters is the longest allowed name")
        .isEqualTo(200);
  }

  @Test
  void moreThanOneHundredHolidaysAreRejectedButOneHundredAreAccepted() throws Exception {
    plan(PlanJson.valid().manyHolidays(101))
        .assertProblem(400, "invalid-input", "Use at most 100 holidays.");
    assertThat(plan(PlanJson.valid().manyHolidays(100)).status())
        .as("100 holidays is the limit")
        .isEqualTo(200);
  }

  // ---- merging, determinism, speed ------------------------------------------------------------

  @Test
  void duplicateHolidayDates() throws Exception {
    Reply r =
        plan(
            PlanJson.valid()
                .with("leaveDays", "1")
                .holidays(THURSDAY, "Fixture Holiday 1", THURSDAY, "Fixture Holiday 9"));

    assertThat(r.status()).as(r.body()).isEqualTo(200);
    List<Map<String, Object>> inBreak = r.at("$.breaks[0].holidays");
    assertThat(inBreak).as("PRD: 'Kept once, showing both names'").hasSize(1);
    assertThat((List<String>) inBreak.get(0).get("names"))
        .containsExactlyInAnyOrder("Fixture Holiday 1", "Fixture Holiday 9");
    assertThat((List<String>) day(r, THURSDAY).get("holidayNames"))
        .containsExactlyInAnyOrder("Fixture Holiday 1", "Fixture Holiday 9");
    assertThat((Integer) r.at("$.summary.totalDaysOff")).as("the date counts once").isEqualTo(4);
  }

  @Test
  void theSameRequestGivesTheSamePlan() throws Exception {
    PlanJson request =
        PlanJson.valid().holidays(THURSDAY, "Fixture Holiday 1", "2027-08-27", "Fixture Holiday 2");

    assertThat(plan(request).body())
        .as("PRD: same answer every time")
        .isEqualTo(plan(request).body());
  }

  @Test
  void fullYearPlanRespondsInUnderOneSecond() throws Exception {
    PlanJson request =
        PlanJson.valid()
            .with("leaveDays", "60")
            .holidays(
                "2027-01-26",
                "Fixture Holiday 1",
                THURSDAY,
                "Fixture Holiday 2",
                "2027-06-10",
                "Fixture Holiday 3",
                "2027-08-27",
                "Fixture Holiday 4",
                "2027-10-08",
                "Fixture Holiday 5",
                "2027-12-24",
                "Fixture Holiday 6");
    plan(request); // warm up the JVM so the timing measures the plan, not class loading

    long start = System.nanoTime();
    Reply r = plan(request);
    long millis = (System.nanoTime() - start) / 1_000_000;

    assertThat(r.status()).isEqualTo(200);
    assertThat(millis)
        .as("PRD: a full-year plan returns in under 1 second (took %d ms)", millis)
        .isLessThan(1000);
  }

  private static Map<String, Object> day(Reply r, String date) {
    List<Map<String, Object>> matches = r.at("$.days[?(@.date=='" + date + "')]");
    assertThat(matches).as("days[] has an entry for " + date).hasSize(1);
    return matches.get(0);
  }
}
