package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * edge-case-hunter, Phase 3: a plan request that straddles midnight IST on 31 December. The
 * controller checks the year against one reading of the clock and the planner reads it again, so a
 * valid 2026 request becomes "cannot plan a past year" inside the planner. Its own context: the
 * clock returns 23:59:59.999 IST on 31 Dec 2026 for the first reading and 00:00 IST on 1 Jan 2027
 * for every later one.
 */
@SpringBootTest(
    properties = {
      "leavewise.holidays.location=classpath*:fixtures/holidays/india-*.json",
      "leavewise.holidays.allow-test-data=true",
      "leavewise.rate-limit.requests-per-minute=100000",
      "leavewise.rate-limit.burst=100000"
    })
@AutoConfigureMockMvc
@Import(NewYearMidnightApiTest.Config.class)
class NewYearMidnightApiTest {

  static final class SteppingClock extends Clock {
    final AtomicInteger readings = new AtomicInteger();
    private final Instant before = Instant.parse("2026-12-31T18:29:59.999Z");
    private final Instant after = Instant.parse("2026-12-31T18:30:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("Asia/Kolkata");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
      return readings.getAndIncrement() == 0 ? before : after;
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    SteppingClock steppingClock() {
      return new SteppingClock();
    }
  }

  @Autowired MockMvc mvc;
  @Autowired SteppingClock clock;

  @BeforeEach
  void resetClock() {
    clock.readings.set(0);
  }

  @Test
  void regressionPlanRequestAcrossNewYearMidnightIsNotAServerError() throws Exception {
    Reply r =
        new Reply(
            mvc.perform(
                    MockMvcRequestBuilders.post("/api/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            PlanJson.valid()
                                .with("year", "2026")
                                .json()
                                .getBytes(StandardCharsets.UTF_8)))
                .andReturn());

    // docs/API.md: 500 is for unexpected server errors; a year that stopped being plannable is
    // either the NO_DAYS_LEFT plan (200) or "Pick 2027 or 2028." (400), never "Something went
    // wrong on our side".
    assertThat(r.status()).as(r.body()).isIn(200, 400);
  }
}
