package dev.leavewise.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds the best selection of leave positions in a day sequence (ADR 0003). Works on positions
 * only, so it can be checked against a brute-force oracle on small random sequences (ADR 0005).
 *
 * <p>In a best plan every break is a maximal run of days off: it starts at the sequence start or
 * right after an untaken working day, ends at the sequence end or right before one, and every
 * working day inside it is a leave. So a plan is a set of non-overlapping candidate runs, and a
 * suffix dynamic programme over (position, leaves left) picks the best set under {@link
 * SelectionOrder}.
 */
public final class BreakOptimizer {

  private BreakOptimizer() {}

  /**
   * @param dayOff {@code dayOff[i]} is true for a weekly off or holiday; false for a working day,
   *     where a leave may be placed. Every working day in the sequence is eligible.
   * @param budget the most leave days to use, 0 or more
   * @param minBreakDays the shortest break, 1 or more
   * @return the best selection by {@link SelectionOrder}; {@link Selection#EMPTY} when no break is
   *     possible
   */
  public static Selection solve(boolean[] dayOff, int budget, int minBreakDays) {
    if (budget < 0 || minBreakDays < 1) {
      throw new IllegalArgumentException("budget must be >= 0 and minBreakDays >= 1");
    }
    int n = dayOff.length;
    if (n == 0 || budget == 0) {
      return Selection.EMPTY;
    }
    int[] workBefore = new int[n + 1]; // workBefore[i] = working days in positions 0..i-1
    for (int i = 0; i < n; i++) {
      workBefore[i + 1] = workBefore[i] + (dayOff[i] ? 0 : 1);
    }

    // best[p][b]: best plan for positions p.. with at most b leaves; best[n][*] is the empty plan.
    Chain[][] best = new Chain[n + 1][budget + 1];
    java.util.Arrays.fill(best[n], Chain.EMPTY);
    for (int p = n - 1; p >= 0; p--) {
      System.arraycopy(best[p + 1], 0, best[p], 0, budget + 1); // skip day p
      boolean canStart = p == 0 || !dayOff[p - 1];
      if (!canStart) {
        continue;
      }
      for (int j = p; j < n; j++) {
        int cost = workBefore[j + 1] - workBefore[p];
        if (cost > budget) {
          break;
        }
        boolean canEnd = j == n - 1 || !dayOff[j + 1];
        int length = j - p + 1;
        if (!canEnd || cost == 0 || length < minBreakDays) {
          continue;
        }
        int after = Math.min(j + 2, n); // day j+1 is a working day that stays untaken
        for (int b = cost; b <= budget; b++) {
          Chain rest = best[after][b - cost];
          Chain current = best[p][b];
          int days = length + rest.days;
          int leaves = cost + rest.leaves;
          // Equal totals: take the candidate. Every other option for (p, b) loses the PRD's
          // date tie-break: skipping p can't use the first working day at or after p, which
          // this candidate does, and an earlier-ending candidate at p leaves day j+1 untaken
          // where this one places a leave. So the latest candidate with equal totals has the
          // earliest leave dates (ADR 0003; the brute-force oracle compares dates and checks it).
          boolean better = days != current.days ? days > current.days : leaves <= current.leaves;
          if (better) {
            best[p][b] = new Chain(p, j, leavePositions(dayOff, p, j, cost), rest);
          }
        }
      }
    }
    return best[0][budget].toSelection();
  }

  private static int[] leavePositions(boolean[] dayOff, int start, int end, int count) {
    int[] positions = new int[count];
    int k = 0;
    for (int i = start; i <= end; i++) {
      if (!dayOff[i]) {
        positions[k++] = i;
      }
    }
    return positions;
  }

  /**
   * An immutable linked list of chosen breaks, shared between DP states so a transition doesn't
   * copy.
   */
  private static final class Chain {
    static final Chain EMPTY = new Chain();

    final int start;
    final int end;
    final int[] leavesHere;
    final Chain next;
    final int days;
    final int leaves;

    private Chain() {
      this.start = -1;
      this.end = -1;
      this.leavesHere = new int[0];
      this.next = null;
      this.days = 0;
      this.leaves = 0;
    }

    Chain(int start, int end, int[] leavesHere, Chain next) {
      this.start = start;
      this.end = end;
      this.leavesHere = leavesHere;
      this.next = next;
      this.days = end - start + 1 + next.days;
      this.leaves = leavesHere.length + next.leaves;
    }

    Selection toSelection() {
      List<Integer> leaves = new ArrayList<>();
      List<Run> breaks = new ArrayList<>();
      for (Chain c = this; c != EMPTY; c = c.next) {
        breaks.add(new Run(c.start, c.end));
        for (int position : c.leavesHere) {
          leaves.add(position);
        }
      }
      return new Selection(leaves, breaks, days);
    }
  }
}
