package dev.leavewise.holidays;

import dev.leavewise.planner.Holiday;
import java.util.List;

/**
 * One bundled holiday file: a year's list with its source (ADR 0007). {@code kind} is "government"
 * (the default) or "company" (the owner's company calendar, ADR 0007 amendment). {@code optional}
 * holds the floaters: listed, but days off only when the user ticks them.
 */
public record HolidayList(
    int year,
    String kind,
    String list,
    HolidaySource source,
    List<Holiday> holidays,
    List<Holiday> optional) {
  public HolidayList {
    holidays = List.copyOf(holidays);
    optional = List.copyOf(optional);
  }

  /** The holidays that are days off for everyone: every holiday except the floaters. */
  public List<Holiday> fixedHolidays() {
    return holidays.stream().filter(h -> !optional.contains(h)).toList();
  }
}
