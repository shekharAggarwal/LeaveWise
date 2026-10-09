package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR 0003: a break is a maximal run of days off, so it can't begin right after a day off. Seen
 * from outside, every break {@code solve} returns must be bounded by working days that stay
 * untaken, or by the ends of the sequence.
 */
class CandidateBreaksTest {

  @Test
  void noStartAfterADayOff() {
    // A break that started at position 1 (right after the day off at 0) would be 4 days; the real
    // break includes position 0 and is 5 days. PRD Break: "days off in a row".
    boolean[] dayOff = Sequences.of("OOWOO");

    assertThat(BreakOptimizer.solve(dayOff, 1, 3))
        .as("the break must be the whole maximal run 0..4 (5 days), not a part of it")
        .isEqualTo(new Selection(List.of(2), List.of(new Run(0, 4)), 5));
  }

  @Test
  void breaksNeverShareADayOffOrTouch() {
    boolean[] dayOff = Sequences.of("OOWOOWOO");

    assertThat(BreakOptimizer.solve(dayOff, 2, 3))
        .as("two leaves close the gap between the runs: one 8-day break, not two overlapping ones")
        .isEqualTo(new Selection(List.of(2, 5), List.of(new Run(0, 7)), 8));
  }

  @Test
  void everyBreakIsMaximalOnRandomLookingSequences() {
    String[] sequences = {"OWOOWWOOOWOW", "WOOWOWWOOWOO", "OOWWOOWWOOWW"};
    for (String days : sequences) {
      boolean[] dayOff = Sequences.of(days);
      for (int budget = 1; budget <= 5; budget++) {
        Selection result = BreakOptimizer.solve(dayOff, budget, 3);
        List<Integer> leaves = result.leaves();

        for (Run run : result.breaks()) {
          boolean startOk =
              run.start() == 0 || (!dayOff[run.start() - 1] && !leaves.contains(run.start() - 1));
          boolean endOk =
              run.end() == dayOff.length - 1
                  || (!dayOff[run.end() + 1] && !leaves.contains(run.end() + 1));
          assertThat(startOk && endOk)
              .as("%s budget %d: break %s is not a maximal run of days off", days, budget, run)
              .isTrue();
        }
      }
    }
  }
}
