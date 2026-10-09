package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Sanity checks of the oracle on its own (it uses no production code), so a wrong oracle is caught.
 */
class BruteForcePlannerTest {

  @Test
  void prdExampleHolidayThursdayPlusLeaveFridayMakesFourDays() {
    // PRD: "a holiday on Thursday plus 1 leave day on Friday makes a 4-day break, Thursday to
    // Sunday." Wed(W) Thu(holiday) Fri(W) Sat Sun Mon(W).
    Selection best = BruteForcePlanner.solve(Sequences.of("WOWOOW"), 1, 3);

    assertThat(best)
        .as("one leave on Friday joins the Thursday holiday and the weekend into 4 days")
        .isEqualTo(new Selection(List.of(2), List.of(new Run(1, 4)), 4));
  }

  @Test
  void budgetZeroGivesTheEmptySelection() {
    assertThat(BruteForcePlanner.solve(Sequences.of("WOOOW"), 0, 3))
        .as("no leave means no break, even when a free run of 3 days off exists")
        .isEqualTo(Selection.EMPTY);
  }

  @Test
  void budgetAboveWorkingDaysTakesEveryWorkingDay() {
    assertThat(BruteForcePlanner.solve(Sequences.of("WOWOOW"), 5, 3))
        .as(
            "with more leaves than working days, all 3 working days become leaves: 6 days in one break")
        .isEqualTo(new Selection(List.of(0, 2, 5), List.of(new Run(0, 5)), 6));
  }

  @Test
  void firstEarlierLeaveDateWinsAmongEqualPlans() {
    // "WWOWW" with 2 leaves: {0,1}, {1,3} and {3,4} all give 3 days; the earliest first date wins.
    assertThat(BruteForcePlanner.solve(Sequences.of("WWOWW"), 2, 3))
        .as(
            "equal days and equal leave counts: the plan whose first differing leave is earlier wins")
        .isEqualTo(new Selection(List.of(0, 1), List.of(new Run(0, 2)), 3));
  }

  @Test
  void runsShorterThanTheMinimumAreNotBreaks() {
    assertThat(BruteForcePlanner.solve(Sequences.of("WWOWW"), 1, 3))
        .as("one leave next to a single day off makes a 2-day run, below the 3-day minimum")
        .isEqualTo(Selection.EMPTY);
  }
}
