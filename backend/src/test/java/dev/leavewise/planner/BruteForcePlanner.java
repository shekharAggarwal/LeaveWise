package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

/**
 * The oracle (ADR 0005): tries every subset of working positions with at most {@code budget}
 * members and scores each with a literal reading of the PRD. It is written for obvious correctness,
 * not speed, and shares no code with the production algorithm.
 *
 * <p>It compares plans with its own {@link #isBetter} (a literal reading of the PRD tie rule)
 * rather than {@link SelectionOrder}, so a bug in the production comparator cannot hide on both
 * sides. {@code SelectionOrderTest} checks the two agree.
 */
final class BruteForcePlanner {

  private BruteForcePlanner() {}

  static Selection solve(boolean[] dayOff, int budget, int minBreakDays) {
    List<Integer> working = new ArrayList<>();
    for (int i = 0; i < dayOff.length; i++) {
      if (!dayOff[i]) {
        working.add(i);
      }
    }
    Search search = new Search(dayOff, working, budget, minBreakDays);
    search.visit(new ArrayList<>(), 0);
    Selection best = search.best;

    // PRD: "A leave day outside a break adds nothing", and fewer leave days win ties, so the best
    // subset must have none. Checked here, not filtered.
    for (int leave : best.leaves()) {
      boolean insideABreak =
          best.breaks().stream().anyMatch(r -> r.start() <= leave && leave <= r.end());
      assertThat(insideABreak)
          .as("oracle bug: best subset has a leave at position %d outside every break", leave)
          .isTrue();
    }
    return best;
  }

  /** PRD: more days inside breaks; then fewer leave days; then the first earlier leave date. */
  static boolean isBetter(Selection a, Selection b) {
    if (a.daysInBreaks() != b.daysInBreaks()) {
      return a.daysInBreaks() > b.daysInBreaks();
    }
    if (a.leaves().size() != b.leaves().size()) {
      return a.leaves().size() < b.leaves().size();
    }
    for (int i = 0; i < a.leaves().size(); i++) {
      if (!a.leaves().get(i).equals(b.leaves().get(i))) {
        return a.leaves().get(i) < b.leaves().get(i);
      }
    }
    return false;
  }

  /** Marks the leaves as days off, finds maximal runs of days off and keeps the qualifying ones. */
  static Selection score(boolean[] dayOff, List<Integer> leaves, int minBreakDays) {
    boolean[] off = dayOff.clone();
    boolean[] isLeave = new boolean[dayOff.length];
    for (int leave : leaves) {
      off[leave] = true;
      isLeave[leave] = true;
    }
    List<Run> breaks = new ArrayList<>();
    int days = 0;
    int i = 0;
    while (i < off.length) {
      if (!off[i]) {
        i++;
        continue;
      }
      int end = i;
      while (end + 1 < off.length && off[end + 1]) {
        end++;
      }
      boolean hasLeave = false;
      for (int k = i; k <= end; k++) {
        hasLeave |= isLeave[k];
      }
      int length = end - i + 1;
      if (hasLeave && length >= minBreakDays) {
        breaks.add(new Run(i, end));
        days += length;
      }
      i = end + 1;
    }
    return new Selection(leaves, breaks, days);
  }

  private static final class Search {
    private final boolean[] dayOff;
    private final List<Integer> working;
    private final int budget;
    private final int minBreakDays;
    Selection best;

    Search(boolean[] dayOff, List<Integer> working, int budget, int minBreakDays) {
      this.dayOff = dayOff;
      this.working = working;
      this.budget = budget;
      this.minBreakDays = minBreakDays;
    }

    /** Scores the current subset, then every larger subset that adds only later working days. */
    void visit(List<Integer> chosen, int nextCandidate) {
      Selection scored = score(dayOff, chosen, minBreakDays);
      if (best == null || isBetter(scored, best)) {
        best = scored;
      }
      if (chosen.size() == budget) {
        return;
      }
      for (int k = nextCandidate; k < working.size(); k++) {
        chosen.add(working.get(k));
        visit(chosen, k + 1);
        chosen.remove(chosen.size() - 1);
      }
    }
  }
}
