package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The argument contract in {@link BreakOptimizer#solve}'s Javadoc: "budget the most leave days to
 * use, 0 or more" and "minBreakDays the shortest break, 1 or more". The break styles only use 3, 5
 * and 9, so the lower bound of 1 is otherwise never exercised.
 */
class BreakOptimizerContractTest {

  @Test
  void minimumBreakOfOneDayIsAccepted() {
    // W O W with one leave: either leave makes a 2-day run; the earlier leave wins the tie (PRD).
    boolean[] dayOff = Sequences.of("WOW");
    Selection expected = new Selection(List.of(0), List.of(new Run(0, 1)), 2);
    assertThat(BruteForcePlanner.solve(dayOff, 1, 1))
        .as("TEST BUG: expectation disagrees with the brute-force oracle")
        .isEqualTo(expected);

    assertThat(BreakOptimizer.solve(dayOff, 1, 1)).isEqualTo(expected);
  }

  @Test
  void negativeBudgetIsRejected() {
    assertThatThrownBy(() -> BreakOptimizer.solve(Sequences.of("WOW"), -1, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void minimumBreakBelowOneIsRejected() {
    assertThatThrownBy(() -> BreakOptimizer.solve(Sequences.of("WOW"), 1, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
