package dev.leavewise.api;

import dev.leavewise.api.ApiDtos.HolidaysResponse;
import dev.leavewise.api.ApiDtos.LineErrorDto;
import dev.leavewise.api.ApiDtos.ParseRequest;
import dev.leavewise.api.ApiDtos.ParseResponse;
import dev.leavewise.api.ApiDtos.ParsedHolidayDto;
import dev.leavewise.holidays.HolidayList;
import dev.leavewise.holidays.HolidayPasteParser;
import dev.leavewise.holidays.HolidayRepository;
import dev.leavewise.planner.HolidayDay;
import dev.leavewise.planner.PlanRequest;
import dev.leavewise.planner.PlanningCalendar;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/holidays and POST /api/holidays/parse (docs/API.md). */
@RestController
class HolidaysController {

  static final String REMINDER = "Check this against your company's official holiday list.";
  static final int MAX_PASTE_CHARACTERS = 8000;

  private final HolidayRepository repository;
  private final Clock clock;

  HolidaysController(HolidayRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @GetMapping("/api/holidays")
  HolidaysResponse holidays(@RequestParam(required = false) String year) {
    int y = requireYear(year);
    Optional<HolidayList> list = repository.forYear(y);
    if (list.isEmpty()) {
      return new HolidaysResponse(y, false, null, null, REMINDER, List.of());
    }
    return new HolidaysResponse(
        y,
        true,
        list.get().list(),
        list.get().source(),
        REMINDER,
        PlanMapper.holidays(merged(list.get())));
  }

  @PostMapping("/api/holidays/parse")
  ParseResponse parse(@RequestBody ParseRequest request) {
    PlanYears years = PlanYears.now(clock);
    if (request.year() == null || !years.allows(request.year())) {
      throw new InvalidInputException("year", years.pickMessage());
    }
    if (request.text() == null) {
      throw new InvalidInputException("text", "Fill in text.");
    }
    String text = request.text();
    if (text.length() > MAX_PASTE_CHARACTERS) {
      throw new InvalidInputException("text", "Paste at most 50 lines (8,000 characters).");
    }
    HolidayPasteParser.Result result = HolidayPasteParser.parse(request.year(), text);
    return new ParseResponse(
        result.holidays().stream()
            .map(h -> new ParsedHolidayDto(h.line(), h.date(), h.name()))
            .toList(),
        result.errors().stream().map(e -> new LineErrorDto(e.line(), e.reason())).toList());
  }

  private int requireYear(String year) {
    PlanYears years = PlanYears.now(clock);
    try {
      int y = Integer.parseInt(year == null ? "" : year.strip());
      if (years.allows(y)) {
        return y;
      }
    } catch (NumberFormatException e) {
      // reported below
    }
    throw new InvalidInputException("year", years.pickMessage());
  }

  /** Same-date entries merged with every name, the way the planner merges them (PRD). */
  private static List<HolidayDay> merged(HolidayList list) {
    PlanRequest request =
        new PlanRequest(
            list.year(),
            0,
            Set.<DayOfWeek>of(),
            dev.leavewise.planner.BreakStyle.LONG_WEEKENDS,
            list.holidays());
    return PlanningCalendar.of(request, LocalDate.of(list.year() - 1, 1, 1)).holidays();
  }
}
