package dev.leavewise.ics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Code review finding 2 (Phase 3): text from a user must never start a new line in the calendar.
 * Every CR must be part of a CRLF line ending, and control characters are dropped.
 */
class IcsInjectionTest {

  @Test
  void loneCarriageReturnIsEscapedNotWritten() {
    assertThat(IcsWriter.escape("Evil\rATTACH:http://x")).isEqualTo("Evil\\nATTACH:http://x");
  }

  @Test
  void everyLineBreakFormIsEscaped() {
    assertThat(IcsWriter.escape("a\r\nb\nc\rd e")).isEqualTo("a\\nb\\nc\\nd\\ne");
  }

  @Test
  void otherControlCharactersAreDropped() {
    assertThat(IcsWriter.escape("a\u0000b\u0007c\u007Fd\te")).isEqualTo("abcd e");
  }

  @Test
  void foldedOutputHasNoBareCarriageReturn() {
    String folded = IcsWriter.fold("SUMMARY:" + IcsWriter.escape("x\ry".repeat(40)));
    String withoutCrlf = folded.replace("\r\n", "");
    assertThat(withoutCrlf).doesNotContain("\r").doesNotContain("\n");
  }
}
