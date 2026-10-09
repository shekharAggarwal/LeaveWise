package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Hand-made day sequences: {@code W} is a working day, {@code O} a day off (weekly off or holiday).
 * Every expected value was worked out by hand and then confirmed with the brute-force oracle;
 * {@link #check} re-runs the oracle first, so a wrong hand calculation fails as "test bug" rather
 * than as a planner bug.
 */
class BreakOptimizerTest {

  private static Selection selection(int days, List<Integer> leaves, Run... breaks) {
    return new Selection(leaves, List.of(breaks), days);
  }

  private static void check(String days, int budget, int minBreakDays, Selection expected) {
    boolean[] dayOff = Sequences.of(days);
    assertThat(BruteForcePlanner.solve(dayOff, budget, minBreakDays))
        .as(
            "TEST BUG: hand-computed expectation disagrees with the brute-force oracle for %s",
            days)
        .isEqualTo(expected);

    assertThat(BreakOptimizer.solve(dayOff, budget, minBreakDays))
        .as("%s with %d leaves and a %d-day minimum", days, budget, minBreakDays)
        .isEqualTo(expected);
  }

  @Test
  void prdExampleHolidayThursdayLeaveFridayIsFourDays() {
    // PRD: "a holiday on Thursday plus 1 leave day on Friday makes a 4-day break, Thursday to
    // Sunday." Wed Thu(holiday) Fri Sat Sun Mon.
    check("WOWOOW", 1, 3, selection(4, List.of(2), new Run(1, 4)));
  }

  @Test
  void budgetZeroGivesEmpty() {
    // A free run of 3 days off exists, but PRD: a break must "include at least one leave day".
    check("WOOOW", 0, 3, Selection.EMPTY);
  }

  @Test
  void noBreakReachableGivesEmpty() {
    // One leave next to the single day off makes only 2 days; the minimum is 3.
    check("WWOWW", 1, 3, Selection.EMPTY);
    // A short trip needs 5 days: 1 leave and 2 days off make 3.
    check("WWWOO", 1, 5, Selection.EMPTY);
  }

  @Test
  void sequenceWithoutAnyWorkingDayGivesEmpty() {
    check("OOO", 1, 3, Selection.EMPTY);
  }

  @Test
  void runsExactlyAsLongAsTheMinimumAreBreaks() {
    check("WOO", 1, 3, selection(3, List.of(0), new Run(0, 2)));
    check("WOOOOW", 1, 5, selection(5, List.of(0), new Run(0, 4)));
  }

  @Test
  void runsOneShorterThanTheMinimumAreNotBreaks() {
    check("WOOOW", 1, 5, Selection.EMPTY);
    check("WOOOOW", 2, 9, Selection.EMPTY);
  }

  @Test
  void leaveNeverPlacedOutsideABreak() {
    // 9 working days and no day off: with 2 leaves no 3-day run can be built, so nothing is placed
    // (PRD: "A leave day outside a break adds nothing, so the planner never places one").
    check("WWWWWWWWW", 2, 3, Selection.EMPTY);
    check("WWWWWWWWW", 3, 3, selection(3, List.of(0, 1, 2), new Run(0, 2)));
    // Four leaves make one run of 4, not a 3-day break plus a stray leave.
    check("WWWWWWWWW", 4, 3, selection(4, List.of(0, 1, 2, 3), new Run(0, 3)));
  }

  @Test
  void everyLeaveOfTheResultLiesInsideOneOfItsBreaks() {
    String[] sequences = {"WWWWOOWWWW", "WOWOOWWOOWW", "OOWWWOOWWWOO", "WWWWOWOO"};
    for (String days : sequences) {
      for (int budget = 0; budget <= 6; budget++) {
        Selection result = BreakOptimizer.solve(Sequences.of(days), budget, 3);

        for (int leave : result.leaves()) {
          assertThat(result.breaks())
              .as("%s budget %d: leave at %d must be inside a break", days, budget, leave)
              .anyMatch(run -> run.start() <= leave && leave <= run.end());
        }
        assertThat(result.leaves().size())
            .as("%s budget %d: leaves used must not exceed the budget", days, budget)
            .isLessThanOrEqualTo(budget);
      }
    }
  }

  @Test
  void leavesNextToABreakExtendItInsteadOfBuildingASeparateOne() {
    // PRD problem: order matters. The best single break (leave 5: 4 days) is not part of the best
    // 2-leave plan, which adds the neighbouring working day too and gets 5 days from the same run.
    check("WWWWOWOO", 2, 3, selection(5, List.of(3, 5), new Run(3, 7)));
    check("WWWWOWOO", 3, 3, selection(6, List.of(2, 3, 5), new Run(2, 7)));
  }

  @Test
  void breakAtTheVeryStartOfTheSequence() {
    check("OOW", 1, 3, selection(3, List.of(2), new Run(0, 2)));
    check("OOWOO", 1, 3, selection(5, List.of(2), new Run(0, 4)));
  }

  @Test
  void breakAtTheVeryEndOfTheSequence() {
    check("WOO", 1, 3, selection(3, List.of(0), new Run(0, 2)));
    check("WWOO", 2, 3, selection(4, List.of(0, 1), new Run(0, 3)));
  }

  @Test
  void breakCoversTheWholeSequenceWhenEveryWorkingDayIsALeave() {
    check("WOOW", 2, 3, selection(4, List.of(0, 3), new Run(0, 3)));
  }

  @Test
  void twoBreaksSeparatedByExactlyOneUntakenWorkingDay() {
    // Run A = 0..2 (leave 0), position 3 stays working, run B = 4..6 (leave 4). Leaves {3, 4} would
    // also make 6 days as one run, but {0, 4} wins the tie on the earlier first leave date.
    check("WOOWWOOW", 2, 3, selection(6, List.of(0, 4), new Run(0, 2), new Run(4, 6)));
  }

  @Test
  void budgetLargerThanTheWorkingDaysTakesEveryWorkingDay() {
    check("WOWOOW", 5, 3, selection(6, List.of(0, 2, 5), new Run(0, 5)));
    check("WWWWOOWWWW", 10, 3, selection(10, List.of(0, 1, 2, 3, 6, 7, 8, 9), new Run(0, 9)));
  }

  @Test
  void equalDaysChooseTheEarliestLeaveDate() {
    // One leave at 1 (run 0..3) or at 4 (run 2..5): both give 4 days; the earlier one wins.
    check("OWOOWO", 1, 3, selection(4, List.of(1), new Run(0, 3)));
  }
}
