package dev.leavewise.api;

import dev.leavewise.holidays.HolidaySource;
import java.time.LocalDate;
import java.util.List;

/** JSON shapes of docs/API.md. Field names and nesting are the contract; change both together. */
final class ApiDtos {

  private ApiDtos() {}

  record BreakStyleOption(String id, String label, int minDays) {}

  record OptionsResponse(
      LocalDate today,
      String timeZone,
      List<Integer> years,
      int defaultYear,
      LeaveDaysOption leaveDays,
      List<String> defaultWeeklyOffs,
      List<BreakStyleOption> breakStyles,
      String defaultBreakStyle,
      int maxPastedLines) {}

  /**
   * {@code default} is a Java keyword, so the record component carries the JSON name explicitly.
   */
  record LeaveDaysOption(
      int min,
      int max,
      @com.fasterxml.jackson.annotation.JsonProperty("default") int defaultCount) {}

  record HolidayDayDto(LocalDate date, List<String> names) {}

  record HolidaysResponse(
      int year,
      boolean available,
      String list,
      HolidaySource source,
      String reminder,
      List<HolidayDayDto> holidays) {}

  record ParseRequest(Integer year, String text) {}

  record ParsedHolidayDto(int line, LocalDate date, String name) {}

  record LineErrorDto(int line, String reason) {}

  record ParseResponse(List<ParsedHolidayDto> holidays, List<LineErrorDto> errors) {}

  record Summary(
      int leavesAvailable, int leavesUsed, int leavesUnused, int totalDaysOff, int breakCount) {}

  record BreakDto(
      LocalDate start,
      LocalDate end,
      int days,
      List<LocalDate> leaveDates,
      List<HolidayDayDto> holidays,
      double daysOffPerLeave) {}

  record FreeLongWeekendDto(
      LocalDate start, LocalDate end, int days, List<HolidayDayDto> holidays) {}

  record NoteDto(String code, String message) {}

  record DayDto(
      LocalDate date,
      String kind,
      boolean planned,
      List<String> holidayNames,
      Integer breakIndex,
      Integer freeLongWeekendIndex) {}

  record PlanResponse(
      int year,
      LocalDate planningFrom,
      LocalDate planningTo,
      String breakStyle,
      int minBreakDays,
      Summary summary,
      List<BreakDto> breaks,
      List<FreeLongWeekendDto> freeLongWeekends,
      List<NoteDto> notes,
      List<DayDto> days) {}
}
