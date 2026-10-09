package dev.leavewise.planner;

/** Test helper: day sequences written as strings, {@code O} = day off, {@code W} = working day. */
final class Sequences {

  private Sequences() {}

  static boolean[] of(String days) {
    boolean[] dayOff = new boolean[days.length()];
    for (int i = 0; i < days.length(); i++) {
      char c = days.charAt(i);
      if (c != 'O' && c != 'W') {
        throw new IllegalArgumentException("only O and W allowed: " + days);
      }
      dayOff[i] = c == 'O';
    }
    return dayOff;
  }

  static String render(boolean[] dayOff) {
    StringBuilder text = new StringBuilder();
    for (boolean off : dayOff) {
      text.append(off ? 'O' : 'W');
    }
    return text.toString();
  }

  static int workingDays(boolean[] dayOff) {
    int count = 0;
    for (boolean off : dayOff) {
      if (!off) {
        count++;
      }
    }
    return count;
  }
}
