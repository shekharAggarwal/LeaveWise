package dev.leavewise.ics;

import dev.leavewise.planner.HolidayDay;
import dev.leavewise.planner.LeaveBreak;
import dev.leavewise.planner.Plan;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Writes a plan as an RFC 5545 calendar (docs/API.md, POST /api/plan/ics): one all-day VEVENT per
 * break with {@code DTSTART;VALUE=DATE} (first day) and {@code DTEND;VALUE=DATE} (the day after the
 * last day), a summary like {@code Break: 4 days off (1 leave)}, a description listing the leave
 * dates ({@code Leave: Fri, 5 Mar 2027}) and holidays inside, {@code UID:<start>-<end>@leavewise},
 * and {@code DTSTAMP} = {@code now} in UTC. CRLF line endings, lines folded at 75 octets, text
 * escaped per RFC 5545 section 3.3.11.
 */
public final class IcsWriter {

  private static final String CRLF = "\r\n";
  private static final int MAX_OCTETS = 75;
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter INDIAN =
      DateTimeFormatter.ofPattern("EEE, d MMM uuuu", Locale.ENGLISH);

  private IcsWriter() {}

  public static String write(Plan plan, Instant now) {
    List<String> lines = new ArrayList<>();
    lines.add("BEGIN:VCALENDAR");
    lines.add("VERSION:2.0");
    lines.add("PRODID:-//LeaveWise//Leave plan//EN");
    lines.add("CALSCALE:GREGORIAN");
    for (LeaveBreak b : plan.breaks()) {
      lines.add("BEGIN:VEVENT");
      lines.add("UID:" + b.start().format(DATE) + "-" + b.end().format(DATE) + "@leavewise");
      lines.add("DTSTAMP:" + STAMP.format(now));
      lines.add("DTSTART;VALUE=DATE:" + b.start().format(DATE));
      lines.add("DTEND;VALUE=DATE:" + b.end().plusDays(1).format(DATE));
      lines.add("SUMMARY:" + escape(summary(b)));
      lines.add("DESCRIPTION:" + escape(description(b)));
      lines.add("TRANSP:TRANSPARENT");
      lines.add("END:VEVENT");
    }
    lines.add("END:VCALENDAR");
    StringBuilder out = new StringBuilder();
    for (String line : lines) {
      out.append(fold(line));
    }
    return out.toString();
  }

  private static String summary(LeaveBreak b) {
    int leaves = b.leaveDates().size();
    return "Break: " + b.days() + " days off (" + leaves + (leaves == 1 ? " leave)" : " leaves)");
  }

  private static String description(LeaveBreak b) {
    List<String> parts = new ArrayList<>();
    for (LocalDate leave : b.leaveDates()) {
      parts.add("Leave: " + leave.format(INDIAN));
    }
    for (HolidayDay h : b.holidays()) {
      parts.add("Holiday: " + h.date().format(INDIAN) + ", " + String.join(" / ", h.names()));
    }
    return String.join("\n", parts);
  }

  /** RFC 5545 section 3.3.11: escape backslash, semicolon, comma and newlines in TEXT values. */
  static String escape(String text) {
    return text.replace("\\", "\\\\")
        .replace(";", "\\;")
        .replace(",", "\\,")
        .replace("\r\n", "\\n")
        .replace("\n", "\\n");
  }

  /** RFC 5545 section 3.1: lines of at most 75 octets; continuation lines start with a space. */
  static String fold(String line) {
    StringBuilder out = new StringBuilder();
    int octets = 0;
    for (int i = 0; i < line.length(); ) {
      int cp = line.codePointAt(i);
      int size = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
      if (octets + size > MAX_OCTETS) {
        out.append(CRLF).append(' ');
        octets = 1;
      }
      out.appendCodePoint(cp);
      octets += size;
      i += Character.charCount(cp);
    }
    return out.append(CRLF).toString();
  }
}
