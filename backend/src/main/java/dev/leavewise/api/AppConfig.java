package dev.leavewise.api;

import dev.leavewise.holidays.HolidayRepository;
import dev.leavewise.planner.Planner;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

  @Bean
  Planner planner(Clock clock) {
    return new Planner(clock);
  }
}
