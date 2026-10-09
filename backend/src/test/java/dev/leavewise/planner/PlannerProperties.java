package dev.leavewise.planner;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Rules that must hold for every request, checked on random ones (docs/QA.md "Other properties").
 * Holiday names are invented; dates are random offsets around the plan year and its edges.
 */
class PlannerProperties {

  /** A request, the clock's date and a seed for shuffling the holiday list. */
  record Scenario(LocalDate today, PlanRequest request, long shuffleSeed) {
    @Override
    public String toString() {
      return "today=%s year=%d leaves=%d offs=%s style=%s holidays=%s"
          .formatted(
              today,
              request.year(),
              request.leaveDays(),
              request.weeklyOffs(),
              request.breakStyle(),
              request.holidays().stream().map(h -> h.date().toString()).toList());
    }
  }

  private static final List<LocalDate> TODAYS_FOR_2027 =
      List.of(
          LocalDate.of(2026, 10, 9),
          LocalDate.of(2027, 1, 1),
          LocalDate.of(2027, 3, 10),
          LocalDate.of(2027, 12, 20),
          LocalDate.of(2027, 12, 30));
  private static final List<LocalDate> TODAYS_FOR_2028 =
      List.of(
          LocalDate.of(2026, 10, 9),
          LocalDate.of(2027, 12, 30),
          LocalDate.of(2028, 2, 28),
          LocalDate.of(2028, 12, 30));

  private Arbitrary<Set<DayOfWeek>> weeklyOffs() {
    return Arbitraries.of(DayOfWeek.values()).set().ofMaxSize(6);
  }

  private Arbitrary<List<Holiday>> holidays(int year, int maxCount) {
    return Arbitraries.integers()
        .between(-15, 380)
        .list()
        .ofMaxSize(maxCount)
        .map(
            offsets -> {
              List<Holiday> holidays = new ArrayList<>();
              for (int i = 0; i < offsets.size(); i++) {
                holidays.add(
                    new Holiday(
                        LocalDate.of(year, 1, 1).plusDays(offsets.get(i)), "Fixture Holiday " + i));
              }
              return holidays;
            });
  }

  private Arbitrary<Scenario> scenarios(
      Arbitrary<Integer> leaveDays, Arbitrary<Integer> year, boolean shortWindow) {
    return year.flatMap(
        y -> {
          Arbitrary<LocalDate> today =
              shortWindow
                  ? Arbitraries.integers().between(12, 30).map(d -> LocalDate.of(2027, 12, d))
                  : Arbitraries.of(y == 2027 ? TODAYS_FOR_2027 : TODAYS_FOR_2028);
          return Combinators.combine(
                  today,
                  leaveDays,
                  weeklyOffs(),
                  Arbitraries.of(BreakStyle.values()),
                  holidays(y, 25),
                  Arbitraries.longs())
              .as(
                  (t, leaves, offs, style, hols, seed) ->
                      new Scenario(t, new PlanRequest(y, leaves, offs, style, hols), seed));
        });
  }

  @Provide
  Arbitrary<Scenario> anyScenario() {
    return scenarios(
        Arbitraries.oneOf(
            Arbitraries.integers().between(0, 8), Arbitraries.integers().between(0, 60)),
        Arbitraries.of(2027, 2028),
        false);
  }

  /** Windows of at most 19 days, so the brute-force oracle can check the whole plan. */
  @Provide
  Arbitrary<Scenario> shortWindowScenarios() {
    return scenarios(Arbitraries.integers().between(0, 6), Arbitraries.of(2027), true);
  }

  /** Whole years with at most 2 leaves, so the brute-force oracle can check the whole plan. */
  @Provide
  Arbitrary<Scenario> fullYearTwoLeaveScenarios() {
    return scenarios(Arbitraries.integers().between(0, 2), Arbitraries.of(2027, 2028), false);
  }

  private static Plan plan(Scenario s) {
    return new Planner(Fixtures.clockOn(s.today())).plan(s.request());
  }

  private static boolean isLeave(Plan plan, LocalDate date) {
    return plan.leaveDates().contains(date);
  }

  @Property(tries = 60)
  void leavesAreWorkingDays(@ForAll("anyScenario") Scenario s) {
    // PRD: "Leave day: always placed on a working day", and leaves stay inside the planning window.
    Plan plan = plan(s);
    PlanningCalendar calendar = PlanningCalendar.of(s.request(), s.today());
    Statistics.label("leaves placed")
        .collect(plan.leaveDates().isEmpty() ? "none" : "some")
        .coverage(coverage -> coverage.check("some").percentage(p -> p >= 30.0));

    for (LocalDate leave : plan.leaveDates()) {
      assertThat(calendar.kindOf(leave))
          .as("%s is a leave but not a working day (%s)", leave, s)
          .isEqualTo(DayKind.WORKING);
      assertThat(leave.getYear())
          .as("leave %s outside the plan year (%s)", leave, s)
          .isEqualTo(s.request().year());
      assertThat(leave)
          .as("leave outside the planning window (%s)", s)
          .isAfterOrEqualTo(plan.planningFrom().orElseThrow());
      assertThat(leave).isBeforeOrEqualTo(plan.planningTo().orElseThrow());
    }
  }

  @Property(tries = 60)
  void leavesUsedNeverExceedTheBalance(@ForAll("anyScenario") Scenario s) {
    Plan plan = plan(s);

    assertThat(plan.leavesUsed())
        .as("more leaves used than the %d available (%s)", s.request().leaveDays(), s)
        .isLessThanOrEqualTo(s.request().leaveDays());
    assertThat(plan.leaveDates()).as("leave dates are distinct").doesNotHaveDuplicates();
    assertThat(plan.leaveDates()).isSorted();
  }

  @Property(tries = 60)
  void noLeaveOutsideABreak(@ForAll("anyScenario") Scenario s) {
    // PRD: "A leave day outside a break adds nothing, so the planner never places one."
    Plan plan = plan(s);

    for (LocalDate leave : plan.leaveDates()) {
      assertThat(plan.breaks())
          .as("leave %s lies in no break (%s)", leave, s)
          .anyMatch(b -> !leave.isBefore(b.start()) && !leave.isAfter(b.end()));
    }
  }

  @Property(tries = 60)
  void everyBreakIsLongEnoughAndHasALeave(@ForAll("anyScenario") Scenario s) {
    Plan plan = plan(s);

    for (LeaveBreak b : plan.breaks()) {
      assertThat(b.leaveDates()).as("PRD Break: at least one leave day (%s)", s).isNotEmpty();
      assertThat(b.days())
          .as(
              "break %s..%s is shorter than the %s minimum (%s)",
              b.start(), b.end(), s.request().breakStyle(), s)
          .isGreaterThanOrEqualTo(s.request().breakStyle().minDays());
    }
    assertThat(plan.totalDaysOff())
        .isEqualTo(plan.breaks().stream().mapToInt(LeaveBreak::days).sum());
  }

  @Property(tries = 60)
  void breaksAreMaximalRunsOfDaysOff(@ForAll("anyScenario") Scenario s) {
    // PRD Break: "days off in a row": every day inside is off, the days either side are not.
    Plan plan = plan(s);
    PlanningCalendar calendar = PlanningCalendar.of(s.request(), s.today());
    Set<LocalDate> leaves = new HashSet<>(plan.leaveDates());
    LocalDate horizonStart = calendar.horizon().isEmpty() ? null : calendar.horizon().get(0);
    LocalDate horizonEnd =
        calendar.horizon().isEmpty() ? null : calendar.horizon().get(calendar.horizon().size() - 1);

    for (LeaveBreak b : plan.breaks()) {
      for (LocalDate d = b.start(); !d.isAfter(b.end()); d = d.plusDays(1)) {
        assertThat(calendar.kindOf(d) != DayKind.WORKING || leaves.contains(d))
            .as(
                "%s inside break %s..%s is a working day that is not a leave (%s)",
                d, b.start(), b.end(), s)
            .isTrue();
      }
      if (!b.start().equals(horizonStart)) {
        LocalDate before = b.start().minusDays(1);
        assertThat(calendar.kindOf(before) == DayKind.WORKING && !leaves.contains(before))
            .as("break %s..%s could be extended to the left (%s)", b.start(), b.end(), s)
            .isTrue();
      }
      if (!b.end().equals(horizonEnd)) {
        LocalDate after = b.end().plusDays(1);
        assertThat(calendar.kindOf(after) == DayKind.WORKING && !leaves.contains(after))
            .as("break %s..%s could be extended to the right (%s)", b.start(), b.end(), s)
            .isTrue();
      }
    }
  }

  @Property(tries = 60)
  void holidayOrderDoesNotMatter(@ForAll("anyScenario") Scenario s) {
    // PRD: "the same inputs always give the same plan"; ADR 0004.
    List<Holiday> shuffled = new ArrayList<>(s.request().holidays());
    Collections.shuffle(shuffled, new Random(s.shuffleSeed()));
    PlanRequest reordered =
        new PlanRequest(
            s.request().year(),
            s.request().leaveDays(),
            s.request().weeklyOffs(),
            s.request().breakStyle(),
            shuffled);

    Plan original = plan(s);
    Plan other = new Planner(Fixtures.clockOn(s.today())).plan(reordered);

    assertThat(other)
        .as("listing the holidays in another order changed the plan (%s)", s)
        .isEqualTo(original);
  }

  @Property(tries = 40)
  void repeatedRunsAreIdentical(@ForAll("anyScenario") Scenario s) {
    Planner planner = new Planner(Fixtures.clockOn(s.today()));

    assertThat(planner.plan(s.request()))
        .as("planning twice gave different plans (%s)", s)
        .isEqualTo(planner.plan(s.request()));
  }

  @Property(tries = 60)
  void spareLeavesMeanEveryWorkingDayInTheWindowIsALeave(@ForAll("anyScenario") Scenario s) {
    // With a break in the plan and a leave to spare, a leave on a working day next to the break
    // would add days, so the best plan would have used it. Hence a spare leave is only possible
    // when every working day of the window is already a leave (PRD: "more leave days than working
    // days").
    Plan plan = plan(s);
    PlanningCalendar calendar = PlanningCalendar.of(s.request(), s.today());
    if (plan.breaks().isEmpty() || plan.leavesUnused() == 0) {
      return;
    }

    long workingDaysInWindow = 0;
    for (LocalDate d = plan.planningFrom().orElseThrow();
        !d.isAfter(plan.planningTo().orElseThrow());
        d = d.plusDays(1)) {
      if (calendar.kindOf(d) == DayKind.WORKING) {
        workingDaysInWindow++;
      }
    }
    assertThat((long) plan.leavesUsed())
        .as(
            "%d leaves unused but only %d of %d working days are leaves (%s)",
            plan.leavesUnused(), plan.leavesUsed(), workingDaysInWindow, s)
        .isEqualTo(workingDaysInWindow);
  }

  @Property(tries = 60)
  void notesFollowTheRules(@ForAll("anyScenario") Scenario s) {
    // docs/API.md notes: at most one, chosen by precedence.
    Plan plan = plan(s);
    Note.Code code = plan.note().map(Note::code).orElse(null);

    if (plan.planningFrom().isEmpty()) {
      assertThat(code).as("empty window (%s)", s).isEqualTo(Note.Code.NO_DAYS_LEFT);
    } else if (s.request().leaveDays() == 0) {
      assertThat(code).as("0 leaves (%s)", s).isEqualTo(Note.Code.NO_LEAVE_USED);
    } else if (plan.breaks().isEmpty()) {
      assertThat(code).as("leaves but no break (%s)", s).isEqualTo(Note.Code.NO_BREAK_POSSIBLE);
    } else if (plan.leavesUnused() > 0) {
      assertThat(plan.note())
          .as("leaves left over (%s)", s)
          .contains(new Note(Note.Code.LEAVES_LEFT_OVER, plan.leavesUnused()));
    } else {
      assertThat(code).as("a normal plan has no note (%s)", s).isNull();
    }
  }

  @Property(tries = 60)
  void calendarDaysDescribeTheWholeYear(@ForAll("anyScenario") Scenario s) {
    Plan plan = plan(s);
    int year = s.request().year();
    int length = LocalDate.of(year, 1, 1).lengthOfYear();

    assertThat(plan.days()).as("one entry per date of %d", year).hasSize(length);
    for (int i = 0; i < length; i++) {
      CalendarDay day = plan.days().get(i);
      assertThat(day.date()).isEqualTo(LocalDate.of(year, 1, 1).plusDays(i));
      assertThat(day.planned())
          .as("%s planned flag (%s)", day.date(), s)
          .isEqualTo(
              plan.planningFrom().isPresent() && !day.date().isBefore(plan.planningFrom().get()));
      assertThat(day.kind() == DayKind.LEAVE)
          .as("%s LEAVE kind must match the leave list (%s)", day.date(), s)
          .isEqualTo(isLeave(plan, day.date()));
      boolean inBreak =
          plan.breaks().stream()
              .anyMatch(b -> !day.date().isBefore(b.start()) && !day.date().isAfter(b.end()));
      assertThat(day.breakIndex().isPresent())
          .as("%s breakIndex (%s)", day.date(), s)
          .isEqualTo(inBreak);
    }
  }

  @Property(tries = 60)
  void freeLongWeekendsFollowTheRules(@ForAll("anyScenario") Scenario s) {
    // PRD: "3 or more days off in a row that need no leave"; docs: includes a window day, not part
    // of a break.
    Plan plan = plan(s);
    PlanningCalendar calendar = PlanningCalendar.of(s.request(), s.today());

    for (FreeLongWeekend f : plan.freeLongWeekends()) {
      assertThat(f.days())
          .as("free long weekend %s..%s is too short (%s)", f.start(), f.end(), s)
          .isGreaterThanOrEqualTo(3);
      boolean touchesWindow =
          !f.end().isBefore(plan.planningFrom().orElseThrow())
              && !f.start().isAfter(plan.planningTo().orElseThrow());
      assertThat(touchesWindow)
          .as("%s..%s has no day in the window (%s)", f.start(), f.end(), s)
          .isTrue();
      for (LocalDate d = f.start(); !d.isAfter(f.end()); d = d.plusDays(1)) {
        assertThat(calendar.kindOf(d))
            .as("%s needs leave (%s)", d, s)
            .isNotEqualTo(DayKind.WORKING);
        assertThat(isLeave(plan, d)).isFalse();
      }
      assertThat(plan.breaks())
          .as("free long weekend %s..%s overlaps a break (%s)", f.start(), f.end(), s)
          .noneMatch(b -> !b.start().isAfter(f.end()) && !b.end().isBefore(f.start()));
    }
  }

  @Property(tries = 150)
  void matchesBruteForceOnShortWindows(@ForAll("shortWindowScenarios") Scenario s) {
    // The whole planner (calendar, window, edges, tie-breaks) against the oracle, for windows small
    // enough to try every subset.
    DateOracle.Answer expected = DateOracle.solve(s.request(), s.today());

    Plan plan = plan(s);

    assertThat(plan.leaveDates())
        .as("leave dates differ from brute force (%s)", s)
        .isEqualTo(expected.leaveDates());
    assertThat(plan.totalDaysOff())
        .as("days in breaks differ from brute force (%s)", s)
        .isEqualTo(expected.daysInBreaks());
    assertThat(plan.breaks().stream().map(b -> new DateOracle.Span(b.start(), b.end())).toList())
        .as("breaks differ from brute force (%s)", s)
        .isEqualTo(expected.breaks());
  }

  @Property(tries = 30)
  void matchesBruteForceForAFullYearWithUpToTwoLeaves(
      @ForAll("fullYearTwoLeaveScenarios") Scenario s) {
    DateOracle.Answer expected = DateOracle.solve(s.request(), s.today());

    Plan plan = plan(s);

    assertThat(plan.leaveDates())
        .as("leave dates differ from brute force (%s)", s)
        .isEqualTo(expected.leaveDates());
    assertThat(plan.totalDaysOff())
        .as("days in breaks differ from brute force (%s)", s)
        .isEqualTo(expected.daysInBreaks());
    assertThat(plan.breaks().stream().map(b -> new DateOracle.Span(b.start(), b.end())).toList())
        .as("breaks differ from brute force (%s)", s)
        .isEqualTo(expected.breaks());
  }
}
