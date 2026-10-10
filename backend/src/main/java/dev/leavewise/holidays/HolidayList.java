package dev.leavewise.holidays;

import dev.leavewise.planner.Holiday;
import java.util.List;

/** One bundled holiday file: a year's list with its source (ADR 0007). */
public record HolidayList(int year, String list, HolidaySource source, List<Holiday> holidays) {
  public HolidayList {
    holidays = List.copyOf(holidays);
  }
}
