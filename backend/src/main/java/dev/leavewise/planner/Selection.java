package dev.leavewise.planner;

import java.util.List;

/**
 * A choice of leave positions in a day sequence and what it yields.
 *
 * @param leaves leave positions, ascending
 * @param breaks the breaks those leaves make (maximal runs of days off with at least one leave and
 *     at least the minimum length), ascending
 * @param daysInBreaks total days inside {@code breaks}: the planner's objective
 */
public record Selection(List<Integer> leaves, List<Run> breaks, int daysInBreaks) {
  public Selection {
    leaves = List.copyOf(leaves);
    breaks = List.copyOf(breaks);
  }

  public static final Selection EMPTY = new Selection(List.of(), List.of(), 0);
}
