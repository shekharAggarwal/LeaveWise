package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static dev.leavewise.planner.Fixtures.offs;
import static dev.leavewise.planner.Fixtures.request;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The calendar the planner works on (ADR 0006). Weekdays used: 2027-01-01 is a Friday, 2027-06-10 a
 * Thursday, 2028-01-01 a Saturday, 2028-12-31 a Sunday (checked with a script).
 */
class PlanningCalendarTest {

  private static PlanningCalendar calendar(PlanRequest request) {
    return PlanningCalendar.of(request, EARLY_TODAY);
  }

  private static int daysOffIn(PlanningCalendar calendar) {
    int count = 0;
    for (boolean off : calendar.dayOff()) {
      if (off) {
        count++;
      }
    }
    return count;
  }

  @Test
  void workingDayIsNeitherWeeklyOffNorHoliday() {
    // PRD: "Working day: any day that is not a weekly off and not a holiday."
    PlanningCalendar calendar = calendar(defaults(2027, 1, holiday(2027, 6, 10)));

    assertThat(calendar.kindOf(date(2027, 6, 9)))
        .as("Wed 9 Jun 2027 is not a weekly off and not a holiday")
        .isEqualTo(DayKind.WORKING);
    assertThat(calendar.kindOf(date(2027, 6, 12)))
        .as("Sat 12 Jun 2027 is a weekly off")
        .isEqualTo(DayKind.WEEKLY_OFF);
    assertThat(calendar.kindOf(date(2027, 6, 10)))
        .as("Thu 10 Jun 2027 is a holiday")
        .isEqualTo(DayKind.HOLIDAY);
  }

  @Test
  void holidayOnWeeklyOffIsOneDayOff() {
    // PRD: "A holiday on a weekly off counts once."
    PlanningCalendar withoutHoliday = calendar(defaults(2027, 1));
    PlanningCalendar withSaturdayHoliday = calendar(defaults(2027, 1, holiday(2027, 6, 12)));

    assertThat(withSaturdayHoliday.kindOf(date(2027, 6, 12)))
        .as("a holiday on a weekly off is reported as HOLIDAY (docs/API.md)")
        .isEqualTo(DayKind.HOLIDAY);
    assertThat(daysOffIn(withSaturdayHoliday))
        .as(
            "a holiday on Saturday must not add a day off to the %d already there",
            daysOffIn(withoutHoliday))
        .isEqualTo(daysOffIn(withoutHoliday));
    assertThat(withSaturdayHoliday.horizon())
        .as("the date appears once in the horizon")
        .filteredOn(date(2027, 6, 12)::equals)
        .hasSize(1);
  }

  @Test
  void weekdayHolidayAddsExactlyOneDayOff() {
    PlanningCalendar withoutHoliday = calendar(defaults(2027, 1));
    PlanningCalendar withMondayHoliday = calendar(defaults(2027, 1, holiday(2027, 6, 14)));

    assertThat(daysOffIn(withMondayHoliday)).isEqualTo(daysOffIn(withoutHoliday) + 1);
  }

  @Test
  void dayOffArrayLinesUpWithTheHorizon() {
    PlanningCalendar calendar = calendar(defaults(2027, 1, holiday(2027, 6, 10)));

    assertThat(calendar.dayOff())
        .as("dayOff()[i] describes horizon().get(i)")
        .hasSameSizeAs(calendar.horizon());
    for (int i = 0; i < calendar.dayOff().length; i++) {
      LocalDate day = calendar.horizon().get(i);
      assertThat(calendar.dayOff()[i])
          .as("dayOff for %s", day)
          .isEqualTo(calendar.kindOf(day) != DayKind.WORKING);
    }
  }

  @Test
  void futureYearWindowIsTheWholeYear() {
    PlanningCalendar calendar = calendar(defaults(2027, 1));

    assertThat(calendar.windowStart()).contains(date(2027, 1, 1));
    assertThat(calendar.windowEnd()).contains(date(2027, 12, 31));
  }

  @Test
  void horizonStartsOnJanuaryFirstWhenTheDayBeforeIsWorking() {
    // 31 Dec 2026 is a Thursday: a working day, so nothing extends the start.
    PlanningCalendar calendar = calendar(defaults(2027, 1));

    assertThat(calendar.horizon().get(0))
        .as("PRD Year edges: only days off just before 1 January are added")
        .isEqualTo(date(2027, 1, 1));
  }

  @Test
  void noHolidayDataOutsideTheYearMeansWeeklyOffsOnlyAtTheEnd() {
    // ADR 0006: "If the adjacent year's list isn't bundled, only weekly offs count."
    // 1 and 2 Jan 2028 are Saturday and Sunday.
    PlanningCalendar calendar = calendar(defaults(2027, 1));

    assertThat(calendar.horizon().get(calendar.horizon().size() - 1))
        .as("horizon ends after the weekly offs that follow 31 Dec 2027 (Fri)")
        .isEqualTo(date(2028, 1, 2));
    assertThat(calendar.horizon()).as("365 days of 2027 plus 2 weekly-off days").hasSize(367);
    assertThat(daysOffIn(calendar))
        .as("104 weekend days in 2027 plus 2 on 1 and 2 Jan 2028")
        .isEqualTo(106);
  }

  @Test
  void leadingEdgeExtendsOverHolidaysBeforeJanuaryFirst() {
    // 30 and 31 Dec 2027 (Thu, Fri) are holidays; 1 and 2 Jan 2028 (Sat, Sun) are weekly offs.
    PlanningCalendar calendar =
        calendar(defaults(2028, 1, holiday(2027, 12, 30), holiday(2027, 12, 31)));

    assertThat(calendar.windowStart())
        .as("leaves can't go before 1 Jan")
        .contains(date(2028, 1, 1));
    assertThat(calendar.horizon().get(0))
        .as("PRD: a break counts 'known holidays just before 1 January'")
        .isEqualTo(date(2027, 12, 30));
  }

  @Test
  void leadingEdgeStopsAtTheFirstWorkingDay() {
    // 29 Dec 2027 is a holiday but 30 and 31 Dec are working days, so the run isn't connected.
    PlanningCalendar calendar = calendar(defaults(2028, 1, holiday(2027, 12, 29)));

    assertThat(calendar.horizon().get(0)).isEqualTo(date(2028, 1, 1));
  }

  @Test
  void trailingEdgeExtendsOverHolidaysAfterDecember31() {
    // 31 Dec 2028 is a Sunday; 1 and 2 Jan 2029 (Mon, Tue) are holidays; 3 Jan is a working day.
    PlanningCalendar calendar =
        calendar(defaults(2028, 1, holiday(2029, 1, 1), holiday(2029, 1, 2)));

    assertThat(calendar.horizon().get(calendar.horizon().size() - 1))
        .as("PRD: a break counts 'known holidays just after 31 December'")
        .isEqualTo(date(2029, 1, 2));
  }

  @Test
  void holidayAfterAWorkingDayDoesNotExtendTheEnd() {
    // 10 Jan 2028 is a holiday but the run from 31 Dec 2027 ends on 2 Jan 2028.
    PlanningCalendar calendar = calendar(defaults(2027, 1, holiday(2028, 1, 10)));

    assertThat(calendar.horizon().get(calendar.horizon().size() - 1)).isEqualTo(date(2028, 1, 2));
    assertThat(calendar.kindOf(date(2028, 1, 10)))
        .as("the date is still a known holiday")
        .isEqualTo(DayKind.HOLIDAY);
  }

  @Test
  void noWeeklyOffsAndNoHolidaysMeansNoEdges() {
    PlanningCalendar calendar = calendar(request(2027, 1, offs(), BreakStyle.LONG_WEEKENDS));

    assertThat(calendar.horizon()).hasSize(365);
    assertThat(daysOffIn(calendar)).isZero();
  }

  @Test
  void onlySundayAsWeeklyOff() {
    PlanningCalendar calendar =
        calendar(request(2027, 1, offs(DayOfWeek.SUNDAY), BreakStyle.LONG_WEEKENDS));

    assertThat(daysOffIn(calendar)).as("52 Sundays in 2027").isEqualTo(52);
    assertThat(calendar.kindOf(date(2027, 1, 2)))
        .as("Saturday is working")
        .isEqualTo(DayKind.WORKING);
    assertThat(calendar.kindOf(date(2027, 1, 3))).as("Sunday is off").isEqualTo(DayKind.WEEKLY_OFF);
  }

  @Test
  void currentYearWindowStartsTomorrowWithNoLeadingEdge() {
    // Today is Sunday 14 Mar 2027; Sat 13 and Sun 14 are over and must not be counted.
    PlanningCalendar calendar = PlanningCalendar.of(defaults(2027, 1), date(2027, 3, 14));

    assertThat(calendar.windowStart()).contains(date(2027, 3, 15));
    assertThat(calendar.horizon().get(0))
        .as("no leading edge in the current year: days up to and including today don't count")
        .isEqualTo(date(2027, 3, 15));
    assertThat(calendar.dayOff()[0]).as("Monday 15 Mar is a working day").isFalse();
  }

  @Test
  void windowIsEmptyOnThirtyFirstDecemberOfThePlanYear() {
    PlanningCalendar calendar = PlanningCalendar.of(defaults(2027, 1), date(2027, 12, 31));

    assertThat(calendar.windowStart()).as("no day is left to plan").isEmpty();
    assertThat(calendar.windowEnd()).isEmpty();
    assertThat(calendar.horizon()).isEmpty();
    assertThat(calendar.dayOff()).isEmpty();
  }

  @Test
  void planYearBeforeTheCurrentYearIsRejected() {
    assertThatThrownBy(() -> PlanningCalendar.of(defaults(2026, 1), date(2027, 3, 14)))
        .as("PRD Inputs: 'Current year or next year'")
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void holidaysAreSortedByDateAndIncludeDatesOutsideTheYear() {
    PlanningCalendar calendar =
        calendar(
            defaults(
                2027,
                1,
                holiday(2027, 9, 6, "Fixture Holiday 2"),
                holiday(2028, 1, 3, "Fixture Holiday 3"),
                holiday(2027, 6, 11, "Fixture Holiday 1")));

    assertThat(calendar.holidays())
        .extracting(HolidayDay::date)
        .as("ascending by date, edge holidays from the request included")
        .containsExactly(date(2027, 6, 11), date(2027, 9, 6), date(2028, 1, 3));
  }

  @Test
  void holidaysListIsEmptyWhenEveryHolidayIsUnticked() {
    // PRD edge case: "Every holiday unticked".
    PlanningCalendar calendar = calendar(defaults(2027, 12));

    assertThat(calendar.holidays()).isEmpty();
    assertThat(calendar.kindOf(date(2027, 6, 10))).isEqualTo(DayKind.WORKING);
  }

  @Test
  void horizonDatesAreConsecutive() {
    PlanningCalendar calendar = calendar(defaults(2028, 1, holiday(2027, 12, 31)));

    List<LocalDate> horizon = calendar.horizon();
    for (int i = 1; i < horizon.size(); i++) {
      assertThat(horizon.get(i)).isEqualTo(horizon.get(i - 1).plusDays(1));
    }
  }
}
