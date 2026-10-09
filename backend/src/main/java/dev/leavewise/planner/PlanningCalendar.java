package dev.leavewise.planner;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

  /** Safety bound on edge runs; real runs end within a week or two of the last holiday. */
  private static final int MAX_EDGE_DAYS = 366;

  private final Set<DayOfWeek> weeklyOffs;
  private final Map<LocalDate, HolidayDay> holidaysByDate;
  private final Optional<LocalDate> windowStart;
  private final Optional<LocalDate> windowEnd;
  private final List<LocalDate> horizon;
  private final boolean[] dayOff;

  private PlanningCalendar(
      Set<DayOfWeek> weeklyOffs,
      Map<LocalDate, HolidayDay> holidaysByDate,
      Optional<LocalDate> windowStart,
      Optional<LocalDate> windowEnd,
      List<LocalDate> horizon) {
    this.weeklyOffs = weeklyOffs;
    this.holidaysByDate = holidaysByDate;
    this.windowStart = windowStart;
    this.windowEnd = windowEnd;
    this.horizon = List.copyOf(horizon);
    this.dayOff = new boolean[horizon.size()];
    for (int i = 0; i < dayOff.length; i++) {
      dayOff[i] = isOff(horizon.get(i));
    }
  }

  /**
   * @throws IllegalArgumentException if the plan year is before {@code today}'s year
   */
  public static PlanningCalendar of(PlanRequest request, LocalDate today) {
    int year = request.year();
    if (year < today.getYear()) {
      throw new IllegalArgumentException("cannot plan a past year: " + year);
    }
    Map<LocalDate, HolidayDay> holidays = mergeHolidays(request.holidays());
    Set<DayOfWeek> offs = Set.copyOf(request.weeklyOffs());
    LocalDate firstDay = LocalDate.of(year, 1, 1);
    LocalDate lastDay = LocalDate.of(year, 12, 31);
    LocalDate start = year == today.getYear() ? today.plusDays(1) : firstDay;
    if (start.isAfter(lastDay)) {
      return new PlanningCalendar(offs, holidays, Optional.empty(), Optional.empty(), List.of());
    }
    PlanningCalendar probe =
        new PlanningCalendar(offs, holidays, Optional.of(start), Optional.of(lastDay), List.of());

    // Leading edge only when the window starts on 1 January (ADR 0006).
    LocalDate horizonStart = start;
    if (start.equals(firstDay)) {
      for (int i = 0; i < MAX_EDGE_DAYS; i++) {
        LocalDate previous = horizonStart.minusDays(1);
        if (!probe.isOff(previous)) {
          break;
        }
        horizonStart = previous;
      }
    }
    LocalDate horizonEnd = lastDay;
    for (int i = 0; i < MAX_EDGE_DAYS && probe.isOff(horizonEnd.plusDays(1)); i++) {
      horizonEnd = horizonEnd.plusDays(1);
    }
    List<LocalDate> horizon = new ArrayList<>();
    for (LocalDate d = horizonStart; !d.isAfter(horizonEnd); d = d.plusDays(1)) {
      horizon.add(d);
    }
    return new PlanningCalendar(offs, holidays, Optional.of(start), Optional.of(lastDay), horizon);
  }

  private static Map<LocalDate, HolidayDay> mergeHolidays(List<Holiday> holidays) {
    Map<LocalDate, TreeSet<String>> names = new TreeMap<>();
    for (Holiday h : holidays) {
      names.computeIfAbsent(h.date(), d -> new TreeSet<>()).add(h.name());
    }
    Map<LocalDate, HolidayDay> merged = new TreeMap<>();
    names.forEach((date, n) -> merged.put(date, new HolidayDay(date, List.copyOf(n))));
    return merged;
  }

  private boolean isOff(LocalDate date) {
    return weeklyOffs.contains(date.getDayOfWeek()) || holidaysByDate.containsKey(date);
  }

  /** First day a leave may go on; empty when no day is left to plan. */
  public Optional<LocalDate> windowStart() {
    return windowStart;
  }

  /** Last day a leave may go on (31 December); empty when no day is left to plan. */
  public Optional<LocalDate> windowEnd() {
    return windowEnd;
  }

  /** Whether a leave may go on this date: inside the window. */
  public boolean inWindow(LocalDate date) {
    return windowStart.isPresent()
        && !date.isBefore(windowStart.get())
        && !date.isAfter(windowEnd.get());
  }

  /** Horizon days in order (empty when the window is empty). */
  public List<LocalDate> horizon() {
    return horizon;
  }

  /** {@code dayOff()[i]} tells whether {@code horizon().get(i)} is a weekly off or a holiday. */
  public boolean[] dayOff() {
    return dayOff.clone();
  }

  /** Kind of any date before leaves are placed: WORKING, WEEKLY_OFF or HOLIDAY. */
  public DayKind kindOf(LocalDate date) {
    if (holidaysByDate.containsKey(date)) {
      return DayKind.HOLIDAY;
    }
    return weeklyOffs.contains(date.getDayOfWeek()) ? DayKind.WEEKLY_OFF : DayKind.WORKING;
  }

  /** The merged holiday on this date, if any. */
  public Optional<HolidayDay> holidayOn(LocalDate date) {
    return Optional.ofNullable(holidaysByDate.get(date));
  }

  /** Every holiday date in the request, merged by date (names distinct and sorted), ascending. */
  public List<HolidayDay> holidays() {
    return List.copyOf(holidaysByDate.values());
  }
}
