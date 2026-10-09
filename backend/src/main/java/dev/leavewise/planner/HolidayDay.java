package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.List;

/**
 * A date that is a holiday, with every name given for it. Holidays on the same date are merged into
 * one HolidayDay; names are distinct and sorted, so the input order never changes the output.
 */
public record HolidayDay(LocalDate date, List<String> names) {
  public HolidayDay {
    names = List.copyOf(names);
  }
}
