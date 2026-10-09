package dev.leavewise.planner;

/**
 * The one situation note a plan carries, if any (docs/API.md, notes). The API layer turns it into
 * words.
 *
 * @param leaves the number the message needs: leaves left over; otherwise 0
 */
public record Note(Code code, int leaves) {

  /** In precedence order: when several apply, the first wins. */
  public enum Code {
    NO_DAYS_LEFT,
    NO_LEAVE_USED,
    NO_BREAK_POSSIBLE,
    LEAVES_LEFT_OVER
  }
}
