package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.OptionalInt;

/**
 * One date of the plan year, for the 12-month calendar.
 *
 * @param planned false for dates on or before today in the current year (outside the window)
 * @param breakIndex index into {@link Plan#breaks()} when the date is inside a break
 * @param freeLongWeekendIndex index into {@link Plan#freeLongWeekends()} when inside one
 */
public record CalendarDay(
    LocalDate date,
    DayKind kind,
    boolean planned,
    List<String> holidayNames,
    OptionalInt breakIndex,
    OptionalInt freeLongWeekendIndex) {
  public CalendarDay {
    holidayNames = List.copyOf(holidayNames);
  }
}
