package dev.leavewise.api;

import dev.leavewise.holidays.HolidayRepository;
import dev.leavewise.planner.Planner;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;

/** Wires the plain-Java planner and the holiday data into Spring. */
@Configuration
public class AppConfig {

  /** "Today" for every rule comes from this clock (ADR 0006); tests replace it with a fixed one. */
  public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

  @Bean
  Clock systemClock() {
    return Clock.system(ZONE);
  }

  @Bean
  HolidayRepository holidayRepository(
      @Value("${leavewise.holidays.location}") String location,
      @Value("${leavewise.holidays.allow-test-data:false}") boolean allowTestData) {
    return HolidayRepository.load(location, allowTestData);
  }

  /**
   * A body like {"year":2026,"year":2027} is rejected instead of silently keeping the last value,
   * so what the server plans is never ambiguous (security review 6).
   */
  @Bean
  JsonMapperBuilderCustomizer strictDuplicateKeys() {
    return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION);
  }

  @Bean
  Planner planner(Clock clock) {
    return new Planner(clock);
  }
}
