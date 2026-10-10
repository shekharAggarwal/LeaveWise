package dev.leavewise.api;

import dev.leavewise.web.RequestTooLargeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Every API error as problem+json (docs/API.md, Errors). Nothing from the request is logged: the
 * 500 handler logs only the exception type and stack, never the message, which can quote input.
 */
@RestControllerAdvice
class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(InvalidInputException.class)
  ProblemDetail invalid(InvalidInputException e) {
    return Problems.invalidInput(e.problems());
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ProblemDetail unreadable(HttpMessageNotReadableException e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof RequestTooLargeException) {
        return Problems.tooLarge();
      }
    }
    return Problems.of(
        HttpStatus.BAD_REQUEST,
        "unreadable",
        "Request could not be read",
        "The request could not be read. Reload the page and try again.");
  }

  @ExceptionHandler(RequestTooLargeException.class)
  ProblemDetail tooLarge() {
    return Problems.tooLarge();
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ProblemDetail notFound() {
    return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", "There is nothing here.");
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ProblemDetail method() {
    return Problems.of(
        HttpStatus.METHOD_NOT_ALLOWED,
        "method-not-allowed",
        "Method not allowed",
        "Use the method docs/API.md lists.");
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ProblemDetail mediaType() {
    return Problems.of(
        HttpStatus.UNSUPPORTED_MEDIA_TYPE,
        "unsupported-media-type",
        "Unsupported media type",
        "Send JSON with Content-Type: application/json.");
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail internal(Exception e) {
    Exception logged = new Exception(e.getClass().getName());
    logged.setStackTrace(e.getStackTrace());
    LOG.error("Unexpected error while handling an API request", logged);
    return Problems.of(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "internal",
        "Server error",
        "Something went wrong on our side. Try again.");
  }
}
