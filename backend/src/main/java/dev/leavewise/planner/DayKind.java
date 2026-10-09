package dev.leavewise.planner;

/** What a calendar day is. A holiday on a weekly off is HOLIDAY (it counts once). */
public enum DayKind {
  WORKING,
  WEEKLY_OFF,
  HOLIDAY,
  LEAVE
}
