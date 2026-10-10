package dev.leavewise.api;

import java.util.List;

/** Thrown when a request breaks the rules in docs/API.md; becomes a 400 problem+json. */
public class InvalidInputException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<FieldProblem> problems;

  public InvalidInputException(List<FieldProblem> problems) {
    super("invalid input", null, false, false);
    this.problems = List.copyOf(problems);
  }

  public InvalidInputException(String field, String message) {
    this(List.of(new FieldProblem(field, message)));
  }

  public List<FieldProblem> problems() {
    return problems;
  }
}
