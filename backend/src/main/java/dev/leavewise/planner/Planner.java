package dev.leavewise.planner;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/** Plans leave days for a request (PRD, Planning rules). "Today" comes from the injected clock. */
public final class Planner {

  /**
   * PRD, Free long weekends: "3 or more days off in a row that need no leave", whatever the break
   * style.
   */
  static final int FREE_LONG_WEEKEND_DAYS = 3;

  private final Clock clock;

  public Planner(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @throws IllegalArgumentException if the plan year is before the current year
   */
  public Plan plan(PlanRequest request) {
    return plan(request, LocalDate.now(clock));
  }

  /**
   * Plans as of {@code today}. A caller that has already checked the year against a reading of the
   * clock passes that same day, so a request that straddles midnight on 31 December is judged
   * against one "today", never two.
   *
   * @throws IllegalArgumentException if the plan year is before {@code today}'s year
   */
  public Plan plan(PlanRequest request, LocalDate today) {
    PlanningCalendar calendar = PlanningCalendar.of(request, today);
    List<LocalDate> horizon = calendar.horizon();
    boolean[] dayOff = calendar.dayOff();

    Selection selection =
        BreakOptimizer.solve(dayOff, request.leaveDays(), request.breakStyle().minDays());

    Set<LocalDate> leaveDates = new HashSet<>();
    List<LeaveBreak> breaks = new ArrayList<>();
    boolean[] off = dayOff.clone();
    for (int position : selection.leaves()) {
      leaveDates.add(horizon.get(position));
      off[position] = true;
    }
    for (Run run : selection.breaks()) {
      List<LocalDate> leavesInRun = new ArrayList<>();
      for (int i = run.start(); i <= run.end(); i++) {
        if (!dayOff[i]) {
          leavesInRun.add(horizon.get(i));
        }
      }
      breaks.add(
          new LeaveBreak(
              horizon.get(run.start()),
              horizon.get(run.end()),
              leavesInRun,
              holidaysIn(calendar, horizon, run)));
    }

    List<FreeLongWeekend> freeLongWeekends = new ArrayList<>();
    int i = 0;
    while (i < off.length) {
      if (!off[i]) {
        i++;
        continue;
      }
      int start = i;
      boolean hasLeave = false;
      boolean touchesWindow = false;
      while (i < off.length && off[i]) {
        hasLeave |= leaveDates.contains(horizon.get(i));
        touchesWindow |= calendar.inWindow(horizon.get(i));
        i++;
      }
      Run run = new Run(start, i - 1);
      if (!hasLeave && touchesWindow && run.length() >= FREE_LONG_WEEKEND_DAYS) {
        freeLongWeekends.add(
            new FreeLongWeekend(
                horizon.get(run.start()),
                horizon.get(run.end()),
                holidaysIn(calendar, horizon, run)));
      }
    }

    Optional<Note> note = note(calendar, request.leaveDays(), selection.leaves().size(), breaks);

    List<CalendarDay> days =
        calendarDays(request.year(), calendar, leaveDates, breaks, freeLongWeekends);
    return new Plan(
        request.year(),
        calendar.windowStart(),
        calendar.windowEnd(),
        request.breakStyle(),
        request.leaveDays(),
        breaks,
        freeLongWeekends,
        note,
        days);
  }

  private static List<HolidayDay> holidaysIn(
      PlanningCalendar calendar, List<LocalDate> horizon, Run run) {
    List<HolidayDay> found = new ArrayList<>();
    for (int i = run.start(); i <= run.end(); i++) {
      calendar.holidayOn(horizon.get(i)).ifPresent(found::add);
    }
    return found;
  }

  /**
   * The one note, in the precedence order of docs/API.md. Spare leaves with at least one break
   * always mean every working day in the window is already a leave: otherwise a spare leave on the
   * untaken working day next to a break would make it longer, so the plan would not be the best.
   */
  private static Optional<Note> note(
      PlanningCalendar calendar, int leaveDays, int leavesUsed, List<LeaveBreak> breaks) {
    if (calendar.windowStart().isEmpty()) {
      return Optional.of(new Note(Note.Code.NO_DAYS_LEFT, 0));
    }
    if (leaveDays == 0) {
      return Optional.of(new Note(Note.Code.NO_LEAVE_USED, 0));
    }
    if (breaks.isEmpty()) {
      return Optional.of(new Note(Note.Code.NO_BREAK_POSSIBLE, 0));
    }
    int unused = leaveDays - leavesUsed;
    if (unused > 0) {
      return Optional.of(new Note(Note.Code.LEAVES_LEFT_OVER, unused));
    }
    return Optional.empty();
  }

  private static List<CalendarDay> calendarDays(
      int year,
      PlanningCalendar calendar,
      Set<LocalDate> leaveDates,
      List<LeaveBreak> breaks,
      List<FreeLongWeekend> freeLongWeekends) {
    Map<LocalDate, Integer> breakIndex = new HashMap<>();
    for (int b = 0; b < breaks.size(); b++) {
      LeaveBreak lb = breaks.get(b);
      for (LocalDate d = lb.start(); !d.isAfter(lb.end()); d = d.plusDays(1)) {
        breakIndex.put(d, b);
      }
    }
    Map<LocalDate, Integer> weekendIndex = new HashMap<>();
    for (int w = 0; w < freeLongWeekends.size(); w++) {
      FreeLongWeekend fw = freeLongWeekends.get(w);
      for (LocalDate d = fw.start(); !d.isAfter(fw.end()); d = d.plusDays(1)) {
        weekendIndex.put(d, w);
      }
    }
    List<CalendarDay> days = new ArrayList<>();
    for (LocalDate d = LocalDate.of(year, 1, 1); d.getYear() == year; d = d.plusDays(1)) {
      DayKind kind = leaveDates.contains(d) ? DayKind.LEAVE : calendar.kindOf(d);
      List<String> names = calendar.holidayOn(d).map(HolidayDay::names).orElse(List.of());
      days.add(
          new CalendarDay(
              d,
              kind,
              calendar.inWindow(d),
              names,
              optional(breakIndex.get(d)),
              optional(weekendIndex.get(d))));
    }
    return days;
  }

  private static OptionalInt optional(Integer value) {
    return value == null ? OptionalInt.empty() : OptionalInt.of(value);
  }
}
