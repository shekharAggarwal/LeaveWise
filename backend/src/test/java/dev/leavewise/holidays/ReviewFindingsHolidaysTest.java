package dev.leavewise.holidays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leavewise.planner.Holiday;
import dev.leavewise.planner.HolidayDay;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regression tests for the Phase 3 API code review (findings 7, 9 and 10). */
class ReviewFindingsHolidaysTest {

  @Test
  void pastedNameLengthCountsCharactersNotUtf16Units() {
    String eightyEmoji = "🎉".repeat(80);
    HolidayPasteParser.Result result = HolidayPasteParser.parse(2027, "2027-03-04 " + eightyEmoji);
    assertThat(result.errors()).isEmpty();
    assertThat(result.holidays()).hasSize(1);
  }

  @Test
  void pastedNameWithAControlCharacterIsReported() {
    HolidayPasteParser.Result result = HolidayPasteParser.parse(2027, "2027-03-04 Bad\u0007name");
    assertThat(result.errors())
        .extracting(HolidayPasteParser.LineError::reason)
        .containsExactly("Holiday names can't contain line breaks or control characters.");
  }

  @Test
  void retrievedOnMustBeAnIsoDate() {
    assertThatThrownBy(
            () ->
                HolidayRepository.load(
                    "classpath*:fixtures/repository/bad-retrieved-on/*.json", true))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("retrievedOn");
  }

  @Test
  void holidayDayMergeKeepsOneEntryPerDateWithSortedDistinctNames() {
    LocalDate d = LocalDate.of(2027, 8, 27);
    List<HolidayDay> merged =
        HolidayDay.merge(
            List.of(
                new Holiday(d, "Fixture B"),
                new Holiday(LocalDate.of(2027, 1, 5), "Fixture A"),
                new Holiday(d, "Fixture A"),
                new Holiday(d, "Fixture B")));
    assertThat(merged)
        .containsExactly(
            new HolidayDay(LocalDate.of(2027, 1, 5), List.of("Fixture A")),
            new HolidayDay(d, List.of("Fixture A", "Fixture B")));
  }
}
