package dev.leavewise.planner;

import java.time.LocalDate;
import java.util.Objects;

/** One holiday as entered: a date and a name. Two holidays may share a date. */
public record Holiday(LocalDate date, String name) {
  public Holiday {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(name, "name");
  }
}
