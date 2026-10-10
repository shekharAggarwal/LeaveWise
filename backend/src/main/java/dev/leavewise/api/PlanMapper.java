package dev.leavewise.api;

import dev.leavewise.api.ApiDtos.BreakDto;
import dev.leavewise.api.ApiDtos.DayDto;
import dev.leavewise.api.ApiDtos.FreeLongWeekendDto;
import dev.leavewise.api.ApiDtos.HolidayDayDto;
import dev.leavewise.api.ApiDtos.NoteDto;
import dev.leavewise.api.ApiDtos.PlanResponse;
import dev.leavewise.api.ApiDtos.Summary;
import dev.leavewise.planner.BreakStyle;
import dev.leavewise.planner.CalendarDay;
import dev.leavewise.planner.HolidayDay;
import dev.leavewise.planner.Note;
import dev.leavewise.planner.Plan;
import java.util.List;
import java.util.OptionalInt;

/** Turns a planner {@link Plan} into the docs/API.md response, including the note's words. */
final class PlanMapper {

  private PlanMapper() {}

  static PlanResponse toResponse(Plan plan) {
    return new PlanResponse(
        plan.year(),
        plan.planningFrom().orElse(null),
        plan.planningTo().orElse(null),
        plan.breakStyle().name(),
        plan.breakStyle().minDays(),
        new Summary(
            plan.leavesAvailable(),
            plan.leavesUsed(),
            plan.leavesUnused(),
            plan.totalDaysOff(),
            plan.breakCount()),
        plan.breaks().stream()
            .map(
                b ->
                    new BreakDto(
                        b.start(),
                        b.end(),
                        b.days(),
                        b.leaveDates(),
                        holidays(b.holidays()),
                        b.daysOffPerLeave()))
            .toList(),
        plan.freeLongWeekends().stream()
            .map(w -> new FreeLongWeekendDto(w.start(), w.end(), w.days(), holidays(w.holidays())))
            .toList(),
        plan.note().map(n -> List.of(note(n, plan))).orElse(List.of()),
        plan.days().stream().map(PlanMapper::day).toList());
  }

  static List<HolidayDayDto> holidays(List<HolidayDay> days) {
    return days.stream().map(h -> new HolidayDayDto(h.date(), h.names())).toList();
  }

  private static DayDto day(CalendarDay d) {
    return new DayDto(
        d.date(),
        d.kind().name(),
        d.planned(),
        d.holidayNames(),
        boxed(d.breakIndex()),
        boxed(d.freeLongWeekendIndex()));
  }

  private static Integer boxed(OptionalInt value) {
    return value.isPresent() ? value.getAsInt() : null;
  }

  /** Note wording from docs/API.md (notes table). */
  static NoteDto note(Note note, Plan plan) {
    String message =
        switch (note.code()) {
          case NO_DAYS_LEFT ->
              "No days left to plan in " + plan.year() + ". Pick " + (plan.year() + 1) + ".";
          case NO_LEAVE_USED -> "No leave used. These are the long weekends you already get.";
          case NO_BREAK_POSSIBLE ->
              "No break of "
                  + plan.breakStyle().minDays()
                  + "+ days fits "
                  + leaves(plan.leavesAvailable())
                  + " with these holidays and weekly offs."
                  + (plan.breakStyle() == BreakStyle.LONG_WEEKENDS
                      ? ""
                      : " Try a shorter break style.");
          case LEAVES_LEFT_OVER ->
              leaves(note.leaves()) + " left over: every working day is already a leave day.";
        };
    return new NoteDto(note.code().name(), message);
  }

  private static String leaves(int n) {
    return n + (n == 1 ? " leave" : " leaves");
  }
}
