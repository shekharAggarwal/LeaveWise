package dev.leavewise.planner;

import java.util.Comparator;

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
    throw new UnsupportedOperationException("not implemented yet");
  }
}
