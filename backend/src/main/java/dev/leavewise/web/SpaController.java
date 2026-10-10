package dev.leavewise.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the one-page app for page paths (no dot, not /api or /actuator); real files under static/
 * win because their paths contain a dot.
 */
@Controller
class SpaController {

  @GetMapping({
    "/{first:^(?!api$|actuator$)[^.]*$}",
    "/{first:^(?!api$|actuator$)[^.]*$}/{second:[^.]*}",
    "/{first:^(?!api$|actuator$)[^.]*$}/{second:[^.]*}/{third:[^.]*}"
  })
  String page() {
    return "forward:/index.html";
  }
}
