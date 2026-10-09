package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * PRD acceptance item: "On 1,000+ random small calendars, the planner's result matches a
 * brute-force solver that tries every combination." Compares leaves, breaks and total, so ties are
 * checked too (ADR 0004, 0005).
 */
class PlannerOracleProperties {

  private static final int MAX_WORKING_DAYS = 16;

  /** A random calendar as text ({@code W}/{@code O}), a leave budget and a minimum break length. */
  record Case(String days, int budget, int minBreakDays) {
    boolean[] dayOff() {
      return Sequences.of(days);
    }

    @Override
    public String toString() {
      return days + " budget=" + budget + " min=" + minBreakDays;
    }
  }

  @Provide
  Arbitrary<Case> cases() {
    return Combinators.combine(
            Arbitraries.oneOf(randomDays(), weeklyPatternDays()),
            Arbitraries.integers().between(0, 8),
            Arbitraries.of(3, 5, 9))
        .as(Case::new);
  }

  /** Each day off with a random density from 10% to 90%. */
  private Arbitrary<String> randomDays() {
    return Arbitraries.integers()
        .between(1, 21)
        .flatMap(
            length ->
                Arbitraries.integers()
                    .between(10, 90)
                    .flatMap(
                        density ->
                            Arbitraries.integers()
                                .between(0, 99)
                                .list()
                                .ofSize(length)
                                .map(
                                    rolls ->
                                        capWorkingDays(
                                            rolls.stream().map(r -> r < density).toList()))));
  }

  /** A week-shaped calendar (some days of every 7 off) with a few extra random days off. */
  private Arbitrary<String> weeklyPatternDays() {
    return Arbitraries.integers()
        .between(1, 21)
        .flatMap(
            length ->
                Arbitraries.integers()
                    .between(0, 126)
                    .flatMap(
                        offMask ->
                            Arbitraries.integers()
                                .between(0, 6)
                                .flatMap(
                                    start ->
                                        Arbitraries.integers()
                                            .between(0, 99)
                                            .list()
                                            .ofSize(length)
                                            .map(
                                                rolls -> {
                                                  java.util.ArrayList<Boolean> off =
                                                      new java.util.ArrayList<>();
                                                  for (int i = 0; i < length; i++) {
                                                    boolean weeklyOff =
                                                        (offMask & (1 << ((i + start) % 7))) != 0;
                                                    off.add(weeklyOff || rolls.get(i) < 8);
                                                  }
                                                  return capWorkingDays(off);
                                                }))));
  }

  /** Turns surplus working days into days off so 2^working stays small for the oracle. */
  private static String capWorkingDays(List<Boolean> dayOff) {
    StringBuilder text = new StringBuilder();
    int working = 0;
    for (boolean off : dayOff) {
      if (!off && working < MAX_WORKING_DAYS) {
        working++;
        text.append('W');
      } else {
        text.append('O');
      }
    }
    return text.toString();
  }

  @Property(tries = 1500)
  void matchesBruteForce(@ForAll("cases") Case c) {
    Selection expected = BruteForcePlanner.solve(c.dayOff(), c.budget(), c.minBreakDays());
    // Guards against a vacuous pass: a good share of the calendars must contain real breaks, and
    // some must have a budget above the number of working days (PRD: more leaves than working
    // days).
    Statistics.label("outcome")
        .collect(expected.breaks().isEmpty() ? "no break" : "has breaks")
        .coverage(coverage -> coverage.check("has breaks").percentage(p -> p >= 25.0));
    Statistics.label("budget")
        .collect(c.budget() > Sequences.workingDays(c.dayOff()) ? "above working days" : "within")
        .coverage(coverage -> coverage.check("above working days").percentage(p -> p >= 5.0));

    Selection actual = BreakOptimizer.solve(c.dayOff(), c.budget(), c.minBreakDays());

    assertThat(actual.daysInBreaks())
        .as("total days inside breaks differs from brute force for %s", c)
        .isEqualTo(expected.daysInBreaks());
    assertThat(actual.leaves())
        .as("leave positions differ from brute force (ties included) for %s", c)
        .isEqualTo(expected.leaves());
    assertThat(actual.breaks())
        .as("breaks differ from brute force for %s", c)
        .isEqualTo(expected.breaks());
  }
}
