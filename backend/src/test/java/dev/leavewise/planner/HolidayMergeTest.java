package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** PRD: "The same holiday date added twice: Kept once, showing both names." */
class HolidayMergeTest {

  @Test
  void sameDateKeptOnceWithBothNames() {
    PlanRequest request =
        defaults(
            2027,
            1,
            holiday(2027, 6, 11, "Fixture Holiday B"),
            holiday(2027, 6, 11, "Fixture Holiday A"));

    PlanningCalendar calendar = PlanningCalendar.of(request, EARLY_TODAY);

    assertThat(calendar.holidays())
        .as("two holidays on one date merge into one HolidayDay")
        .containsExactly(
            new HolidayDay(
                date(2027, 6, 11), java.util.List.of("Fixture Holiday A", "Fixture Holiday B")));
  }

  @Test
  void namesAreDistinctAndSortedWhateverTheInputOrder() {
    PlanRequest request =
        defaults(
            2027,
            1,
            holiday(2027, 6, 11, "Fixture Holiday C"),
            holiday(2027, 6, 11, "Fixture Holiday A"),
            holiday(2027, 6, 11, "Fixture Holiday C"),
            holiday(2027, 6, 11, "Fixture Holiday B"));

    assertThat(PlanningCalendar.of(request, EARLY_TODAY).holidays().get(0).names())
        .as("names are distinct and sorted so input order can't change the output")
        .containsExactly("Fixture Holiday A", "Fixture Holiday B", "Fixture Holiday C");
  }

  @Test
  void duplicateDateCountsAsOneDayOff() {
    PlanningCalendar once =
        PlanningCalendar.of(defaults(2027, 1, holiday(2027, 6, 14)), EARLY_TODAY);
    PlanningCalendar twice =
        PlanningCalendar.of(
            defaults(
                2027,
                1,
                holiday(2027, 6, 14, "Fixture Holiday A"),
                holiday(2027, 6, 14, "Fixture Holiday B")),
            EARLY_TODAY);

    assertThat(twice.dayOff())
        .as("a date listed twice is still one day off")
        .containsExactly(once.dayOff());
    assertThat(twice.horizon()).isEqualTo(once.horizon());
  }

  @Test
  void planShowsBothNamesOnTheCalendarDayAndInTheBreak() {
    PlanRequest request =
        defaults(
            2027,
            1,
            holiday(2027, 6, 10, "Fixture Holiday B"),
            holiday(2027, 6, 10, "Fixture Holiday A"));

    Plan plan = new Planner(Fixtures.clockOn(EARLY_TODAY)).plan(request);

    assertThat(
            plan.days().stream()
                .filter(d -> d.date().equals(date(2027, 6, 10)))
                .findFirst()
                .orElseThrow()
                .holidayNames())
        .as("the calendar day lists both names")
        .containsExactly("Fixture Holiday A", "Fixture Holiday B");
    assertThat(plan.breaks()).hasSize(1);
    assertThat(plan.breaks().get(0).holidays())
        .as("the break lists the holiday once, with both names")
        .containsExactly(
            new HolidayDay(
                date(2027, 6, 10), java.util.List.of("Fixture Holiday A", "Fixture Holiday B")));
  }
}
