package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * ADR 0004: the tie order, tested on its own so a bug here can't hide in both the planner and the
 * oracle. {@code compare(a, b) < 0} means {@code a} is the better plan.
 */
class SelectionOrderTest {

  private static final SelectionOrder ORDER = SelectionOrder.BEST_FIRST;

  /** A selection with the given total; the breaks list is irrelevant to the order. */
  private static Selection selection(int daysInBreaks, Integer... leaves) {
    return new Selection(List.of(leaves), List.of(), daysInBreaks);
  }

  @Test
  void moreDaysInBreaksWins() {
    Selection more = selection(6, 3, 4, 5, 6);
    Selection fewer = selection(5, 0);

    assertThat(ORDER.compare(more, fewer))
        .as(
            "PRD: the best plan has 'the most total days inside breaks', even if it uses more leaves")
        .isNegative();
    assertThat(ORDER.compare(fewer, more))
        .as("the comparison must be antisymmetric: the plan with fewer days is worse")
        .isPositive();
  }

  @Test
  void equalDaysFewerLeavesWins() {
    Selection oneLeave = selection(4, 9);
    Selection twoLeaves = selection(4, 1, 2);

    assertThat(ORDER.compare(oneLeave, twoLeaves))
        .as("PRD: 'the plan using fewer leave days wins' even when its dates are later")
        .isNegative();
    assertThat(ORDER.compare(twoLeaves, oneLeave)).as("antisymmetric").isPositive();
  }

  @Test
  void equalDaysAndLeavesFirstEarlierPositionWins() {
    Selection earlier = selection(5, 2, 7, 9);
    Selection later = selection(5, 2, 8, 9);

    assertThat(ORDER.compare(earlier, later))
        .as(
            "PRD: 'compare both plans' leave dates in order; the plan with the first earlier date wins'")
        .isNegative();
    assertThat(ORDER.compare(later, earlier)).as("antisymmetric").isPositive();
  }

  @Test
  void firstDifferingPositionDecidesNotTheLastOne() {
    Selection earlyFirst = selection(5, 1, 20);
    Selection lateFirst = selection(5, 2, 3);

    assertThat(ORDER.compare(earlyFirst, lateFirst))
        .as("the first position that differs decides: [1, 20] beats [2, 3] although 20 > 3")
        .isNegative();
  }

  @Test
  void equalSelectionsCompareAsZero() {
    assertThat(ORDER.compare(selection(5, 2, 7), selection(5, 2, 7)))
        .as("identical selections are tied")
        .isZero();
    assertThat(ORDER.compare(Selection.EMPTY, Selection.EMPTY))
        .as("EMPTY ties with EMPTY")
        .isZero();
  }

  @Test
  void anyPlanBeatsEmptyAndEmptyBeatsNothingBetter() {
    assertThat(ORDER.compare(selection(3, 0), Selection.EMPTY))
        .as("a plan with 3 days beats the empty plan with 0")
        .isNegative();
  }

  @Test
  void sortingPutsTheBestPlanFirst() {
    List<Selection> shuffled =
        new ArrayList<>(
            List.of(
                selection(4, 5),
                selection(5, 1, 2),
                selection(5, 3),
                selection(5, 1),
                Selection.EMPTY));
    Collections.reverse(shuffled);

    shuffled.sort(ORDER);

    assertThat(shuffled)
        .as("order: most days, then fewest leaves, then earliest first leave")
        .containsExactly(
            selection(5, 1), selection(5, 3), selection(5, 1, 2), selection(4, 5), Selection.EMPTY);
  }

  @Provide
  Arbitrary<Selection> selections() {
    return Arbitraries.integers()
        .between(0, 6)
        .flatMap(
            days ->
                Arbitraries.integers()
                    .between(0, 8)
                    .set()
                    .ofMaxSize(4)
                    .map(
                        leaves -> {
                          List<Integer> sorted = new ArrayList<>(leaves);
                          Collections.sort(sorted);
                          return new Selection(sorted, List.of(), days);
                        }));
  }

  @Property(tries = 500)
  void agreesWithTheOraclesTieRule(
      @ForAll("selections") Selection a, @ForAll("selections") Selection b) {
    int sign = Integer.signum(ORDER.compare(a, b));
    int expected = BruteForcePlanner.isBetter(a, b) ? -1 : BruteForcePlanner.isBetter(b, a) ? 1 : 0;

    assertThat(sign)
        .as("SelectionOrder and the oracle's literal PRD reading disagree on %s vs %s", a, b)
        .isEqualTo(expected);
  }
}
