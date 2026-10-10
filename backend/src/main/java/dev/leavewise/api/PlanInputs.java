package dev.leavewise.api;

import dev.leavewise.planner.BreakStyle;
import dev.leavewise.planner.Holiday;
import dev.leavewise.planner.PlanRequest;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;

/**
 * Validates a plan request body against docs/API.md and turns it into a planner request. The body
 * is read as a JSON tree so that {@code 12.5} and {@code "12"} are rejected rather than coerced.
 */
final class PlanInputs {

  static final String LEAVE_DAYS_MESSAGE = "Enter a whole number from 0 to 60."; // PRD
  static final String WORKING_DAY_MESSAGE = "Keep at least one working day in the week."; // PRD
  static final String BREAK_STYLE_MESSAGE =
      "Pick a break style: Long weekends, Short trips or Big trips.";
  static final String WEEKLY_OFFS_MESSAGE = "Weekly offs must be day names, like SATURDAY.";
  static final String NAME_LENGTH_MESSAGE = "Keep holiday names to 80 characters or fewer.";
  static final int MAX_HOLIDAYS = 100;
  static final int MAX_NAME_LENGTH = 80;

  private PlanInputs() {}

  /**
   * @throws InvalidInputException listing every problem found
   */
  static PlanRequest parse(JsonNode body, PlanYears years) {
    if (body == null || !body.isObject()) {
      throw new InvalidInputException("body", "Send the plan inputs as a JSON object.");
    }
    List<FieldProblem> problems = new ArrayList<>();

    JsonNode yearNode = body.path("year");
    int year = 0;
    if (yearNode.isMissingNode() || yearNode.isNull()) {
      problems.add(new FieldProblem("year", "Fill in year."));
    } else if (!isInt(yearNode) || !years.allows(yearNode.intValue())) {
      problems.add(new FieldProblem("year", years.pickMessage()));
    } else {
      year = yearNode.intValue();
    }

    JsonNode leaveNode = body.path("leaveDays");
    int leaveDays = 0;
    if (leaveNode.isMissingNode() || leaveNode.isNull()) {
      problems.add(new FieldProblem("leaveDays", "Fill in leaveDays."));
    } else if (!isInt(leaveNode)
        || leaveNode.intValue() < 0
        || leaveNode.intValue() > PlanRequest.MAX_LEAVE_DAYS) {
      problems.add(new FieldProblem("leaveDays", LEAVE_DAYS_MESSAGE));
    } else {
      leaveDays = leaveNode.intValue();
    }

    Set<DayOfWeek> weeklyOffs = EnumSet.noneOf(DayOfWeek.class);
    JsonNode offsNode = body.path("weeklyOffs");
    if (offsNode.isMissingNode() || offsNode.isNull()) {
      problems.add(new FieldProblem("weeklyOffs", "Fill in weeklyOffs."));
    } else if (!offsNode.isArray()) {
      problems.add(new FieldProblem("weeklyOffs", WEEKLY_OFFS_MESSAGE));
    } else {
      boolean bad = false;
      for (JsonNode day : offsNode) {
        DayOfWeek parsed = day.isString() ? dayOf(day.stringValue()) : null;
        if (parsed == null) {
          bad = true;
        } else {
          weeklyOffs.add(parsed);
        }
      }
      if (bad) {
        problems.add(new FieldProblem("weeklyOffs", WEEKLY_OFFS_MESSAGE));
      } else if (weeklyOffs.size() == 7) {
        problems.add(new FieldProblem("weeklyOffs", WORKING_DAY_MESSAGE));
      }
    }

    JsonNode styleNode = body.path("breakStyle");
    BreakStyle breakStyle = styleNode.isString() ? styleOf(styleNode.stringValue()) : null;
    if (breakStyle == null) {
      problems.add(new FieldProblem("breakStyle", BREAK_STYLE_MESSAGE));
    }

    List<Holiday> holidays = new ArrayList<>();
    JsonNode holidaysNode = body.path("holidays");
    if (holidaysNode.isMissingNode() || holidaysNode.isNull()) {
      problems.add(new FieldProblem("holidays", "Fill in holidays."));
    } else if (!holidaysNode.isArray()) {
      problems.add(new FieldProblem("holidays", "Send holidays as a list."));
    } else if (holidaysNode.size() > MAX_HOLIDAYS) {
      problems.add(new FieldProblem("holidays", "Use at most 100 holidays."));
    } else {
      int i = 0;
      for (JsonNode item : holidaysNode) {
        String field = "holidays[" + i++ + "]";
        Holiday holiday = holidayOf(item, field, year, problems);
        if (holiday != null) {
          holidays.add(holiday);
        }
      }
    }

    if (!problems.isEmpty()) {
      throw new InvalidInputException(problems);
    }
    return new PlanRequest(year, leaveDays, weeklyOffs, breakStyle, holidays);
  }

  private static Holiday holidayOf(
      JsonNode item, String field, int year, List<FieldProblem> problems) {
    JsonNode dateNode = item.path("date");
    String dateText = dateNode.isString() ? dateNode.stringValue() : dateNode.toString();
    LocalDate date = null;
    try {
      date = dateNode.isString() ? LocalDate.parse(dateText) : null;
    } catch (DateTimeException e) {
      date = null;
    }
    if (date == null || (year != 0 && date.getYear() != year)) {
      String shown = year == 0 ? "the chosen year" : String.valueOf(year);
      problems.add(
          new FieldProblem(field + ".date", "Holiday " + dateText + " is not in " + shown + "."));
      return null;
    }
    JsonNode nameNode = item.path("name");
    String name = nameNode.isString() ? nameNode.stringValue().strip() : "";
    if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
      problems.add(new FieldProblem(field + ".name", NAME_LENGTH_MESSAGE));
      return null;
    }
    return new Holiday(date, name);
  }

  private static boolean isInt(JsonNode node) {
    return node.isIntegralNumber() && node.canConvertToInt();
  }

  private static DayOfWeek dayOf(String text) {
    try {
      return DayOfWeek.valueOf(text);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static BreakStyle styleOf(String text) {
    try {
      return BreakStyle.valueOf(text);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
