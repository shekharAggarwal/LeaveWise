package dev.leavewise.planner;

import static dev.leavewise.planner.Fixtures.date;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * PRD, Results, Break list: "days off per leave". docs/API.md: {@code daysOffPerLeave} is "days /
 * leaveDates.length, rounded to one decimal".
 */
class LeaveBreakTest {

  @Test
  void daysOffPerLeaveIsDaysDividedByLeaves() {
    // 10 days (1 to 10 March) with 3 leaves: 3.33 rounds to 3.3.
    LeaveBreak lb =
        new LeaveBreak(
            date(2027, 3, 1),
            date(2027, 3, 10),
            List.of(date(2027, 3, 3), date(2027, 3, 4), date(2027, 3, 5)),
            List.of());

    assertThat(lb.days()).isEqualTo(10);
    assertThat(lb.daysOffPerLeave()).isEqualTo(3.3);
  }

  @Test
  void daysOffPerLeaveRoundsToOneDecimal() {
    // 5 days with 3 leaves: 1.67 rounds to 1.7; 9 days with 4 leaves: 2.25 rounds half up to 2.3.
    LeaveBreak fiveDays =
        new LeaveBreak(
            date(2027, 3, 1),
            date(2027, 3, 5),
            List.of(date(2027, 3, 1), date(2027, 3, 2), date(2027, 3, 3)),
            List.of());
    LeaveBreak nineDays =
        new LeaveBreak(
            date(2027, 3, 1),
            date(2027, 3, 9),
            List.of(date(2027, 3, 2), date(2027, 3, 3), date(2027, 3, 4), date(2027, 3, 5)),
            List.of());

    assertThat(fiveDays.daysOffPerLeave()).isEqualTo(1.7);
    assertThat(nineDays.daysOffPerLeave()).isEqualTo(2.3);
  }

  @Test
  void oneLeaveGivesTheBreakLength() {
    LeaveBreak lb =
        new LeaveBreak(date(2027, 3, 1), date(2027, 3, 4), List.of(date(2027, 3, 2)), List.of());

    assertThat(lb.daysOffPerLeave()).isEqualTo(4.0);
  }
}
