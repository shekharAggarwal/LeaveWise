package dev.leavewise.api;

import dev.leavewise.api.ApiDtos.HolidaysResponse;
import dev.leavewise.api.ApiDtos.LineErrorDto;
import dev.leavewise.api.ApiDtos.ParseResponse;
import dev.leavewise.api.ApiDtos.ParsedHolidayDto;
import dev.leavewise.holidays.HolidayList;
import dev.leavewise.holidays.HolidayPasteParser;
import dev.leavewise.holidays.HolidayRepository;
import dev.leavewise.planner.HolidayDay;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

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
        PlanMapper.holidays(HolidayDay.merge(list.get().holidays())));
  }

  @PostMapping("/api/holidays/parse")
  ParseResponse parse(@RequestBody JsonNode body) {
    PlanYears years = PlanYears.now(clock);
    if (body == null || !body.isObject()) {
      throw new InvalidInputException("body", "Send the pasted list as a JSON object.");
    }
    int year = PlanInputs.requireYear(body.path("year"), years);
    JsonNode textNode = body.path("text");
    if (textNode.isMissingNode() || textNode.isNull()) {
      throw new InvalidInputException("text", "Fill in text.");
    }
    if (!textNode.isString()) {
      throw new InvalidInputException("text", "Paste the holiday list as text.");
    }
    String text = textNode.stringValue();
    if (text.codePointCount(0, text.length()) > MAX_PASTE_CHARACTERS) {
      throw new InvalidInputException("text", "Paste at most 50 lines (8,000 characters).");
    }
    HolidayPasteParser.Result result = HolidayPasteParser.parse(year, text);
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
}
