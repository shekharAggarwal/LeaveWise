package dev.leavewise.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Writes a problem+json response from a filter, before Spring MVC is involved. */
final class ProblemWriter {

  private final JsonMapper json;

  ProblemWriter(JsonMapper json) {
    this.json = json;
  }

  void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
    response.setStatus(problem.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    json.writeValue(response.getOutputStream(), problem);
  }
}
