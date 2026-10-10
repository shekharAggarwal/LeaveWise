package dev.leavewise.holidays;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a pasted holiday list: one {@code YYYY-MM-DD Name} per line, up to 50 lines (docs/API.md).
 */
public final class HolidayPasteParser {

  public static final int MAX_LINES = 50;
  public static final int MAX_NAME_LENGTH = 80;

  private static final Pattern LINE = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})(?:\\s+(.*))?");

  private HolidayPasteParser() {}

  /** A parsed line. {@code line} is 1-based. */
  public record ParsedHoliday(int line, LocalDate date, String name) {}

  /** A rejected line with the reason shown to the user (wording in docs/API.md). */
  public record LineError(int line, String reason) {}

  public record Result(List<ParsedHoliday> holidays, List<LineError> errors) {
    public Result {
      holidays = List.copyOf(holidays);
      errors = List.copyOf(errors);
    }
  }

  /**
   * Blank lines are skipped but still count for line numbers; lines 51 and later are reported,
   * never loaded. The date and name are separated by whitespace; the name is trimmed.
   */
  public static Result parse(int year, String text) {
    List<ParsedHoliday> holidays = new ArrayList<>();
    List<LineError> errors = new ArrayList<>();
    String[] lines = text.split("\\R", -1);
    for (int i = 0; i < lines.length; i++) {
      int number = i + 1;
      String line = lines[i].strip();
      if (line.isEmpty()) {
        continue;
      }
      if (number > MAX_LINES) {
        errors.add(new LineError(number, "Only 50 lines can be pasted. This line was not loaded."));
        continue;
      }
      Matcher m = LINE.matcher(line);
      if (!m.matches()) {
        errors.add(
            new LineError(
                number, "Start the line with a date like " + year + "-01-26, then the name."));
        continue;
      }
      LocalDate date;
      try {
        date = LocalDate.parse(m.group(1));
      } catch (DateTimeException e) {
        errors.add(new LineError(number, m.group(1) + " is not a real date."));
        continue;
      }
      if (date.getYear() != year) {
        errors.add(new LineError(number, date + " is not in " + year + "."));
        continue;
      }
      String name = m.group(2) == null ? "" : m.group(2).strip();
      if (name.isEmpty()) {
        errors.add(new LineError(number, "Add a name after the date."));
      } else if (name.length() > MAX_NAME_LENGTH) {
        errors.add(new LineError(number, "Keep the name to 80 characters or fewer."));
      } else {
        holidays.add(new ParsedHoliday(number, date, name));
      }
    }
    return new Result(holidays, errors);
  }
}
