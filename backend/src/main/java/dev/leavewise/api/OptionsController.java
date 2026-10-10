package dev.leavewise.api;

import dev.leavewise.api.ApiDtos.BreakStyleOption;
import dev.leavewise.api.ApiDtos.LeaveDaysOption;
import dev.leavewise.api.ApiDtos.OptionsResponse;
import dev.leavewise.holidays.HolidayPasteParser;
import dev.leavewise.planner.BreakStyle;
import dev.leavewise.planner.PlanRequest;
import java.time.Clock;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/options: form defaults and limits from the server clock (docs/API.md). */
@RestController
class OptionsController {

  static final int DEFAULT_LEAVE_DAYS = 12; // PRD, Inputs

  private final Clock clock;

  OptionsController(Clock clock) {
    this.clock = clock;
  }

  @GetMapping("/api/options")
  OptionsResponse options() {
    PlanYears years = PlanYears.now(clock);
    return new OptionsResponse(
        years.today(),
        AppConfig.ZONE.getId(),
        years.all(),
        years.defaultYear(),
        new LeaveDaysOption(0, PlanRequest.MAX_LEAVE_DAYS, DEFAULT_LEAVE_DAYS),
        List.of("SATURDAY", "SUNDAY"),
        List.of(
            new BreakStyleOption(
                BreakStyle.LONG_WEEKENDS.name(),
                "Long weekends",
                BreakStyle.LONG_WEEKENDS.minDays()),
            new BreakStyleOption(
                BreakStyle.SHORT_TRIPS.name(), "Short trips", BreakStyle.SHORT_TRIPS.minDays()),
            new BreakStyleOption(
                BreakStyle.BIG_TRIPS.name(), "Big trips", BreakStyle.BIG_TRIPS.minDays())),
        BreakStyle.LONG_WEEKENDS.name(),
        HolidayPasteParser.MAX_LINES);
  }
}
