package dev.leavewise.api;

import dev.leavewise.holidays.HolidayPasteParser;
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
  private static final java.util.regex.Pattern ISO_DATE =
      java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");
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

    int year = 0;
    try {
      year = requireYear(body.path("year"), years);
    } catch (InvalidInputException e) {
      problems.addAll(e.problems());
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
        Holiday holiday = holidayOf(item, field, year, years, problems);
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

  /**
   * The {@code year} field of any request: a whole number, the current or next year.
   *
   * @throws InvalidInputException with "Fill in year." or the "Pick ... or ..." message
   */
  static int requireYear(JsonNode yearNode, PlanYears years) {
    if (yearNode.isMissingNode() || yearNode.isNull()) {
      throw new InvalidInputException("year", "Fill in year.");
    }
    if (!isInt(yearNode) || !years.allows(yearNode.intValue())) {
      throw new InvalidInputException("year", years.pickMessage());
    }
    return yearNode.intValue();
  }

  private static Holiday holidayOf(
      JsonNode item, String field, int year, PlanYears years, List<FieldProblem> problems) {
    JsonNode dateNode = item.path("date");
    if (!dateNode.isString() || dateNode.stringValue().isBlank()) {
      problems.add(new FieldProblem(field + ".date", "Fill in the holiday date."));
      return null;
    }
    String dateText = dateNode.stringValue().strip();
    LocalDate date;
    try {
      date = ISO_DATE.matcher(dateText).matches() ? LocalDate.parse(dateText) : null;
    } catch (DateTimeException e) {
      date = null;
    }
    int exampleYear = year == 0 ? years.current() : year;
    if (date == null) {
      problems.add(
          new FieldProblem(field + ".date", "Write holiday dates like " + exampleYear + "-01-26."));
      return null;
    }
    if (year != 0 && date.getYear() != year) {
      problems.add(
          new FieldProblem(field + ".date", "Holiday " + date + " is not in " + year + "."));
      return null;
    }
    JsonNode nameNode = item.path("name");
    String name = nameNode.isString() ? nameNode.stringValue().strip() : "";
    String nameProblem = nameProblem(name);
    if (nameProblem != null) {
      problems.add(new FieldProblem(field + ".name", nameProblem));
      return null;
    }
    return new Holiday(date, name);
  }

  /** Shared with the paste parser's rules: a name, at most 80 characters, no control characters. */
  static String nameProblem(String name) {
    if (name.isEmpty()) {
      return "Give each holiday a name.";
    }
    if (HolidayPasteParser.hasControlCharacter(name)) {
      return HolidayPasteParser.CONTROL_CHARACTER_MESSAGE;
    }
    if (name.codePointCount(0, name.length()) > MAX_NAME_LENGTH) {
      return NAME_LENGTH_MESSAGE;
    }
    return null;
  }

  /**
   * A whole number that fits an int. JSON has one number type, so {@code 60.0} and {@code 6e1} are
   * the whole number 60 (PRD: the error is for a value "not a whole number"); {@code 12.5} and
   * {@code "12"} are not.
   */
  private static boolean isInt(JsonNode node) {
    if (node.isIntegralNumber()) {
      return node.canConvertToInt();
    }
    if (!node.isFloatingPointNumber()) {
      return false;
    }
    double value = node.doubleValue();
    return value == Math.rint(value) && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
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
