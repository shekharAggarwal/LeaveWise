package dev.leavewise.holidays;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Guards the real files under {@code src/main/resources/holidays} (ADR 0007). With no files yet it
 * passes; once the laptop step adds them, it checks each one with test data refused.
 */
class BundledHolidayFilesTest {

  private static final String PATTERN = "classpath*:holidays/india-*.json";
  private static final Pattern FILE_NAME = Pattern.compile("india-(\\d{4})\\.json");

  @Test
  void everyBundledFileLoadsWithTestDataRefused() throws IOException {
    HolidayRepository repository = HolidayRepository.load(PATTERN, false);

    Resource[] files = new PathMatchingResourcePatternResolver().getResources(PATTERN);
    for (Resource file : files) {
      Matcher name = FILE_NAME.matcher(file.getFilename());
      assertThat(name.matches()).as("file name %s is india-YYYY.json", file.getFilename()).isTrue();
      int year = Integer.parseInt(name.group(1));

      HolidayList list = repository.forYear(year).orElse(null);

      assertThat(list).as("%s should be loaded for %d", file.getFilename(), year).isNotNull();
      assertThat(list.source().url())
          .as("%s must name its official source over https", file.getFilename())
          .startsWith("https://");
      assertThat(list.holidays())
          .as("%s must hold at least one holiday", file.getFilename())
          .isNotEmpty();
    }
  }
}
