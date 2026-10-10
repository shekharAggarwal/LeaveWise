package dev.leavewise.holidays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.leavewise.planner.Holiday;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * ADR 0007: the loader refuses a bundled file that isn't https on a gov.in or nic.in host, that has
 * a date outside its year, a blank name, a file-name year that differs from its year, a second file
 * for the same year, or test data when not allowed. All files here are fixtures marked {@code
 * "testData": true}.
 */
class HolidayRepositoryTest {

  private static final String FIXTURES = "classpath*:fixtures/repository/";

  private static HolidayRepository loadFixtures(String folder) {
    return HolidayRepository.load(FIXTURES + folder + "/india-*.json", true);
  }

  @Test
  void loadsAValidFolderAndReturnsEachYearsListWithItsSource() {
    HolidayRepository repository = loadFixtures("valid");

    HolidayList list = repository.forYear(2027).orElseThrow();

    assertThat(list.year()).as("year of the 2027 file").isEqualTo(2027);
    assertThat(list.list()).as("list name from the file").isEqualTo("Fixture list 2027");
    assertThat(list.source().publisher()).isEqualTo("Fixture Publisher");
    assertThat(list.source().title()).isEqualTo("Fixture memorandum 2027");
    assertThat(list.source().url()).isEqualTo("https://fixture.gov.in/list.pdf");
    assertThat(list.source().retrievedOn()).isEqualTo("2026-10-01");
    assertThat(list.holidays())
        .as("holidays exactly as written in the file")
        .containsExactlyInAnyOrder(
            new Holiday(LocalDate.of(2027, 3, 4), "Fixture Holiday 1"),
            new Holiday(LocalDate.of(2027, 8, 27), "Fixture Holiday 2"));
  }

  @Test
  void loadsOneListPerFileSoEveryYearInTheFolderIsAvailable() {
    HolidayRepository repository = loadFixtures("valid");

    assertThat(repository.forYear(2028))
        .as("india-2028.json is on a nic.in host, which is allowed")
        .isPresent();
    assertThat(repository.forYear(2028).orElseThrow().holidays())
        .containsExactly(new Holiday(LocalDate.of(2028, 2, 29), "Fixture Holiday 3"));
  }

  @Test
  void forYearIsEmptyWhenNoFileExistsForThatYear() {
    HolidayRepository repository = loadFixtures("valid");

    assertThat(repository.forYear(2029))
        .as("no india-2029.json was loaded, so the API reports available: false")
        .isEmpty();
  }

  @Test
  void aPatternMatchingNoFilesGivesAnEmptyRepository() {
    HolidayRepository repository =
        HolidayRepository.load(FIXTURES + "no-such-folder/india-*.json", false);

    assertThat(repository.forYear(2027))
        .as("with no bundled files the app starts with no list (ADR 0007, Consequences)")
        .isEmpty();
  }

  @Test
  void refusesAFileWhoseSourceIsNotOnAGovernmentHost() {
    assertThatThrownBy(() -> loadFixtures("bad-url-not-gov"))
        .as("source.url https://example.com is not a gov.in or nic.in host")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesASourceUrlThatIsHttpInsteadOfHttps() {
    assertThatThrownBy(() -> loadFixtures("bad-url-http"))
        .as("source.url must be https")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAHostThatOnlyContainsGovInInItsName() {
    assertThatThrownBy(() -> loadFixtures("bad-url-lookalike-host"))
        .as("gov.in.example.com is not a gov.in host; only hosts ending in .gov.in or .nic.in pass")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAHolidayDateOutsideTheFilesYear() {
    assertThatThrownBy(() -> loadFixtures("date-outside-year"))
        .as("a 2028 date in the 2027 file")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesABlankHolidayName() {
    assertThatThrownBy(() -> loadFixtures("blank-name"))
        .as("a name of only spaces is blank")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAFileWhoseNameYearDiffersFromItsYearField() {
    assertThatThrownBy(() -> loadFixtures("year-mismatch"))
        .as("india-2027.json says \"year\": 2028")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesTwoFilesForTheSameYear() {
    assertThatThrownBy(
            () -> HolidayRepository.load(FIXTURES + "duplicate-year/**/india-2027.json", true))
        .as("two india-2027.json files would make the year's list ambiguous")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesTestDataWhenNotAllowed() {
    assertThatThrownBy(() -> HolidayRepository.load(FIXTURES + "valid/india-*.json", false))
        .as("a file marked \"testData\": true must never load in production")
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesAFileThatIsNotValidJson() {
    assertThatThrownBy(() -> loadFixtures("malformed-json"))
        .as("a file that can't be read stops startup instead of being skipped")
        .isInstanceOf(IllegalStateException.class);
  }

  // ADR 0007 amendment (2026-10-10): the owner's company list is bundled for 2026.

  @Test
  void aCompanyListLoadsWithAnyHttpsSourceAndMarksItsFloatersOptional() {
    HolidayList list = loadFixtures("company").forYear(2026).orElseThrow();

    assertThat(list.kind()).isEqualTo("company");
    assertThat(list.source().url()).isEqualTo("https://fixture-company.example/holidays");
    assertThat(list.holidays())
        .containsExactly(
            new Holiday(LocalDate.of(2026, 1, 1), "Fixture Holiday 1"),
            new Holiday(LocalDate.of(2026, 1, 14), "Fixture Floater 1"));
    assertThat(list.optional())
        .as("floaters are listed but not days off unless the user ticks them")
        .containsExactly(new Holiday(LocalDate.of(2026, 1, 14), "Fixture Floater 1"));
    assertThat(list.fixedHolidays())
        .containsExactly(new Holiday(LocalDate.of(2026, 1, 1), "Fixture Holiday 1"));
  }

  @Test
  void aGovernmentListIsTheDefaultKindAndHasNoOptionalHolidays() {
    HolidayList list = loadFixtures("valid").forYear(2027).orElseThrow();

    assertThat(list.kind()).isEqualTo("government");
    assertThat(list.optional()).isEmpty();
  }

  @Test
  void aCompanyListStillNeedsAnHttpsSource() {
    assertThatThrownBy(() -> loadFixtures("company-http"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source.url must be https");
  }

  @Test
  void anUnknownKindIsRefused() {
    assertThatThrownBy(() -> loadFixtures("bad-kind"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("\"kind\" must be government or company");
  }
}
