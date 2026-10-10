package dev.leavewise.ics;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.planner.BreakStyle;
import dev.leavewise.planner.Holiday;
import dev.leavewise.planner.LeaveBreak;
import dev.leavewise.planner.Plan;
import dev.leavewise.planner.PlanRequest;
import dev.leavewise.planner.Planner;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * docs/API.md, POST /api/plan/ics, and PRD Results, Calendar file: "An .ics download with one
 * all-day event per break; each event's notes list its leave dates". Plans come from the real
 * planner with a fixed clock; names are invented fixtures.
 */
class IcsWriterTest {

  private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
  private static final Instant STAMP = Instant.parse("2026-10-09T06:00:00Z");
  private static final Planner PLANNER =
      new Planner(Clock.fixed(Instant.parse("2026-10-09T04:30:00Z"), IST));
  private static final Set<DayOfWeek> SAT_SUN = EnumSet.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
  private static final DateTimeFormatter BASIC = DateTimeFormatter.BASIC_ISO_DATE;

  /** Thu 4 Mar 2027 holiday plus one leave on Fri 5 Mar: one break, Thu 4 to Sun 7 Mar. */
  private static Plan singleBreakPlan(String holidayName) {
    return PLANNER.plan(
        new PlanRequest(
            2027,
            1,
            SAT_SUN,
            BreakStyle.LONG_WEEKENDS,
            List.of(new Holiday(LocalDate.of(2027, 3, 4), holidayName))));
  }

  /** Mon 8 to Wed 10 Mar 2027 are holidays; leaves Thu 11 and Fri 12 make Sat 6 to Sun 14 Mar. */
  private static Plan twoLeaveBreakPlan() {
    return PLANNER.plan(
        new PlanRequest(
            2027,
            2,
            SAT_SUN,
            BreakStyle.BIG_TRIPS,
            List.of(
                new Holiday(LocalDate.of(2027, 3, 8), "Fixture Holiday 1"),
                new Holiday(LocalDate.of(2027, 3, 9), "Fixture Holiday 2"),
                new Holiday(LocalDate.of(2027, 3, 10), "Fixture Holiday 3"))));
  }

  /** Two leaves, one holiday: the best plan is several separate breaks. */
  private static Plan manyBreaksPlan() {
    return PLANNER.plan(
        new PlanRequest(
            2027,
            2,
            SAT_SUN,
            BreakStyle.LONG_WEEKENDS,
            List.of(new Holiday(LocalDate.of(2027, 3, 4), "Fixture Holiday"))));
  }

  /** Thu 30 Dec 2027 holiday plus leave Fri 31 Dec: break Thu 30 Dec to Sun 2 Jan 2028. */
  private static Plan newYearCrossingPlan() {
    return PLANNER.plan(
        new PlanRequest(
            2027,
            1,
            SAT_SUN,
            BreakStyle.LONG_WEEKENDS,
            List.of(new Holiday(LocalDate.of(2027, 12, 30), "Fixture Holiday"))));
  }

  private static Plan noBreakPlan() {
    return PLANNER.plan(new PlanRequest(2027, 0, SAT_SUN, BreakStyle.LONG_WEEKENDS, List.of()));
  }

  /** The physical lines of the output, without CRLF. */
  private static List<String> physicalLines(String ics) {
    return new ArrayList<>(Arrays.asList(ics.split("\r\n", -1)));
  }

  /** RFC 5545 section 3.1: a line starting with a space or tab continues the previous one. */
  private static List<String> logicalLines(String ics) {
    List<String> lines = physicalLines(ics);
    if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
      lines.remove(lines.size() - 1);
    }
    List<String> logical = new ArrayList<>();
    for (String line : lines) {
      if ((line.startsWith(" ") || line.startsWith("\t")) && !logical.isEmpty()) {
        int last = logical.size() - 1;
        logical.set(last, logical.get(last) + line.substring(1));
      } else {
        logical.add(line);
      }
    }
    return logical;
  }

  /** The VEVENT blocks, each as its unfolded lines between BEGIN:VEVENT and END:VEVENT. */
  private static List<List<String>> events(String ics) {
    List<List<String>> events = new ArrayList<>();
    List<String> current = null;
    for (String line : logicalLines(ics)) {
      if (line.equals("BEGIN:VEVENT")) {
        current = new ArrayList<>();
      } else if (line.equals("END:VEVENT")) {
        events.add(current);
        current = null;
      } else if (current != null) {
        current.add(line);
      }
    }
    return events;
  }

  private static String valueOf(List<String> event, String prefix) {
    return event.stream()
        .filter(line -> line.startsWith(prefix))
        .findFirst()
        .map(line -> line.substring(prefix.length()))
        .orElse(null);
  }

  /** The raw (still escaped) DESCRIPTION value of the first event. */
  private static String rawDescription(String ics) {
    return valueOf(events(ics).get(0), "DESCRIPTION:");
  }

  /** Reverses RFC 5545 section 3.3.11 escaping. */
  private static String unescape(String raw) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < raw.length()) {
        char next = raw.charAt(++i);
        out.append(next == 'n' || next == 'N' ? '\n' : next);
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  @Test
  void theCalendarIsWrappedInBeginAndEndVcalendarWithTheStandardHeaders() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    List<String> lines = logicalLines(ics);
    assertThat(lines.get(0)).as("first line").isEqualTo("BEGIN:VCALENDAR");
    assertThat(lines.get(lines.size() - 1)).as("last line").isEqualTo("END:VCALENDAR");
    assertThat(lines)
        .contains("VERSION:2.0", "PRODID:-//LeaveWise//Leave plan//EN", "CALSCALE:GREGORIAN");
  }

  @Test
  void everyLineEndsWithCrlfAndNoBareNewlinesAppear() {
    String ics = IcsWriter.write(manyBreaksPlan(), STAMP);

    assertThat(ics).as("RFC 5545 ends the last line with CRLF too").endsWith("\r\n");
    assertThat(ics.replace("\r\n", ""))
        .as("no bare CR or LF may remain once every CRLF is removed")
        .doesNotContain("\r")
        .doesNotContain("\n");
  }

  @Test
  void thereIsOneVeventPerBreak() {
    Plan plan = manyBreaksPlan();
    assertThat(plan.breakCount()).as("precondition: the plan has several breaks").isGreaterThan(1);

    String ics = IcsWriter.write(plan, STAMP);

    assertThat(events(ics)).hasSize(plan.breakCount());
    assertThat(logicalLines(ics).stream().filter(l -> l.equals("BEGIN:VEVENT")).count())
        .isEqualTo(plan.breakCount());
  }

  @Test
  void eventsAreWrittenInTheOrderOfTheBreaks() {
    Plan plan = manyBreaksPlan();

    List<List<String>> events = events(IcsWriter.write(plan, STAMP));

    for (int i = 0; i < plan.breaks().size(); i++) {
      assertThat(valueOf(events.get(i), "DTSTART;VALUE=DATE:"))
          .as("event %d starts on the break's first day", i)
          .isEqualTo(plan.breaks().get(i).start().format(BASIC));
    }
  }

  @Test
  void dtstartIsTheFirstDayAndDtendIsTheDayAfterTheLastDay() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    List<String> event = events(ics).get(0);
    assertThat(valueOf(event, "DTSTART;VALUE=DATE:")).as("Thu 4 Mar 2027").isEqualTo("20270304");
    assertThat(valueOf(event, "DTEND;VALUE=DATE:"))
        .as("the break ends Sun 7 Mar; an all-day DTEND is exclusive, so Mon 8 Mar")
        .isEqualTo("20270308");
  }

  @Test
  void dtendAfterABreakCrossingNewYearRollsIntoTheNextYear() {
    Plan plan = newYearCrossingPlan();
    LeaveBreak crossing = plan.breaks().get(0);
    assertThat(crossing.end()).as("precondition").isEqualTo(LocalDate.of(2028, 1, 2));

    List<String> event = events(IcsWriter.write(plan, STAMP)).get(0);

    assertThat(valueOf(event, "DTSTART;VALUE=DATE:")).isEqualTo("20271230");
    assertThat(valueOf(event, "DTEND;VALUE=DATE:"))
        .as("last day Sun 2 Jan 2028, so DTEND is Mon 3 Jan 2028")
        .isEqualTo("20280103");
    assertThat(valueOf(event, "UID:")).isEqualTo("20271230-20280102@leavewise");
  }

  @Test
  void uidIsStartDashEndInIsoBasicDatesAtLeavewise() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    assertThat(valueOf(events(ics).get(0), "UID:")).isEqualTo("20270304-20270307@leavewise");
  }

  @Test
  void uidsAreTheSameWhenThePlanIsWrittenAgainSoAnImportUpdatesInsteadOfDuplicating() {
    Plan plan = manyBreaksPlan();

    List<String> first =
        events(IcsWriter.write(plan, STAMP)).stream().map(e -> valueOf(e, "UID:")).toList();
    List<String> second =
        events(IcsWriter.write(plan, STAMP.plusSeconds(3600))).stream()
            .map(e -> valueOf(e, "UID:"))
            .toList();

    assertThat(second).isEqualTo(first);
    assertThat(first).as("each break has its own UID").doesNotHaveDuplicates();
  }

  @Test
  void dtstampIsTheGivenInstantInUtcBasicFormat() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    assertThat(valueOf(events(ics).get(0), "DTSTAMP:")).isEqualTo("20261009T060000Z");
  }

  @Test
  void dtstampKeepsSecondsAndIsOnEveryEvent() {
    Plan plan = manyBreaksPlan();

    List<List<String>> events =
        events(IcsWriter.write(plan, Instant.parse("2026-12-31T23:59:59Z")));

    assertThat(events).isNotEmpty();
    for (List<String> event : events) {
      assertThat(valueOf(event, "DTSTAMP:")).isEqualTo("20261231T235959Z");
    }
  }

  @Test
  void twoWritesDifferOnlyInTheDtstampLine() {
    Plan plan = manyBreaksPlan();

    String first = IcsWriter.write(plan, STAMP);
    String second = IcsWriter.write(plan, STAMP.plusSeconds(86_400));

    assertThat(first.replaceAll("DTSTAMP:[0-9TZ]+", "DTSTAMP:X"))
        .as("API.md: two downloads differ only in that line")
        .isEqualTo(second.replaceAll("DTSTAMP:[0-9TZ]+", "DTSTAMP:X"));
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void summaryNamesTheDaysOffAndSingularLeave() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    assertThat(valueOf(events(ics).get(0), "SUMMARY:")).isEqualTo("Break: 4 days off (1 leave)");
  }

  @Test
  void summaryUsesPluralLeavesForTwoOrMore() {
    Plan plan = twoLeaveBreakPlan();
    assertThat(plan.breakCount()).as("precondition: one 9-day break").isEqualTo(1);
    assertThat(plan.breaks().get(0).leaveDates()).as("precondition").hasSize(2);

    String ics = IcsWriter.write(plan, STAMP);

    assertThat(valueOf(events(ics).get(0), "SUMMARY:")).isEqualTo("Break: 9 days off (2 leaves)");
    assertThat(valueOf(events(ics).get(0), "UID:")).isEqualTo("20270306-20270314@leavewise");
  }

  @Test
  void descriptionListsEachLeaveDateInIndianFormatWithTheYear() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture Holiday"), STAMP);

    assertThat(unescape(rawDescription(ics))).contains("Leave: Fri, 5 Mar 2027");
  }

  @Test
  void descriptionListsEveryLeaveDateOfABreakWithSeveral() {
    String ics = IcsWriter.write(twoLeaveBreakPlan(), STAMP);

    assertThat(unescape(rawDescription(ics)))
        .contains("Leave: Thu, 11 Mar 2027")
        .contains("Leave: Fri, 12 Mar 2027");
  }

  @Test
  void descriptionNamesTheHolidaysInsideTheBreak() {
    String ics = IcsWriter.write(twoLeaveBreakPlan(), STAMP);

    assertThat(unescape(rawDescription(ics)))
        .contains("Fixture Holiday 1")
        .contains("Fixture Holiday 2")
        .contains("Fixture Holiday 3");
  }

  @Test
  void commasSemicolonsAndBackslashesInNamesAreEscaped() {
    String ics = IcsWriter.write(singleBreakPlan("Fixture, Holiday; with \\ backslash"), STAMP);

    String raw = rawDescription(ics);
    assertThat(raw)
        .as("RFC 5545 3.3.11: comma, semicolon and backslash get a backslash")
        .contains("Fixture\\, Holiday\\; with \\\\ backslash")
        .doesNotContain("Fixture, Holiday");
    assertThat(unescape(raw)).contains("Fixture, Holiday; with \\ backslash");
  }

  @Test
  void aNewlineInANameIsWrittenAsBackslashN() {
    String ics = IcsWriter.write(singleBreakPlan("Line one\nLine two"), STAMP);

    assertThat(rawDescription(ics))
        .as("a real newline would break the line; it must be the two characters \\n")
        .contains("Line one\\nLine two");
    assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
  }

  @Test
  void lineBreaksBetweenDescriptionItemsAreEscapedToo() {
    String ics = IcsWriter.write(twoLeaveBreakPlan(), STAMP);

    assertThat(rawDescription(ics))
        .as("leave dates and holidays are separate items, joined by escaped newlines")
        .contains("\\n");
    assertThat(unescape(rawDescription(ics)).lines().count()).isGreaterThan(2);
  }

  @Test
  void noLineExceeds75OctetsEvenWithALongNonAsciiHolidayName() {
    String longName = "Ünïcödé Fëstïval 🎉 ".repeat(8);
    Plan plan = singleBreakPlan(longName);

    String ics = IcsWriter.write(plan, STAMP);

    List<String> lines = physicalLines(ics);
    for (String line : lines) {
      assertThat(line.getBytes(StandardCharsets.UTF_8).length)
          .as("octets in line \"%s\"", line)
          .isLessThanOrEqualTo(75);
      assertThat(new String(line.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8))
          .as("a multi-byte character was split across a fold")
          .isEqualTo(line);
    }
    assertThat(lines.stream().anyMatch(l -> l.startsWith(" ")))
        .as("the long description must have been folded")
        .isTrue();
    assertThat(unescape(rawDescription(ics)))
        .as("unfolding gives back the whole name")
        .contains(longName);
  }

  @Test
  void aPlanWithNoBreaksIsStillAValidCalendarWithNoEvents() {
    Plan plan = noBreakPlan();
    assertThat(plan.breaks()).as("precondition").isEmpty();

    String ics = IcsWriter.write(plan, STAMP);

    List<String> lines = logicalLines(ics);
    assertThat(lines.get(0)).isEqualTo("BEGIN:VCALENDAR");
    assertThat(lines)
        .contains("VERSION:2.0", "PRODID:-//LeaveWise//Leave plan//EN", "CALSCALE:GREGORIAN");
    assertThat(ics).endsWith("END:VCALENDAR\r\n").doesNotContain("VEVENT");
  }
}
