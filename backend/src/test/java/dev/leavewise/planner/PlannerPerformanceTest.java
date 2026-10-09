package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.EARLY_TODAY;
import static dev.leavewise.planner.Fixtures.holiday;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PRD Quality: "Speed: a full-year plan returns in under 1 second." */
class PlannerPerformanceTest {

  /** Twelve fixture holidays spread over 2027 (weekday-agnostic; names are invented). */
  private static final List<Holiday> HOLIDAYS =
      List.of(
          holiday(2027, 1, 26, "Fixture Holiday 1"),
          holiday(2027, 2, 17, "Fixture Holiday 2"),
          holiday(2027, 3, 12, "Fixture Holiday 3"),
          holiday(2027, 4, 6, "Fixture Holiday 4"),
          holiday(2027, 5, 14, "Fixture Holiday 5"),
          holiday(2027, 6, 9, "Fixture Holiday 6"),
          holiday(2027, 7, 21, "Fixture Holiday 7"),
          holiday(2027, 8, 11, "Fixture Holiday 8"),
          holiday(2027, 9, 8, "Fixture Holiday 9"),
          holiday(2027, 10, 13, "Fixture Holiday 10"),
          holiday(2027, 11, 3, "Fixture Holiday 11"),
          holiday(2027, 12, 22, "Fixture Holiday 12"));

  @Test
  void fullYearSixtyLeavesUnderOneSecond() {
    Planner planner = Fixtures.plannerOn(EARLY_TODAY);
    PlanRequest request =
        new PlanRequest(2027, 60, Fixtures.SAT_SUN, BreakStyle.LONG_WEEKENDS, HOLIDAYS);
    planner.plan(request); // warm-up: class loading and JIT, as a running server would have

    long start = System.nanoTime();
    Plan plan = planner.plan(request);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(elapsed)
        .as("PRD: 'a full-year plan returns in under 1 second' (60 leaves, 12 holidays)")
        .isLessThan(Duration.ofSeconds(1));
    assertThat(plan.leavesUsed()).as("sanity: the plan is not trivially empty").isGreaterThan(0);
  }

  @Test
  void bigTripsWithMaximumLeavesAlsoUnderOneSecond() {
    Planner planner = Fixtures.plannerOn(EARLY_TODAY);
    PlanRequest request =
        new PlanRequest(2027, 60, Fixtures.offs(), BreakStyle.BIG_TRIPS, HOLIDAYS);
    planner.plan(request);

    long start = System.nanoTime();
    planner.plan(request);
    Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

    assertThat(elapsed)
        .as("no weekly offs and a 9-day minimum is the widest search")
        .isLessThan(Duration.ofSeconds(1));
  }
}
