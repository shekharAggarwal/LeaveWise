package dev.leavewise.planner;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * A break in the plan (PRD, Planning rules). {@code start} and {@code end} are inclusive and may
 * fall in the previous or next year at the year edges.
 *
 * @param leaveDates the leave days inside the break, ascending (at least one)
 * @param holidays the holidays inside the break, ascending
 */
public record LeaveBreak(
    LocalDate start, LocalDate end, List<LocalDate> leaveDates, List<HolidayDay> holidays) {
  public LeaveBreak {
    leaveDates = List.copyOf(leaveDates);
    holidays = List.copyOf(holidays);
  }

  /** Total days off in the break. */
  public int days() {
    return (int) ChronoUnit.DAYS.between(start, end) + 1;
  }

  /** Days off per leave day, rounded to one decimal. */
  public double daysOffPerLeave() {
    return Math.round(days() * 10.0 / leaveDates.size()) / 10.0;
  }
}
