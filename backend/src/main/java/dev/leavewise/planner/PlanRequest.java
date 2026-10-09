package dev.leavewise.planner;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

/**
 * Validated planner inputs.
 *
 * @param year the year to plan; leave days are only placed inside it
 * @param leaveDays leave balance, 0 to 60
 * @param weeklyOffs weekly offs; at most 6 days, so at least one working day remains in the week
 * @param breakStyle sets the shortest break
 * @param holidays every known holiday, in any order, duplicates allowed. Dates outside {@code year}
 *     are used only to extend breaks across 1 January and 31 December (PRD, Year edges; ADR 0006).
 */
public record PlanRequest(
    int year,
    int leaveDays,
    Set<DayOfWeek> weeklyOffs,
    BreakStyle breakStyle,
    List<Holiday> holidays) {

  public static final int MAX_LEAVE_DAYS = 60;

  /**
   * @throws IllegalArgumentException if leaveDays is outside 0..60 or all seven days are weekly
   *     offs
   */
  public PlanRequest {
    if (leaveDays < 0 || leaveDays > MAX_LEAVE_DAYS) {
      throw new IllegalArgumentException("leaveDays must be 0 to 60");
    }
    weeklyOffs = Set.copyOf(weeklyOffs);
    if (weeklyOffs.size() == 7) {
      throw new IllegalArgumentException("at least one working day must remain in the week");
    }
    if (breakStyle == null) {
      throw new IllegalArgumentException("breakStyle is required");
    }
    holidays = List.copyOf(holidays);
  }
}
