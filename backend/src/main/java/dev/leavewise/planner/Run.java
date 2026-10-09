package dev.leavewise.planner;

/** A run of consecutive positions {@code start..end} (both inclusive) in a day sequence. */
public record Run(int start, int end) {
  public Run {
    if (start < 0 || end < start) {
      throw new IllegalArgumentException("bad run " + start + ".." + end);
    }
  }

  public int length() {
    return end - start + 1;
  }
}
