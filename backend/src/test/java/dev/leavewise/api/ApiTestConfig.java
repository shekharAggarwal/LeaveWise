package dev.leavewise.api;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test wiring shared by the API and web tests: a clock the test can move (default 2026-10-09, so
 * the years are 2026 and 2027 and the default year is 2027) and a controller that always fails, to
 * check the 500 format.
 */
@TestConfiguration
public class ApiTestConfig {

  public static final String DEFAULT_NOW = "2026-10-09T06:00:00Z";
  public static final String BOOM_DETAIL = "secret-internal-detail";

  /** A clock in Asia/Kolkata whose instant tests can change. */
  public static final class MutableClock extends Clock {
    private static final ZoneId KOLKATA = ZoneId.of("Asia/Kolkata");
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse(DEFAULT_NOW));

    public void set(String isoInstant) {
      now.set(Instant.parse(isoInstant));
    }

    @Override
    public ZoneId getZone() {
      return KOLKATA;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now.get(), zone);
    }

    @Override
    public Instant instant() {
      return now.get();
    }
  }

  // A test-only endpoint that throws, for the 500 test. Spring 6+ only maps @Controller types, so
  // it
  // is a @RestController found by component scanning (test sources only; production has no such
  // path).
  @RestController
  public static class BoomController {
    @GetMapping("/api/test-boom")
    @ResponseBody
    public String boom() {
      throw new IllegalStateException(
          BOOM_DETAIL + " at com.example.Internal.method(Internal.java:42)");
    }
  }

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock();
  }
}
