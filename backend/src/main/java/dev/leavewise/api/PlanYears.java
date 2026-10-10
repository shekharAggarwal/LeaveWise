package dev.leavewise.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

/** PRD, Inputs: the current year or next year; next year is the default from October onward. */
record PlanYears(LocalDate today) {

  static PlanYears now(Clock clock) {
    return new PlanYears(LocalDate.now(clock));
  }

  int current() {
    return today.getYear();
  }

  int next() {
    return today.getYear() + 1;
  }

  List<Integer> all() {
    return List.of(current(), next());
  }

  int defaultYear() {
    return today.getMonthValue() >= Month.OCTOBER.getValue() ? next() : current();
  }

  boolean allows(int year) {
    return year == current() || year == next();
  }

  String pickMessage() {
    return "Pick " + current() + " or " + next() + ".";
  }
}
