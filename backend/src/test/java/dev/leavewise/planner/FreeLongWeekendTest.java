package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.date;
import static dev.leavewise.planner.Fixtures.defaults;
import static dev.leavewise.planner.Fixtures.holiday;
import static dev.leavewise.planner.Fixtures.plannerOn;
import static dev.leavewise.planner.Fixtures.request;
import static dev.leavewise.planner.PlanAssertions.day;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * PRD Free long weekends: "3 or more days off in a row that need no leave. They are shown, but
 * don't count toward the plan." Weekdays: 2027-06-11 Fri, 2027-09-06 Mon, 2027-06-15 Tue,
 * 2027-03-14 Sun, 2027-12-31 Fri, 2028-01-01 Sat, 2028-12-30 Sat.
 */
class FreeLongWeekendTest {

  private static final Holiday FRIDAY_HOLIDAY = holiday(2027, 6, 11, "Fixture Holiday 1");
  private static final Holiday MONDAY_HOLIDAY = holiday(2027, 9, 6, "Fixture Holiday 2");

  private static FreeLongWeekend freeLongWeekend(
      LocalDate start, LocalDate end, HolidayDay... holidays) {
    return new FreeLongWeekend(start, end, List.of(holidays));
  }

  @Test
  void listedButNotCounted() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 0, FRIDAY_HOLIDAY, MONDAY_HOLIDAY));

    assertThat(plan.freeLongWeekends())
        .as("Fri 11 Jun + weekend and Sat 4 Sep to Mon 6 Sep are 3 days off with no leave")
        .containsExactly(
            freeLongWeekend(
                date(2027, 6, 11),
                date(2027, 6, 13),
                new HolidayDay(date(2027, 6, 11), List.of("Fixture Holiday 1"))),
            freeLongWeekend(
                date(2027, 9, 4),
                date(2027, 9, 6),
                new HolidayDay(date(2027, 9, 6), List.of("Fixture Holiday 2"))));
    assertThat(plan.freeLongWeekends()).allMatch(f -> f.days() == 3);
    assertThat(plan.totalDaysOff()).as("PRD: they 'don't count toward the plan'").isZero();
    assertThat(plan.breakCount()).isZero();
    assertThat(plan.leavesUsed()).isZero();
  }

  @Test
  void aLongWeekendBesideALeaveIsNotCountedEither() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1, FRIDAY_HOLIDAY, MONDAY_HOLIDAY));

    assertThat(plan.totalDaysOff())
        .as("only the 4-day break counts (Thu 10 - Sun 13 Jun), not the free 3 days of 4-6 Sep")
        .isEqualTo(4);
    assertThat(plan.freeLongWeekends())
        .containsExactly(
            freeLongWeekend(
                date(2027, 9, 4),
                date(2027, 9, 6),
                new HolidayDay(date(2027, 9, 6), List.of("Fixture Holiday 2"))));
  }

  @Test
  void notListedWhenPartOfABreak() {
    // PRD Break: a run with a leave day in it is a break. Thu 10 Jun leave extends Fri 11 - Sun 13.
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 1, FRIDAY_HOLIDAY, MONDAY_HOLIDAY));

    assertThat(plan.breaks()).hasSize(1);
    assertThat(plan.breaks().get(0).start()).isEqualTo(date(2027, 6, 10));
    assertThat(plan.breaks().get(0).end()).isEqualTo(date(2027, 6, 13));
    assertThat(plan.freeLongWeekends())
        .as("a run that a leave extends is a break, not a free long weekend")
        .noneMatch(
            f -> !f.start().isAfter(date(2027, 6, 13)) && !f.end().isBefore(date(2027, 6, 10)));
  }

  @Test
  void plainWeekendsAreNotLongWeekends() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 0));

    assertThat(plan.freeLongWeekends()).as("Saturday and Sunday are only 2 days").isEmpty();
  }

  @Test
  void threeHolidaysInARowMakeOneEvenWithoutAWeekend() {
    Plan plan =
        plannerOn(EARLY_TODAY)
            .plan(
                defaults(
                    2027,
                    0,
                    holiday(2027, 6, 15, "Fixture Holiday 1"),
                    holiday(2027, 6, 16, "Fixture Holiday 2"),
                    holiday(2027, 6, 17, "Fixture Holiday 3")));

    assertThat(plan.freeLongWeekends()).hasSize(1);
    assertThat(plan.freeLongWeekends().get(0).start()).isEqualTo(date(2027, 6, 15));
    assertThat(plan.freeLongWeekends().get(0).end()).isEqualTo(date(2027, 6, 17));
    assertThat(plan.freeLongWeekends().get(0).holidays()).hasSize(3);
  }

  @Test
  void listedForEveryBreakStyle() {
    // PRD: 3 or more days; the break style sets the shortest break, not the shortest free weekend.
    Plan plan =
        plannerOn(EARLY_TODAY)
            .plan(request(2027, 0, Fixtures.SAT_SUN, BreakStyle.BIG_TRIPS, FRIDAY_HOLIDAY));

    assertThat(plan.freeLongWeekends()).hasSize(1);
  }

  @Test
  void calendarDaysPointAtTheirFreeLongWeekend() {
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 0, FRIDAY_HOLIDAY, MONDAY_HOLIDAY));

    assertThat(day(plan, date(2027, 6, 12)).freeLongWeekendIndex()).isEqualTo(OptionalInt.of(0));
    assertThat(day(plan, date(2027, 9, 5)).freeLongWeekendIndex()).isEqualTo(OptionalInt.of(1));
    assertThat(day(plan, date(2027, 6, 12)).breakIndex()).isEqualTo(OptionalInt.empty());
    assertThat(day(plan, date(2027, 6, 9)).freeLongWeekendIndex())
        .as("an ordinary working day belongs to no free long weekend")
        .isEqualTo(OptionalInt.empty());
  }

  @Test
  void mustIncludeADayOfTheWindowWhenItStartsBeforeJanuaryFirst() {
    // Fri 31 Dec 2027 holiday + Sat 1 and Sun 2 Jan 2028: includes 1 Jan, so it is listed.
    Plan plan =
        plannerOn(EARLY_TODAY).plan(defaults(2028, 0, holiday(2027, 12, 31, "Fixture Holiday 1")));

    assertThat(plan.freeLongWeekends())
        .containsExactly(
            freeLongWeekend(
                date(2027, 12, 31),
                date(2028, 1, 2),
                new HolidayDay(date(2027, 12, 31), List.of("Fixture Holiday 1"))));
  }

  @Test
  void mustIncludeADayOfTheWindowWhenItEndsAfterDecemberThirtyFirst() {
    // Sat 30 and Sun 31 Dec 2028 plus Mon 1 Jan 2029 holiday.
    Plan plan =
        plannerOn(EARLY_TODAY).plan(defaults(2028, 0, holiday(2029, 1, 1, "Fixture Holiday 1")));

    assertThat(plan.freeLongWeekends()).hasSize(1);
    assertThat(plan.freeLongWeekends().get(0).start()).isEqualTo(date(2028, 12, 30));
    assertThat(plan.freeLongWeekends().get(0).end()).isEqualTo(date(2029, 1, 1));
  }

  @Test
  void notListedWhenEveryDayIsOutsideTheWindow() {
    // Sat 1, Sun 2, Mon 3 Jan 2028 are 3 days off, but all after 31 Dec 2027.
    Plan plan = plannerOn(EARLY_TODAY).plan(defaults(2027, 0, holiday(2028, 1, 3)));

    assertThat(plan.freeLongWeekends())
        .as("PRD (docs): a free long weekend must include a day of the planning window")
        .isEmpty();
  }

  @Test
  void daysOffBeforeTheWindowDoNotCount() {
    // Today is Sun 14 Mar 2027. Mon 15 - Wed 17 are holidays: with the weekend already over, the
    // run inside the window is 15-17 (3 days), not 13-17.
    Plan plan =
        plannerOn(date(2027, 3, 14))
            .plan(
                defaults(
                    2027,
                    0,
                    holiday(2027, 3, 15, "Fixture Holiday 1"),
                    holiday(2027, 3, 16, "Fixture Holiday 2"),
                    holiday(2027, 3, 17, "Fixture Holiday 3")));

    assertThat(plan.freeLongWeekends()).hasSize(1);
    assertThat(plan.freeLongWeekends().get(0).start())
        .as("Sat 13 and Sun 14 Mar are not planned and must not be counted")
        .isEqualTo(date(2027, 3, 15));
    assertThat(plan.freeLongWeekends().get(0).days()).isEqualTo(3);
  }

  @Test
  void aShortRunInsideTheWindowIsNotMadeLongByDaysBeforeIt() {
    // Today is Sat 13 Mar 2027, Mon 15 Mar is a holiday: inside the window that is Sun-Mon, 2 days.
    Plan plan = plannerOn(date(2027, 3, 13)).plan(defaults(2027, 0, holiday(2027, 3, 15)));

    assertThat(plan.freeLongWeekends()).isEmpty();
  }

  @Test
  void aRunBeforeTheWindowIsNotListed() {
    // Fri 12 holiday, Sat 13, Sun 14 (today): all over.
    Plan plan = plannerOn(date(2027, 3, 14)).plan(defaults(2027, 0, holiday(2027, 3, 12)));

    assertThat(plan.freeLongWeekends()).isEmpty();
  }
}
