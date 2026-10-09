package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.SAT_SUN;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static dev.leavewise.planner.Fixtures.offs;
import static dev.leavewise.planner.Fixtures.plannerOn;
import static dev.leavewise.planner.Fixtures.request;
import static dev.leavewise.planner.PlanAssertions.day;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * PRD Planning rules and edge cases at date level. Weekdays used (checked with a script):
 * 2027-01-01 Fri, 2027-06-10 Thu, 2027-06-11 Fri, 2027-06-12 Sat, 2027-09-06 Mon, 2027-12-27 Mon.
 *
 * <p>Expected values: worked out by hand, then confirmed with an independent Python brute force
 * (and, where the leave budget is 1 or 2, with {@link DateOracle}, which uses {@link
 * BruteForcePlanner}). Cases with a bigger budget are marked "script only".
 */
class PlannerRulesTest {

  private static Plan plan(LocalDate today, PlanRequest request) {
    return plannerOn(today).plan(request);
  }

  private static Plan plan(PlanRequest request) {
    return plan(EARLY_TODAY, request);
  }

  @Test
  void prdExampleThursdayHolidayFridayLeave() {
    // PRD: "a holiday on Thursday plus 1 leave day on Friday makes a 4-day break, Thursday to
    // Sunday."
    PlanRequest request = defaults(2027, 1, holiday(2027, 6, 10, "Fixture Holiday 1"));

    Plan plan = plan(request);

    assertThat(plan.breaks()).hasSize(1);
    LeaveBreak theBreak = plan.breaks().get(0);
    assertThat(theBreak.start()).isEqualTo(date(2027, 6, 10));
    assertThat(theBreak.end()).as("Thursday to Sunday").isEqualTo(date(2027, 6, 13));
    assertThat(theBreak.days()).isEqualTo(4);
    assertThat(theBreak.leaveDates()).containsExactly(date(2027, 6, 11));
    assertThat(theBreak.holidays())
        .containsExactly(new HolidayDay(date(2027, 6, 10), List.of("Fixture Holiday 1")));
    assertThat(theBreak.daysOffPerLeave()).as("4 days for 1 leave").isEqualTo(4.0);
    assertThat(plan.leaveDates()).isEqualTo(DateOracle.solve(request, EARLY_TODAY).leaveDates());
    assertThat(day(plan, date(2027, 6, 10)).kind()).isEqualTo(DayKind.HOLIDAY);
    assertThat(day(plan, date(2027, 6, 11)).kind()).isEqualTo(DayKind.LEAVE);
    assertThat(day(plan, date(2027, 6, 12)).kind()).isEqualTo(DayKind.WEEKLY_OFF);
  }

  @Test
  void holidayOnWeeklyOffCountsOnce() {
    // Fri 11 Jun and Sat 12 Jun (a weekly off) are holidays. One leave on Thu 10 Jun gives Thu-Sun
    // = 4 days; if the Saturday holiday were counted twice the break would be 5.
    PlanRequest request =
        defaults(
            2027,
            1,
            holiday(2027, 6, 11, "Fixture Holiday 1"),
            holiday(2027, 6, 12, "Fixture Holiday 2"));

    Plan plan = plan(request);

    assertThat(plan.leaveDates()).containsExactly(date(2027, 6, 10));
    assertThat(plan.breaks().get(0).days())
        .as("PRD: 'A holiday on a weekly off counts once'")
        .isEqualTo(4);
    assertThat(plan.totalDaysOff()).isEqualTo(4);
    assertThat(plan.breaks().get(0).holidays())
        .as("both holidays are listed inside the break")
        .extracting(HolidayDay::date)
        .containsExactly(date(2027, 6, 11), date(2027, 6, 12));
    assertThat(day(plan, date(2027, 6, 12)).kind())
        .as("a holiday on a weekly off is a HOLIDAY day (docs/API.md)")
        .isEqualTo(DayKind.HOLIDAY);
    assertThat(plan.leaveDates()).isEqualTo(DateOracle.solve(request, EARLY_TODAY).leaveDates());
  }

  @Test
  void leavesOnlyOnWorkingDays() {
    // PRD: "Leave day: always placed on a working day."
    PlanRequest request =
        defaults(
            2027,
            2,
            holiday(2027, 6, 11, "Fixture Holiday 1"),
            holiday(2027, 9, 6, "Fixture Holiday 2"));
    PlanningCalendar calendar = PlanningCalendar.of(request, EARLY_TODAY);

    Plan plan = plan(request);

    assertThat(plan.leaveDates()).hasSize(2);
    for (LocalDate leave : plan.leaveDates()) {
      assertThat(calendar.kindOf(leave))
          .as("%s must be a working day before leaves are placed", leave)
          .isEqualTo(DayKind.WORKING);
      assertThat(day(plan, leave).kind()).isEqualTo(DayKind.LEAVE);
    }
    assertThat(plan.leaveDates()).isEqualTo(DateOracle.solve(request, EARLY_TODAY).leaveDates());
    assertThat(plan.leaveDates())
        .as("script only: two separate 4-day breaks (Thu 10 - Sun 13 Jun, Fri 3 - Mon 6 Sep)")
        .containsExactly(date(2027, 6, 10), date(2027, 9, 3));
  }

  @Test
  void breakNeedsALeaveDay() {
    // Fri 11 Jun + weekend is 3 days off, but with 0 leaves it is not a break.
    Plan plan = plan(defaults(2027, 0, holiday(2027, 6, 11)));

    assertThat(plan.breaks()).as("PRD Break: 'at least one leave day'").isEmpty();
    assertThat(plan.freeLongWeekends()).hasSize(1);
  }

  @Test
  void breakMeetsMinimumLengthLongWeekends() {
    Plan plan = plan(defaults(2027, 1));

    assertThat(plan.leaveDates())
        .as("script only: 1 leave on Fri 1 Jan 2027 gives Fri-Sun, 3 days, the earliest such plan")
        .containsExactly(date(2027, 1, 1));
    assertThat(plan.breaks().get(0).days()).as("exactly the 3-day minimum").isEqualTo(3);
    assertThat(plan.leaveDates())
        .isEqualTo(DateOracle.solve(defaults(2027, 1), EARLY_TODAY).leaveDates());
  }

  @Test
  void breakMeetsMinimumLengthShortTrips() {
    PlanRequest twoLeaves = request(2027, 2, SAT_SUN, BreakStyle.SHORT_TRIPS);
    PlanRequest threeLeaves = request(2027, 3, SAT_SUN, BreakStyle.SHORT_TRIPS);

    Plan two = plan(twoLeaves);
    Plan three = plan(threeLeaves);

    assertThat(two.breaks())
        .as("2 leaves reach at most 4 days (Fri-Mon), below the 5-day minimum")
        .isEmpty();
    assertThat(three.breaks()).hasSize(1);
    assertThat(three.breaks().get(0).days()).as("exactly the 5-day minimum").isEqualTo(5);
    assertThat(three.leaveDates())
        .as("script only: Fri 1 Jan, Mon 4 Jan and Tue 5 Jan 2027 make Fri 1 - Tue 5 Jan")
        .containsExactly(date(2027, 1, 1), date(2027, 1, 4), date(2027, 1, 5));
    assertThat(two.leaveDates()).isEqualTo(DateOracle.solve(twoLeaves, EARLY_TODAY).leaveDates());
  }

  @Test
  void breakMeetsMinimumLengthBigTrips() {
    Plan four = plan(request(2027, 4, SAT_SUN, BreakStyle.BIG_TRIPS));
    Plan five = plan(request(2027, 5, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(four.breaks())
        .as("4 leaves reach at most 6 days, below the 9-day minimum")
        .isEmpty();
    assertThat(five.breaks()).hasSize(1);
    assertThat(five.breaks().get(0).days()).as("exactly the 9-day minimum").isEqualTo(9);
    assertThat(five.breaks().get(0).start()).isEqualTo(date(2027, 1, 2));
    assertThat(five.breaks().get(0).end()).isEqualTo(date(2027, 1, 10));
    assertThat(five.leaveDates())
        .as("script only: Mon 4 - Fri 8 Jan 2027 joined to the weekends either side")
        .containsExactly(
            date(2027, 1, 4),
            date(2027, 1, 5),
            date(2027, 1, 6),
            date(2027, 1, 7),
            date(2027, 1, 8));
  }

  @Test
  void bestPlanMaximisesTotalDaysAcrossTheYear() {
    // PRD Best plan: "the most total days inside breaks". Two leaves in one run (Thu 10 - Mon 14
    // Jun)
    // give 5 days, but one leave on Fri 11 Jun (4 days) plus one on Fri 1 Jan (3 days) give 7.
    PlanRequest request = defaults(2027, 2, holiday(2027, 6, 10, "Fixture Holiday 1"));

    Plan plan = plan(request);

    assertThat(plan.totalDaysOff()).isEqualTo(7);
    assertThat(plan.breakCount()).isEqualTo(2);
    assertThat(plan.leaveDates()).containsExactly(date(2027, 1, 1), date(2027, 6, 11));
    assertThat(plan.totalDaysOff())
        .isEqualTo(DateOracle.solve(request, EARLY_TODAY).daysInBreaks());
  }

  @Test
  void earliestLeaveWinsAmongEqualPlans() {
    // PRD Ties: with no holidays every Friday gives the same 3 days; the earliest date wins.
    Plan plan = plan(defaults(2027, 1));

    assertThat(plan.leaveDates()).containsExactly(date(2027, 1, 1));
  }

  @Test
  void twelveLeavesWithNoHolidaysBuildTwelveLongWeekends() {
    // PRD Summary example shape: "12 leaves -> N days off across K breaks". With only weekends the
    // best is a Friday leave 12 times: 12 x 3 = 36 days (script only; a Thu+Fri pair gives only 4).
    Plan plan = plan(defaults(2027, 12));

    assertThat(plan.leavesUsed()).isEqualTo(12);
    assertThat(plan.totalDaysOff()).isEqualTo(36);
    assertThat(plan.breakCount()).isEqualTo(12);
    assertThat(plan.leaveDates())
        .containsExactlyElementsOf(
            IntStream.range(0, 12).mapToObj(i -> date(2027, 1, 1).plusWeeks(i)).toList());
    assertThat(plan.note()).as("every leave was used, so no note").isEmpty();
    assertThat(plan.leavesUnused()).isZero();
  }

  @Test
  void noHolidays() {
    // PRD edge case "Every holiday unticked".
    Plan plan = plan(defaults(2027, 12));

    assertThat(plan.breaks()).allMatch(b -> b.holidays().isEmpty());
    assertThat(plan.days()).noneMatch(d -> d.kind() == DayKind.HOLIDAY);
    assertThat(plan.totalDaysOff()).isEqualTo(36);
  }

  @Test
  void zeroLeavesGivesNoBreaksAndFreeWeekends() {
    // PRD: "0 leave days: Free long weekends only, with a note that no leave is used."
    Plan plan = plan(defaults(2027, 0, holiday(2027, 6, 11), holiday(2027, 9, 6)));

    assertThat(plan.breaks()).isEmpty();
    assertThat(plan.freeLongWeekends()).hasSize(2);
    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_LEAVE_USED);
    assertThat(plan.leavesUsed()).isZero();
    assertThat(plan.leavesAvailable()).isZero();
    assertThat(plan.totalDaysOff()).isZero();
  }

  @Test
  void moreLeavesThanWorkingDaysUsesEveryWorkingDay() {
    // PRD: "More leave days than working days left: A plan using every working day, with a note on
    // how many leaves were left over." Today Mon 27 Dec 2027: working days left are Tue 28 - Fri
    // 31.
    PlanRequest request = defaults(2027, 6);

    Plan plan = plan(date(2027, 12, 27), request);

    assertThat(plan.leaveDates())
        .containsExactly(
            date(2027, 12, 28), date(2027, 12, 29), date(2027, 12, 30), date(2027, 12, 31));
    assertThat(plan.breaks()).hasSize(1);
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2027, 12, 28));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2028, 1, 2));
    assertThat(plan.totalDaysOff()).isEqualTo(6);
    assertThat(plan.note()).contains(new Note(Note.Code.LEAVES_LEFT_OVER, 2));
    assertThat(plan.leavesUnused()).isEqualTo(2);
    assertThat(plan.leaveDates())
        .isEqualTo(DateOracle.solve(request, date(2027, 12, 27)).leaveDates());
  }

  @Test
  void exactlyAsManyLeavesAsWorkingDaysIsNotLeftOver() {
    Plan plan = plan(date(2027, 12, 27), defaults(2027, 4));

    assertThat(plan.leavesUsed()).isEqualTo(4);
    assertThat(plan.note()).as("4 leaves for 4 working days: nothing left over").isEmpty();
  }

  @Test
  void sundayOnlyWeek() {
    // PRD edge case "Only Sunday as a weekly off". Fri 1 Jan + Sat 2 Jan leaves make Fri-Sun = 3
    // days.
    PlanRequest oneLeave = request(2027, 1, offs(DayOfWeek.SUNDAY), BreakStyle.LONG_WEEKENDS);
    PlanRequest twoLeaves = request(2027, 2, offs(DayOfWeek.SUNDAY), BreakStyle.LONG_WEEKENDS);

    Plan one = plan(oneLeave);
    Plan two = plan(twoLeaves);

    assertThat(one.breaks()).as("1 leave + 1 Sunday = 2 days, below 3").isEmpty();
    assertThat(two.leaveDates()).containsExactly(date(2027, 1, 1), date(2027, 1, 2));
    assertThat(two.breaks().get(0).days()).isEqualTo(3);
    assertThat(two.leaveDates()).isEqualTo(DateOracle.solve(twoLeaves, EARLY_TODAY).leaveDates());
    assertThat(day(two, date(2027, 1, 2)).kind())
        .as("Saturday is a working day here")
        .isEqualTo(DayKind.LEAVE);
  }

  @Test
  void noWeeklyOffs() {
    // PRD allows any days of the week as weekly offs, so none is valid. Without any day off every
    // break day is a leave, so 12 leaves give one 12-day break, the earliest possible.
    PlanRequest twelve = request(2027, 12, offs(), BreakStyle.LONG_WEEKENDS);
    PlanRequest two = request(2027, 2, offs(), BreakStyle.LONG_WEEKENDS);

    Plan plan = plan(twelve);

    assertThat(plan.totalDaysOff()).isEqualTo(12);
    assertThat(plan.breaks()).hasSize(1);
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2027, 1, 1));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2027, 1, 12));
    assertThat(plan(two).note()).map(Note::code).contains(Note.Code.NO_BREAK_POSSIBLE);
    assertThat(plan(two).leaveDates()).isEqualTo(DateOracle.solve(two, EARLY_TODAY).leaveDates());
  }

  @Test
  void unusedLeavesWhenNothingGrows() {
    // PRD: "A leave day outside a break adds nothing". Big trips with 4 leaves can't reach 9 days,
    // so no leave is placed at all.
    Plan plan = plan(request(2027, 4, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.leaveDates()).isEmpty();
    assertThat(plan.leavesUnused()).isEqualTo(4);
    assertThat(plan.days()).noneMatch(d -> d.kind() == DayKind.LEAVE);
  }

  @Test
  void noBreakPossible() {
    // PRD: "No break possible with these inputs: The reason, plus a suggestion to try a shorter
    // break style."
    Plan plan = plan(request(2027, 3, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.breaks()).isEmpty();
    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_BREAK_POSSIBLE);
  }

  @Test
  void ordinaryPlanHasNoNote() {
    Plan plan = plan(defaults(2027, 12, holiday(2027, 6, 11), holiday(2027, 9, 6)));

    assertThat(plan.note()).isEmpty();
  }

  @Test
  void onlyOneNoteNoBreakPossibleEvenThoughEveryLeaveIsUnused() {
    // docs/API.md: one note per plan; with no break, NO_BREAK_POSSIBLE is the note although all 3
    // leaves are spare.
    Plan plan = plan(request(2027, 3, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.leavesUnused()).isEqualTo(3);
    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_BREAK_POSSIBLE);
  }

  @Test
  void onlyOneNoteNoLeaveUsedBeatsNoBreakPossible() {
    Plan plan = plan(request(2027, 0, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_LEAVE_USED);
  }

  @Test
  void onlyOneNoteNoBreakPossibleBeatsLeavesLeftOver() {
    // Today Thu 30 Dec 2027: one working day (Fri 31 Dec), 5 leaves, Big trips: even all of the
    // window reaches only 3 days, so the note is NO_BREAK_POSSIBLE, not LEAVES_LEFT_OVER.
    Plan plan = plan(date(2027, 12, 30), request(2027, 5, SAT_SUN, BreakStyle.BIG_TRIPS));

    assertThat(plan.note()).map(Note::code).contains(Note.Code.NO_BREAK_POSSIBLE);
  }

  @Test
  void leftOverNoteCountsTheLeavesNotUsed() {
    // Today Thu 30 Dec 2027, Long weekends: Fri 31 Dec is a leave (3 days to Sun 2 Jan); 4 of 5
    // left.
    Plan plan = plan(date(2027, 12, 30), defaults(2027, 5));

    assertThat(plan.note()).contains(new Note(Note.Code.LEAVES_LEFT_OVER, 4));
  }

  @Test
  void sameInputsAlwaysGiveTheSamePlan() {
    PlanRequest request = defaults(2027, 12, holiday(2027, 6, 11), holiday(2027, 9, 6));
    Planner planner = plannerOn(EARLY_TODAY);

    assertThat(planner.plan(request))
        .as("PRD: 'the same inputs always give the same plan'")
        .isEqualTo(planner.plan(request));
  }

  @Test
  void leaveBalanceIsNeverExceeded() {
    for (int leaves : new int[] {0, 1, 5, 12, 60}) {
      Plan plan = plan(defaults(2027, leaves, holiday(2027, 6, 11), holiday(2027, 9, 6)));

      assertThat(plan.leavesUsed()).as("%d leaves available", leaves).isLessThanOrEqualTo(leaves);
      assertThat(plan.leavesAvailable()).isEqualTo(leaves);
    }
  }
}
