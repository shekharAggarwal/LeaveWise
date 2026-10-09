package dev.leavewise.planner;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Shared test fixtures. Holiday names are invented ("Fixture Holiday n") and the dates are chosen
 * for their weekday only; none is a real public holiday.
 */
final class Fixtures {

  static final ZoneId IST = ZoneId.of("Asia/Kolkata");

  /** A "today" in the year before 2027, so the whole of 2027 or 2028 is planned. */
  static final LocalDate EARLY_TODAY = LocalDate.of(2026, 10, 9);

  static final Set<DayOfWeek> SAT_SUN = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

  private Fixtures() {}

  static Clock clockOn(LocalDate today) {
    return Clock.fixed(today.atTime(10, 0).atZone(IST).toInstant(), IST);
  }

  static Planner plannerOn(LocalDate today) {
    return new Planner(clockOn(today));
  }

  static LocalDate date(int year, int month, int day) {
    return LocalDate.of(year, month, day);
  }

  static Holiday holiday(int year, int month, int day, String name) {
    return new Holiday(LocalDate.of(year, month, day), name);
  }

  static Holiday holiday(int year, int month, int day) {
    return holiday(year, month, day, "Fixture Holiday " + month + "-" + day);
  }

  static PlanRequest request(
      int year, int leaves, Set<DayOfWeek> weeklyOffs, BreakStyle style, Holiday... holidays) {
    return new PlanRequest(year, leaves, weeklyOffs, style, List.of(holidays));
  }

  /** Saturday and Sunday off, long weekends, as in the PRD defaults. */
  static PlanRequest defaults(int year, int leaves, Holiday... holidays) {
    return request(year, leaves, SAT_SUN, BreakStyle.LONG_WEEKENDS, holidays);
  }

  static Set<DayOfWeek> offs(DayOfWeek... days) {
    return days.length == 0 ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(Arrays.asList(days));
  }
}
