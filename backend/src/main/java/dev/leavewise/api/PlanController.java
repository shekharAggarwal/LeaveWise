package dev.leavewise.api;

import dev.leavewise.api.ApiDtos.PlanResponse;
import dev.leavewise.holidays.HolidayList;
import dev.leavewise.holidays.HolidayRepository;
import dev.leavewise.ics.IcsWriter;
import dev.leavewise.planner.Holiday;
import dev.leavewise.planner.Plan;
import dev.leavewise.planner.PlanRequest;
import dev.leavewise.planner.Planner;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** POST /api/plan and POST /api/plan/ics (docs/API.md). */
@RestController
class PlanController {

  private static final MediaType CALENDAR =
      new MediaType("text", "calendar", StandardCharsets.UTF_8);

  private final Planner planner;
  private final HolidayRepository holidays;
  private final Clock clock;

  PlanController(Planner planner, HolidayRepository holidays, Clock clock) {
    this.planner = planner;
    this.holidays = holidays;
    this.clock = clock;
  }

  @PostMapping("/api/plan")
  PlanResponse plan(@RequestBody JsonNode body) {
    return PlanMapper.toResponse(planFor(body));
  }

  @PostMapping("/api/plan/ics")
  ResponseEntity<String> ics(@RequestBody JsonNode body) {
    Plan plan = planFor(body);
    return ResponseEntity.ok()
        .contentType(CALENDAR)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment()
                .filename("leavewise-" + plan.year() + ".ics")
                .build()
                .toString())
        .body(IcsWriter.write(plan, clock.instant()));
  }

  private Plan planFor(JsonNode body) {
    // One reading of the clock per request, for both the year check and the planner.
    PlanYears years = PlanYears.now(clock);
    PlanRequest request = PlanInputs.parse(body, years);
    // Year edges (ADR 0006): bundled holidays of the adjacent years extend breaks across 1 Jan and
    // 31 Dec. Floaters don't: nobody has chosen them for that year (ADR 0007 amendment).
    List<Holiday> all = new ArrayList<>(request.holidays());
    holidays.forYear(request.year() - 1).map(HolidayList::fixedHolidays).ifPresent(all::addAll);
    holidays.forYear(request.year() + 1).map(HolidayList::fixedHolidays).ifPresent(all::addAll);
    return planner.plan(
        new PlanRequest(
            request.year(), request.leaveDays(), request.weeklyOffs(), request.breakStyle(), all),
        years.today());
  }
}
