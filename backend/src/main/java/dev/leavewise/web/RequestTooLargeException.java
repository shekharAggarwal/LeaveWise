package dev.leavewise.web;

import java.io.IOException;

/** A request body went past the size limit while being read (docs/API.md: 32 KB). */
public class RequestTooLargeException extends IOException {

  private static final long serialVersionUID = 1L;

  public RequestTooLargeException() {
    super("request body too large");
  }
}
