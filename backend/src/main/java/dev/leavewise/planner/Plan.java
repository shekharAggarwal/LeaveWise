package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The best plan for a request.
 *
 * @param planningFrom first day of the window; empty when no day is left to plan
 * @param planningTo last day of the window; empty when no day is left to plan
 * @param leavesAvailable the request's leave balance
 * @param breaks breaks in date order
 * @param freeLongWeekends free long weekends in date order, not counted in the totals
 * @param note the situation note, if any
 * @param days one entry per date of the plan year (365 or 366)
 */
public record Plan(
    int year,
    Optional<LocalDate> planningFrom,
    Optional<LocalDate> planningTo,
    BreakStyle breakStyle,
    int leavesAvailable,
    List<LeaveBreak> breaks,
    List<FreeLongWeekend> freeLongWeekends,
    Optional<Note> note,
    List<CalendarDay> days) {

  public Plan {
    breaks = List.copyOf(breaks);
    freeLongWeekends = List.copyOf(freeLongWeekends);
    days = List.copyOf(days);
  }

  /** All leave dates in the plan, ascending. */
  public List<LocalDate> leaveDates() {
    return breaks.stream().flatMap(b -> b.leaveDates().stream()).toList();
  }

  public int leavesUsed() {
    return breaks.stream().mapToInt(b -> b.leaveDates().size()).sum();
  }

  public int leavesUnused() {
    return leavesAvailable - leavesUsed();
  }

  /** Total days inside breaks: the planner's objective. */
  public int totalDaysOff() {
    return breaks.stream().mapToInt(LeaveBreak::days).sum();
  }

  public int breakCount() {
    return breaks.size();
  }
}
