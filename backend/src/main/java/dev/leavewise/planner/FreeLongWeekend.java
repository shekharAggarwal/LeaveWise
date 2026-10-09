package dev.leavewise.planner;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 3 or more days off in a row that need no leave and are not part of a break (PRD, Free long
 * weekends). It includes at least one day of the planning window.
 */
public record FreeLongWeekend(LocalDate start, LocalDate end, List<HolidayDay> holidays) {
  public FreeLongWeekend {
    holidays = List.copyOf(holidays);
  }

  public int days() {
    return (int) ChronoUnit.DAYS.between(start, end) + 1;
  }
}
