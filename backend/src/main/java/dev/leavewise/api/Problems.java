package dev.leavewise.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** RFC 9457 problem details in the shape docs/API.md describes. */
public final class Problems {

  private Problems() {}

  public static ProblemDetail of(HttpStatus status, String suffix, String title, String detail) {
    return of(status, suffix, title, detail, List.of());
  }

  public static ProblemDetail of(
      HttpStatus status, String suffix, String title, String detail, List<FieldProblem> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(URI.create("urn:leavewise:problem:" + suffix));
    problem.setTitle(title);
    problem.setProperty("errors", errors);
    return problem;
  }

  public static ProblemDetail invalidInput(List<FieldProblem> errors) {
    return of(
        HttpStatus.BAD_REQUEST,
        "invalid-input",
        "Check your inputs",
        errors.get(0).message(),
        errors);
  }

  public static ProblemDetail tooLarge() {
    return of(
        HttpStatus.CONTENT_TOO_LARGE,
        "too-large",
        "Request too large",
        "The request is too large. Paste at most 50 holiday lines.");
  }

  public static ProblemDetail rateLimited() {
    return of(
        HttpStatus.TOO_MANY_REQUESTS,
        "rate-limited",
        "Too many requests",
        "Too many requests. Wait a minute and try again.");
  }
}
