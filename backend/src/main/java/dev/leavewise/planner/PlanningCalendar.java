package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The days the planner works on (ADR 0006).
 *
 * <ul>
 *   <li>Window: 1 January to 31 December of the plan year, or from the day after {@code today} when
 *       the plan year is the current year. Empty when today is 31 December of the plan year. Leave
 *       days go only inside it.
 *   <li>Horizon: the window, plus the unbroken run of days off just before 1 January (only when the
 *       window starts on 1 January) and just after 31 December. Days off there come from weekly
 *       offs and from holidays in the request that fall outside the plan year.
 * </ul>
 */
public final class PlanningCalendar {

  private PlanningCalendar() {}

  /**
   * @throws IllegalArgumentException if the plan year is before {@code today}'s year
   */
  public static PlanningCalendar of(PlanRequest request, LocalDate today) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** First day a leave may go on; empty when no day is left to plan. */
  public Optional<LocalDate> windowStart() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Last day a leave may go on (31 December); empty when no day is left to plan. */
  public Optional<LocalDate> windowEnd() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Horizon days in order (empty when the window is empty). */
  public List<LocalDate> horizon() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** {@code dayOff()[i]} tells whether {@code horizon().get(i)} is a weekly off or a holiday. */
  public boolean[] dayOff() {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Kind of any date before leaves are placed: WORKING, WEEKLY_OFF or HOLIDAY. */
  public DayKind kindOf(LocalDate date) {
    throw new UnsupportedOperationException("not implemented yet");
  }

  /** Every holiday date in the request, merged by date (names distinct and sorted), ascending. */
  public List<HolidayDay> holidays() {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
