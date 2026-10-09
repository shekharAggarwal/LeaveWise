package dev.leavewise.planner;

/** The shortest break the planner will build (PRD, Inputs: Break style). */
public enum BreakStyle {
  LONG_WEEKENDS(3),
  SHORT_TRIPS(5),
  BIG_TRIPS(9);

  private final int minDays;

  BreakStyle(int minDays) {
    this.minDays = minDays;
  }

  /** Minimum number of days off in a row for a break: 3, 5 or 9. */
  public int minDays() {
    return minDays;
  }
}
