package dev.leavewise.planner;

import java.util.Comparator;
import java.util.List;

/**
 * The total order on plans from the PRD's tie rule (ADR 0004). {@code compare(a, b) < 0} means
 * {@code a} is the better plan: more days in breaks; then fewer leave days; then, comparing the
 * ascending leave positions one by one, the first earlier position wins.
 */
public final class SelectionOrder implements Comparator<Selection> {

  public static final SelectionOrder BEST_FIRST = new SelectionOrder();

  private SelectionOrder() {}

  @Override
  public int compare(Selection a, Selection b) {
    if (a.daysInBreaks() != b.daysInBreaks()) {
      return Integer.compare(b.daysInBreaks(), a.daysInBreaks());
    }
    if (a.leaves().size() != b.leaves().size()) {
      return Integer.compare(a.leaves().size(), b.leaves().size());
    }
    return compareLeaves(a.leaves(), b.leaves());
  }

  /**
   * Lexicographic order on equal-length ascending position lists: the first earlier position wins.
   */
  static int compareLeaves(List<Integer> a, List<Integer> b) {
    for (int i = 0; i < a.size(); i++) {
      int c = Integer.compare(a.get(i), b.get(i));
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }
}
