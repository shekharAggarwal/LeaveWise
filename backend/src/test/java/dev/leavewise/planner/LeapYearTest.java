package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static dev.leavewise.planner.Fixtures.plannerOn;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * PRD edge case: "A leap year (2028)". Weekdays: 2028-02-28 Mon, 2028-02-29 Tue, 2028-03-01 Wed.
 */
class LeapYearTest {

  @Test
  void plans366Days() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2028, 1));

    assertThat(plan.days()).as("2028 has 366 calendar days").hasSize(366);
    assertThat(plan.days().get(0).date()).isEqualTo(date(2028, 1, 1));
    assertThat(plan.days().get(365).date()).isEqualTo(date(2028, 12, 31));
    assertThat(plan.days())
        .extracting(CalendarDay::date)
        .as("29 February 2028 is a date of the year")
        .contains(date(2028, 2, 29));
  }

  @Test
  void aNonLeapYearPlans365Days() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1));

    assertThat(plan.days()).hasSize(365);
    assertThat(plan.days().get(31 + 28).date())
        .as("in 2027, 31 days of January and 28 of February come before 1 March")
        .isEqualTo(date(2027, 3, 1));
  }

  @Test
  void leapYearDatesAreConsecutive() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2028, 1));

    for (int i = 1; i < plan.days().size(); i++) {
      assertThat(plan.days().get(i).date())
          .as("day %d follows the previous date", i)
          .isEqualTo(plan.days().get(i - 1).date().plusDays(1));
    }
  }

  @Test
  void feb29IsAWorkingDayCandidate() {
    // Mon 28 Feb and Wed 1 Mar are holidays, so one leave on Tue 29 Feb joins them with the weekend
    // before: Sat 26 Feb to Wed 1 Mar = 5 days. Leaving 25 Feb instead gives only 4.
    PlanRequest request = defaults(2028, 1, holiday(2028, 2, 28), holiday(2028, 3, 1));
    PlanningCalendar calendar = PlanningCalendar.of(request, EARLY_TODAY);

    Plan plan = plannerOn(EARLY_TODAY).plan(request);

    assertThat(calendar.kindOf(date(2028, 2, 29))).isEqualTo(DayKind.WORKING);
    assertThat(plan.leaveDates())
        .as("29 Feb is a real working day and the best place for the leave")
        .containsExactly(date(2028, 2, 29));
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2028, 2, 26));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2028, 3, 1));
    assertThat(plan.totalDaysOff()).isEqualTo(5);
  }

  @Test
  void feb29MatchesTheBruteForceOracle() {
    PlanRequest request = defaults(2028, 1, holiday(2028, 2, 28), holiday(2028, 3, 1));

    assertThat(plannerOn(EARLY_TODAY).plan(request).leaveDates())
        .isEqualTo(DateOracle.solve(request, EARLY_TODAY).leaveDates());
  }

  @Test
  void marchFirstFollowsFebruary29InThePlanDays() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2028, 0));

    int feb29 = date(2028, 2, 29).getDayOfYear() - 1;
    assertThat(plan.days().get(feb29 + 1).date()).isEqualTo(date(2028, 3, 1));
    assertThat(plan.days().get(feb29).kind()).as("Tuesday is working").isEqualTo(DayKind.WORKING);
  }
}
