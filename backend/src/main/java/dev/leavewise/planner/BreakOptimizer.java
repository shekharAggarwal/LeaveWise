package dev.leavewise.planner;

/**
 * Finds the best selection of leave positions in a day sequence (ADR 0003). Works on positions
 * only, so it can be checked against a brute-force oracle on small random sequences (ADR 0005).
 */
public final class BreakOptimizer {

  private BreakOptimizer() {}

  /**
   * @param dayOff {@code dayOff[i]} is true for a weekly off or holiday; false for a working day,
   *     where a leave may be placed. Every working day in the sequence is eligible.
   * @param budget the most leave days to use, 0 or more
   * @param minBreakDays the shortest break, 1 or more
   * @return the best selection by {@link SelectionOrder}; {@link Selection#EMPTY} when no break is
   *     possible
   */
  public static Selection solve(boolean[] dayOff, int budget, int minBreakDays) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
