package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A date that is a holiday, with every name given for it. Holidays on the same date are merged into
 * one HolidayDay; names are distinct and sorted, so the input order never changes the output.
 */
public record HolidayDay(LocalDate date, List<String> names) {
  public HolidayDay {
    names = List.copyOf(names);
  }

  /**
   * Merges holidays by date (PRD: "Kept once, showing both names"): one entry per date, ascending,
   * with distinct names sorted, so the input order never changes the result.
   */
  public static List<HolidayDay> merge(List<Holiday> holidays) {
    Map<LocalDate, TreeSet<String>> names = new TreeMap<>();
    for (Holiday h : holidays) {
      names.computeIfAbsent(h.date(), d -> new TreeSet<>()).add(h.name());
    }
    return names.entrySet().stream()
        .map(e -> new HolidayDay(e.getKey(), List.copyOf(e.getValue())))
        .toList();
  }
}
