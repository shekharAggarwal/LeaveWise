package dev.leavewise.holidays;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.holidays.HolidayPasteParser.LineError;
import dev.leavewise.holidays.HolidayPasteParser.ParsedHoliday;
import dev.leavewise.holidays.HolidayPasteParser.Result;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** docs/API.md, POST /api/holidays/parse: reasons are word for word from the table. */
class HolidayPasteParserTest {

  private static ParsedHoliday holiday(int line, int y, int m, int d, String name) {
    return new ParsedHoliday(line, LocalDate.of(y, m, d), name);
  }

  @Test
  void validLinesLoadWithTheirOneBasedLineNumbers() {
    Result result =
        HolidayPasteParser.parse(
            2027, "2027-03-04 Fixture Holiday 1\n2027-08-27 Fixture Holiday 2");

    assertThat(result.holidays())
        .containsExactly(
            holiday(1, 2027, 3, 4, "Fixture Holiday 1"),
            holiday(2, 2027, 8, 27, "Fixture Holiday 2"));
    assertThat(result.errors()).as("no line is bad").isEmpty();
  }

  @Test
  void emptyTextGivesNoHolidaysAndNoErrors() {
    Result result = HolidayPasteParser.parse(2027, "");

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void aLineNotStartingWithADateIsReportedWithTheStartReason() {
    Result result = HolidayPasteParser.parse(2027, "not a line");

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors())
        .containsExactly(
            new LineError(1, "Start the line with a date like 2027-01-26, then the name."));
  }

  @Test
  void theStartReasonShowsTheChosenYearInItsExample() {
    Result result = HolidayPasteParser.parse(2028, "hello");

    assertThat(result.errors())
        .containsExactly(
            new LineError(1, "Start the line with a date like 2028-01-26, then the name."));
  }

  @Test
  void aDateWithoutLeadingZerosDoesNotStartWithYyyyMmDd() {
    Result result = HolidayPasteParser.parse(2027, "2027-3-4 Fixture Holiday");

    assertThat(result.errors())
        .as("only the YYYY-MM-DD shape counts as a date")
        .containsExactly(
            new LineError(1, "Start the line with a date like 2027-01-26, then the name."));
  }

  @Test
  void aDateThatDoesNotExistIsReportedAsNotARealDate() {
    Result result = HolidayPasteParser.parse(2027, "2027-02-30 Fixture Holiday");

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors()).containsExactly(new LineError(1, "2027-02-30 is not a real date."));
  }

  @Test
  void aMonthOutOfRangeIsAlsoNotARealDate() {
    Result result = HolidayPasteParser.parse(2027, "2027-13-01 Fixture Holiday");

    assertThat(result.errors()).containsExactly(new LineError(1, "2027-13-01 is not a real date."));
  }

  @Test
  void the29thOfFebruaryIsRealInALeapYearAndNotInOtherYears() {
    Result leap = HolidayPasteParser.parse(2028, "2028-02-29 Fixture Holiday");
    Result notLeap = HolidayPasteParser.parse(2027, "2027-02-29 Fixture Holiday");

    assertThat(leap.holidays())
        .as("2028 is a leap year")
        .containsExactly(holiday(1, 2028, 2, 29, "Fixture Holiday"));
    assertThat(notLeap.errors())
        .as("2027 is not a leap year")
        .containsExactly(new LineError(1, "2027-02-29 is not a real date."));
  }

  @Test
  void aDateInAnotherYearIsReportedAsNotInTheChosenYear() {
    Result result = HolidayPasteParser.parse(2027, "2026-12-25 Fixture Holiday");

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors()).containsExactly(new LineError(1, "2026-12-25 is not in 2027."));
  }

  @Test
  void aDateWithNoNameAfterItIsReportedWithTheAddANameReason() {
    Result result = HolidayPasteParser.parse(2027, "2027-03-04");

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors()).containsExactly(new LineError(1, "Add a name after the date."));
  }

  @Test
  void aDateFollowedOnlyByWhitespaceHasNoName() {
    Result result = HolidayPasteParser.parse(2027, "2027-03-04   \t ");

    assertThat(result.errors()).containsExactly(new LineError(1, "Add a name after the date."));
  }

  @Test
  void aNameOf80CharactersIsAccepted() {
    String name = "x".repeat(80);

    Result result = HolidayPasteParser.parse(2027, "2027-03-04 " + name);

    assertThat(result.errors()).as("80 characters is the limit and still fine").isEmpty();
    assertThat(result.holidays()).containsExactly(holiday(1, 2027, 3, 4, name));
  }

  @Test
  void aNameOver80CharactersIsReportedWithTheKeepItShortReason() {
    Result result = HolidayPasteParser.parse(2027, "2027-03-04 " + "x".repeat(81));

    assertThat(result.holidays()).isEmpty();
    assertThat(result.errors())
        .containsExactly(new LineError(1, "Keep the name to 80 characters or fewer."));
  }

  @Test
  void blankLinesAreSkippedButStillCountForLineNumbers() {
    Result result =
        HolidayPasteParser.parse(
            2027, "2027-03-04 Fixture Holiday 1\n\n   \n2027-08-27 Fixture Holiday 2\nbad");

    assertThat(result.holidays())
        .containsExactly(
            holiday(1, 2027, 3, 4, "Fixture Holiday 1"),
            holiday(4, 2027, 8, 27, "Fixture Holiday 2"));
    assertThat(result.errors())
        .as("the bad line is line 5 because lines 2 and 3 were blank")
        .extracting(LineError::line)
        .containsExactly(5);
  }

  @Test
  void otherLinesStillLoadWhenSomeLinesAreBad() {
    String text =
        "2027-03-04 Example A\n2027-02-30 Bad date\n\n2026-12-25 Wrong year\nnot a line\n2027-08-27 B";

    Result result = HolidayPasteParser.parse(2027, text);

    assertThat(result.holidays())
        .containsExactly(holiday(1, 2027, 3, 4, "Example A"), holiday(6, 2027, 8, 27, "B"));
    assertThat(result.errors())
        .containsExactly(
            new LineError(2, "2027-02-30 is not a real date."),
            new LineError(4, "2026-12-25 is not in 2027."),
            new LineError(5, "Start the line with a date like 2027-01-26, then the name."));
  }

  @Test
  void leadingAndTrailingWhitespaceIsIgnoredAndTheNameIsTrimmed() {
    Result result = HolidayPasteParser.parse(2027, "  2027-03-04   Fixture Holiday   ");

    assertThat(result.errors()).isEmpty();
    assertThat(result.holidays()).containsExactly(holiday(1, 2027, 3, 4, "Fixture Holiday"));
  }

  @Test
  void windowsLineEndingsDoNotLeaveACarriageReturnInTheName() {
    Result result =
        HolidayPasteParser.parse(
            2027, "2027-03-04 Fixture Holiday 1\r\n2027-08-27 Fixture Holiday 2\r\n");

    assertThat(result.errors()).isEmpty();
    assertThat(result.holidays())
        .containsExactly(
            holiday(1, 2027, 3, 4, "Fixture Holiday 1"),
            holiday(2, 2027, 8, 27, "Fixture Holiday 2"));
  }

  @Test
  void aTabCanSeparateTheDateFromTheName() {
    Result result = HolidayPasteParser.parse(2027, "2027-03-04\tFixture Holiday");

    assertThat(result.errors()).isEmpty();
    assertThat(result.holidays()).containsExactly(holiday(1, 2027, 3, 4, "Fixture Holiday"));
  }

  @Test
  void theSameDateOnTwoLinesReturnsBothLinesBecauseMergingHappensLater() {
    Result result =
        HolidayPasteParser.parse(
            2027, "2027-03-04 Fixture Holiday A\n2027-03-04 Fixture Holiday B");

    assertThat(result.errors()).as("a repeated date is not an error here").isEmpty();
    assertThat(result.holidays())
        .containsExactly(
            holiday(1, 2027, 3, 4, "Fixture Holiday A"),
            holiday(2, 2027, 3, 4, "Fixture Holiday B"));
  }

  @Test
  void fiftyLinesAllLoad() {
    Result result = HolidayPasteParser.parse(2027, numberedLines(50));

    assertThat(result.holidays()).as("lines 1 to 50 are within the limit").hasSize(50);
    assertThat(result.errors()).isEmpty();
  }

  @Test
  void lineFiftyOneAndLaterAreReportedAndNotLoadedWhileTheFirstFiftyStillLoad() {
    Result result = HolidayPasteParser.parse(2027, numberedLines(52));

    assertThat(result.holidays())
        .as("lines 1 to 50 load")
        .extracting(ParsedHoliday::line)
        .containsExactlyElementsOf(range(1, 50));
    assertThat(result.errors())
        .containsExactly(
            new LineError(51, "Only 50 lines can be pasted. This line was not loaded."),
            new LineError(52, "Only 50 lines can be pasted. This line was not loaded."));
  }

  @Test
  void blankLinesCountTowardTheFiftyLineLimit() {
    String text = "\n".repeat(49) + "2027-03-04 Fixture Holiday 1\n2027-03-05 Fixture Holiday 2";

    Result result = HolidayPasteParser.parse(2027, text);

    assertThat(result.holidays())
        .as("line 50 loads, line 51 is past the limit")
        .containsExactly(holiday(50, 2027, 3, 4, "Fixture Holiday 1"));
    assertThat(result.errors())
        .containsExactly(
            new LineError(51, "Only 50 lines can be pasted. This line was not loaded."));
  }

  private static String numberedLines(int count) {
    List<String> lines = new ArrayList<>();
    LocalDate day = LocalDate.of(2027, 1, 1);
    for (int i = 0; i < count; i++) {
      lines.add(day.plusDays(i) + " Fixture Holiday " + (i + 1));
    }
    return String.join("\n", lines);
  }

  private static List<Integer> range(int from, int to) {
    List<Integer> numbers = new ArrayList<>();
    for (int i = from; i <= to; i++) {
      numbers.add(i);
    }
    return numbers;
  }
}
