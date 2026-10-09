package dev.leavewise.planner;

import java.time.LocalDate;

/** Small lookups shared by the date-level tests. */
final class PlanAssertions {

  private PlanAssertions() {}

  static CalendarDay day(Plan plan, LocalDate date) {
    return plan.days().stream()
        .filter(d -> d.date().equals(date))
        .findFirst()
        .orElseThrow(() -> new AssertionError(date + " is not a date of the plan year"));
  }
}
