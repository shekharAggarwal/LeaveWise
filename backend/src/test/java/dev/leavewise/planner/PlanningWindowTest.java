package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.plannerOn;
import static dev.leavewise.planner.Fixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * PRD Inputs: "For the current year, only days after today are planned." ADR 0006. Weekdays:
 * 2027-03-10 is a Wednesday, 2027-03-14 a Sunday, 2027-12-30 a Thursday, 2027-12-31 a Friday.
 */
class PlanningWindowTest {

  @Test
  void currentYearStartsTomorrow() {
    Plan plan = plannerOn(date(2027, 3, 10)).plan(defaults(2027, 1));

    assertThat(plan.planningFrom())
        .as("window starts the day after today")
        .contains(date(2027, 3, 11));
    assertThat(plan.planningTo()).as("window ends on 31 December").contains(date(2027, 12, 31));
    assertThat(plan.days()).as("the calendar still has every date of the year").hasSize(365);
    assertThat(plan.days())
        .filteredOn(day -> !day.date().isAfter(date(2027, 3, 10)))
        .as("PRD: days up to and including today are not planned")
        .allMatch(day -> !day.planned());
    assertThat(plan.days())
        .filteredOn(day -> day.date().isAfter(date(2027, 3, 10)))
        .as("every date after today is planned")
        .allMatch(CalendarDay::planned);
  }

  @Test
  void nextYearIsPlannedInFullEvenLateInTheCurrentYear() {
    Plan plan = plannerOn(date(2027, 12, 30)).plan(defaults(2028, 1));

    assertThat(plan.planningFrom()).contains(date(2028, 1, 1));
    assertThat(plan.days()).allMatch(CalendarDay::planned, "all 366 days of 2028 are planned");
  }

  @Test
  void todayIsNotCountedEvenIfItIsADayOff() {
    // Today is Sunday 14 Mar 2027. A leave on Monday 15 Mar would make Sat-Sun-Mon only if the
    // weekend already over were counted; it must not be (ADR 0006).
    Plan plan = plannerOn(date(2027, 3, 14)).plan(defaults(2027, 1));

    assertThat(plan.leaveDates())
        .as(
            "the first 3-day break that starts after today is Fri 19 - Sun 21 Mar, not Sat 13 - Mon 15")
        .containsExactly(date(2027, 3, 19));
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2027, 3, 19));
    assertThat(plan.days().get(date(2027, 3, 14).getDayOfYear() - 1).kind())
        .as("today keeps its kind (a weekly off) but is not planned")
        .isEqualTo(DayKind.WEEKLY_OFF);
  }

  @Test
  void leavesNeverFallOnOrBeforeToday() {
    Plan plan = plannerOn(date(2027, 3, 10)).plan(defaults(2027, 12));

    assertThat(plan.leaveDates()).isNotEmpty().allMatch(d -> d.isAfter(date(2027, 3, 10)));
  }

  @Test
  void planOnThirtyFirstDecemberHasNoDaysLeft() {
    // PRD Edge case: "Current year, planning from today". API.md: NO_DAYS_LEFT, nothing planned.
    Plan plan = plannerOn(date(2027, 12, 31)).plan(defaults(2027, 5));

    assertThat(plan.planningFrom()).as("empty window").isEmpty();
    assertThat(plan.planningTo()).isEmpty();
    assertThat(plan.breaks()).isEmpty();
    assertThat(plan.freeLongWeekends()).isEmpty();
    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_DAYS_LEFT);
    assertThat(plan.days()).hasSize(365).allMatch(day -> !day.planned(), "no date is planned");
    assertThat(plan.leavesAvailable()).isEqualTo(5);
    assertThat(plan.leavesUnused()).as("all 5 leaves are unused").isEqualTo(5);
    assertThat(plan.totalDaysOff()).isZero();
  }

  @Test
  void noDaysLeftTakesPrecedenceOverNoLeaveUsed() {
    Plan plan = plannerOn(date(2027, 12, 31)).plan(defaults(2027, 0));

    assertThat(plan.note())
        .as("docs/API.md: precedence NO_DAYS_LEFT, NO_LEAVE_USED, ...")
        .map(Note::code)
        .contains(Note.Code.NO_DAYS_LEFT);
  }

  @Test
  void windowShorterThanMinimumBreakGivesNoBreakPossible() {
    // Today Thu 30 Dec 2027: only Fri 31 Dec is left; 1 and 2 Jan 2028 are the weekend. With
    // Big trips (9 days) even the whole window can't reach the minimum.
    Plan plan =
        plannerOn(date(2027, 12, 30))
            .plan(request(2027, 5, Fixtures.SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.breaks()).as("no 9-day break fits in 3 days").isEmpty();
    assertThat(plan.leaveDates()).isEmpty();
    assertThat(plan.note())
        .as(
            "PRD tension: 'more leave days than working days' vs no break: docs/API.md says NO_BREAK_POSSIBLE")
        .map(Note::code)
        .contains(Note.Code.NO_BREAK_POSSIBLE);
  }

  @Test
  void oneDayWindowStillBuildsAShortBreakWhenTheEdgeWeekendReachesTheMinimum() {
    // Same day, Long weekends: Fri 31 Dec + Sat 1 Jan + Sun 2 Jan = 3 days, so the break exists.
    Plan plan = plannerOn(date(2027, 12, 30)).plan(defaults(2027, 1));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 12, 31));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2028, 1, 2));
    assertThat(plan.note()).as("the one leave is used").isEmpty();
  }

  @Test
  void todayIsReadInTheClocksZone() {
    // 20:00 UTC on 14 Mar 2027 is 01:30 on 15 Mar in India: today is the 15th, so planning starts
    // on the 16th. Reading the date in UTC would start on the 15th.
    Clock clock = Clock.fixed(Instant.parse("2027-03-14T20:00:00Z"), Fixtures.IST);

    Plan plan = new Planner(clock).plan(defaults(2027, 1));

    assertThat(plan.planningFrom()).contains(date(2027, 3, 16));
  }

  @Test
  void planYearBeforeTheCurrentYearIsRejected() {
    assertThatThrownBy(() -> plannerOn(date(2027, 3, 10)).plan(defaults(2026, 1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void earlierYearClockPlansTheWholeYear() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1));

    assertThat(plan.planningFrom()).contains(LocalDate.of(2027, 1, 1));
  }
}
