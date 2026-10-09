package dev.leavewise.planner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Brute-force answer for a whole request, for date-level tests. It builds its own horizon with
 * plain loops (not {@link PlanningCalendar}), then runs {@link BruteForcePlanner} on it. Only for
 * small cases: it throws when the search would be too large.
 */
final class DateOracle {

  record Span(LocalDate start, LocalDate end) {}

  record Answer(List<LocalDate> leaveDates, List<Span> breaks, int daysInBreaks) {}

  private static final double MAX_SUBSETS = 3_000_000;

  private DateOracle() {}

  static Answer solve(PlanRequest request, LocalDate today) {
    Set<LocalDate> holidayDates = new HashSet<>();
    request.holidays().forEach(h -> holidayDates.add(h.date()));
    LocalDate janFirst = LocalDate.of(request.year(), 1, 1);
    LocalDate windowStart = request.year() == today.getYear() ? today.plusDays(1) : janFirst;
    LocalDate windowEnd = LocalDate.of(request.year(), 12, 31);
    if (windowStart.isAfter(windowEnd)) {
      return new Answer(List.of(), List.of(), 0);
    }
    LocalDate first = windowStart;
    if (windowStart.equals(janFirst)) {
      while (isOff(first.minusDays(1), request.weeklyOffs(), holidayDates)) {
        first = first.minusDays(1);
      }
    }
    LocalDate last = windowEnd;
    while (isOff(last.plusDays(1), request.weeklyOffs(), holidayDates)) {
      last = last.plusDays(1);
    }
    List<LocalDate> horizon = new ArrayList<>();
    for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
      horizon.add(d);
    }
    boolean[] dayOff = new boolean[horizon.size()];
    int working = 0;
    for (int i = 0; i < dayOff.length; i++) {
      dayOff[i] = isOff(horizon.get(i), request.weeklyOffs(), holidayDates);
      if (!dayOff[i]) {
        working++;
      }
    }
    guardSize(working, request.leaveDays());
    Selection best =
        BruteForcePlanner.solve(dayOff, request.leaveDays(), request.breakStyle().minDays());
    List<LocalDate> leaves = best.leaves().stream().map(horizon::get).toList();
    List<Span> spans =
        best.breaks().stream()
            .map(r -> new Span(horizon.get(r.start()), horizon.get(r.end())))
            .toList();
    return new Answer(leaves, spans, best.daysInBreaks());
  }

  private static boolean isOff(LocalDate date, Set<DayOfWeek> weeklyOffs, Set<LocalDate> holidays) {
    return weeklyOffs.contains(date.getDayOfWeek()) || holidays.contains(date);
  }

  private static void guardSize(int working, int budget) {
    double subsets = 0;
    double term = 1;
    for (int k = 0; k <= Math.min(budget, working); k++) {
      subsets += term;
      term = term * (working - k) / (k + 1);
    }
    if (subsets > MAX_SUBSETS) {
      throw new IllegalStateException("brute force too large for a unit test: " + subsets);
    }
  }
}
