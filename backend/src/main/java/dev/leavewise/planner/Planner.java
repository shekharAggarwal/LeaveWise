package dev.leavewise.planner;

import java.time.Clock;
import java.util.Objects;

/** Plans leave days for a request (PRD, Planning rules). "Today" comes from the injected clock. */
public final class Planner {

  private final Clock clock;

  public Planner(Clock clock) {
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * @throws IllegalArgumentException if the plan year is before the current year
   */
  public Plan plan(PlanRequest request) {
    throw new UnsupportedOperationException("not implemented yet");
  }
}
