package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static dev.leavewise.planner.Fixtures.offs;
import static dev.leavewise.planner.Fixtures.plannerOn;
import static dev.leavewise.planner.Fixtures.request;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * PRD Year edges: "leave days stay inside the chosen year, but a break also counts the weekly offs
 * and known holidays just before 1 January and just after 31 December." Weekdays: 2026-12-31 Thu,
 * 2027-01-01 Fri, 2027-12-29 Wed, 2027-12-31 Fri, 2028-01-01 Sat, 2028-01-03 Mon.
 *
 * <p>Expected values were worked out by hand and confirmed with a separate brute-force script;
 * cases with a short window are also compared with the Java oracle through {@link DateOracle}.
 */
class YearEdgeTest {

  @Test
  void breakExtendsIntoNextYear() {
    // Window is 30-31 Dec 2027 (today Wed 29 Dec). 3 Jan 2028 (Mon) is a known holiday, so a leave
    // on Fri 31 Dec gives Fri, Sat, Sun, Mon = 4 days, ending in the next year.
    Plan plan = plannerOn(date(2027, 12, 29)).plan(defaults(2027, 1, holiday(2028, 1, 3)));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 12, 31));
    assertThat(plan.breaks()).hasSize(1);
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2027, 12, 31));
    assertThat(plan.breaks().get(0).end())
        .as("PRD: the break counts the known holiday just after 31 December")
        .isEqualTo(date(2028, 1, 3));
    assertThat(plan.totalDaysOff()).as("Fri 31 Dec to Mon 3 Jan").isEqualTo(4);
    assertThat(plan.breaks().get(0).holidays())
        .extracting(HolidayDay::date)
        .containsExactly(date(2028, 1, 3));
  }

  @Test
  void breakExtendsIntoNextYearInAFullYearPlan() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1, holiday(2028, 1, 3)));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 12, 31));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2028, 1, 3));
    assertThat(plan.totalDaysOff())
        .as("4 days is the best one leave can do: only this holiday gives more than a weekend")
        .isEqualTo(4);
  }

  @Test
  void breakExtendsFromPreviousYear() {
    // Thu 31 Dec 2026 is a known holiday; the leave goes on Fri 1 Jan 2027: Thu, Fri, Sat, Sun.
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1, holiday(2026, 12, 31)));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 1, 1));
    assertThat(plan.breaks().get(0).start())
        .as("PRD: the break counts the known holiday just before 1 January")
        .isEqualTo(date(2026, 12, 31));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2027, 1, 3));
    assertThat(plan.totalDaysOff()).isEqualTo(4);
    assertThat(plan.days().get(0).date())
        .as("the 12-month calendar shows only the chosen year")
        .isEqualTo(date(2027, 1, 1));
    assertThat(plan.days().get(0).breakIndex())
        .as("1 Jan is inside break 0")
        .isEqualTo(OptionalInt.of(0));
    assertThat(plan.days().get(2).breakIndex())
        .as("3 Jan is inside break 0")
        .isEqualTo(OptionalInt.of(0));
  }

  @Test
  void breakExtendsFromPreviousYearThroughWeeklyOffs() {
    // Weekly offs Friday and Saturday. 31 Dec 2027 (Fri) and 1 Jan 2028 (Sat) are days off, Sun 2
    // Jan
    // is working. One leave on Sun 2 Jan joins them: Fri, Sat, Sun = 3 days starting in 2027.
    // Without the leading edge the run would be only Sat-Sun (2 days) and no 3-day break would
    // start on 2 Jan.
    Plan plan =
        plannerOn(EARLY_TODAY)
            .plan(
                request(
                    2028, 1, offs(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY), BreakStyle.LONG_WEEKENDS));

    assertThat(plan.leaveDates()).containsExactly(date(2028, 1, 2));
    assertThat(plan.breaks().get(0).start())
        .as("weekly offs just before 1 January count even with no holiday data")
        .isEqualTo(date(2027, 12, 31));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2028, 1, 2));
  }

  @Test
  void unknownAdjacentYearUsesWeeklyOffsOnly() {
    // No holiday for 2028: only the weekly offs on Sat 1 and Sun 2 Jan 2028 extend the break.
    Plan plan = plannerOn(date(2027, 12, 29)).plan(defaults(2027, 1));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 12, 31));
    assertThat(plan.breaks().get(0).end())
        .as("Fri 31 Dec 2027 to Sun 2 Jan 2028: 3 days")
        .isEqualTo(date(2028, 1, 2));
    assertThat(plan.totalDaysOff()).isEqualTo(3);
  }

  @Test
  void holidayNotConnectedToTheYearEndDoesNotExtendTheBreak() {
    Plan plan = plannerOn(date(2027, 12, 29)).plan(defaults(2027, 1, holiday(2028, 1, 10)));

    assertThat(plan.breaks().get(0).end())
        .as("10 Jan 2028 is separated from the weekend by working days")
        .isEqualTo(date(2028, 1, 2));
  }

  @Test
  void twoLeavesAtTheEndOfTheYearBuildTheLongerBreak() {
    Plan plan = plannerOn(date(2027, 12, 29)).plan(defaults(2027, 2));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 12, 30), date(2027, 12, 31));
    assertThat(plan.breaks().get(0).days()).as("Thu 30 Dec to Sun 2 Jan").isEqualTo(4);
  }

  @Test
  void noLeaveOutsideYear() {
    // 30 and 31 Dec 2026 (Wed, Thu) are holidays. A leave on 29 Dec 2026 would extend that run,
    // but leaves must stay inside 2027, so the best 5 leaves are all in 2027.
    PlanRequest request = defaults(2027, 5, holiday(2026, 12, 30), holiday(2026, 12, 31));
    Plan plan = plannerOn(EARLY_TODAY).plan(request);

    assertThat(plan.leaveDates())
        .as("PRD: 'leave days stay inside the chosen year'")
        .isNotEmpty()
        .allMatch(d -> d.getYear() == 2027);
    assertThat(plan.leaveDates())
        .containsExactly(
            date(2027, 1, 1),
            date(2027, 1, 8),
            date(2027, 1, 15),
            date(2027, 1, 22),
            date(2027, 1, 29));
    assertThat(plan.breaks().get(0).start())
        .as("first break: Wed 30 Dec 2026 to Sun 3 Jan 2027 with the leave on Fri 1 Jan")
        .isEqualTo(date(2026, 12, 30));
    assertThat(plan.totalDaysOff()).as("5 + 3 + 3 + 3 + 3").isEqualTo(17);
  }

  @Test
  void edgeCasesAgreeWithTheBruteForceOracle() {
    LocalDateCase[] cases = {
      new LocalDateCase(date(2027, 12, 29), defaults(2027, 1)),
      new LocalDateCase(date(2027, 12, 29), defaults(2027, 2)),
      new LocalDateCase(date(2027, 12, 29), defaults(2027, 1, holiday(2028, 1, 3))),
      new LocalDateCase(date(2027, 12, 29), defaults(2027, 3, holiday(2028, 1, 4))),
      new LocalDateCase(EARLY_TODAY, defaults(2027, 1, holiday(2026, 12, 31))),
      new LocalDateCase(
          EARLY_TODAY, defaults(2027, 2, holiday(2026, 12, 30), holiday(2026, 12, 31))),
      new LocalDateCase(EARLY_TODAY, defaults(2028, 2, holiday(2027, 12, 31))),
    };
    for (LocalDateCase c : cases) {
      DateOracle.Answer expected = DateOracle.solve(c.request(), c.today());

      Plan plan = plannerOn(c.today()).plan(c.request());

      assertThat(plan.leaveDates())
          .as("leave dates for %s planned on %s", c.request(), c.today())
          .isEqualTo(expected.leaveDates());
      assertThat(plan.totalDaysOff()).isEqualTo(expected.daysInBreaks());
      assertThat(plan.breaks().stream().map(b -> new DateOracle.Span(b.start(), b.end())).toList())
          .isEqualTo(expected.breaks());
    }
  }

  private record LocalDateCase(java.time.LocalDate today, PlanRequest request) {}
}
