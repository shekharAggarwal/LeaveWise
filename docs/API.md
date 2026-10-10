# API

Base path `/api`. JSON in and out (UTF-8). Nothing is stored and no request is logged. The browser checks inputs
for fast feedback, and the server checks them again; the server is the authority.

> All dates and holiday names in the examples below are **made-up example values**, not real holiday data.

## Conventions

| Topic | Rule |
| --- | --- |
| Dates | ISO `YYYY-MM-DD` (calendar dates, no time zone). The server's "today" is in Asia/Kolkata. |
| Weekly offs | `MONDAY` … `SUNDAY` (Java `DayOfWeek` names). Order doesn't matter; a repeated day is collapsed into one. |
| Break styles | `LONG_WEEKENDS` (3+ days), `SHORT_TRIPS` (5+ days), `BIG_TRIPS` (9+ days). |
| Request size | Bodies over 32 KB get `413`. |
| Rate limit | 60 requests per minute per client on `/api/**`, with a burst of 20; over it gets `429` and `Retry-After` (whole seconds until a request is allowed). A client is an IPv4 address or an IPv6 /64 prefix. The limits apply to every path the server routes to the API, however it is spelled (`/%61pi/...`, `/api;x/...`). |
| Errors | RFC 9457 `application/problem+json`, described at the end. |
| Headers on every response | `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`, `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy: camera=(), microphone=(), geolocation=()`, `Strict-Transport-Security: max-age=31536000` (HTTPS only), `Cache-Control: no-store` on `/api/**`. |

## `GET /api/options`

Defaults and limits for the form, computed from the server clock so the browser and server agree on "today".

```json
{
  "today": "2026-10-09",
  "timeZone": "Asia/Kolkata",
  "years": [2026, 2027],
  "defaultYear": 2027,
  "leaveDays": {"min": 0, "max": 60, "default": 12},
  "defaultWeeklyOffs": ["SATURDAY", "SUNDAY"],
  "breakStyles": [
    {"id": "LONG_WEEKENDS", "label": "Long weekends", "minDays": 3},
    {"id": "SHORT_TRIPS", "label": "Short trips", "minDays": 5},
    {"id": "BIG_TRIPS", "label": "Big trips", "minDays": 9}
  ],
  "defaultBreakStyle": "LONG_WEEKENDS",
  "maxPastedLines": 50
}
```

`defaultYear` is the current year, or next year from 1 October onward (PRD, Inputs).

## `GET /api/holidays?year=2027`

The bundled India public holiday list for one year. `year` must be one of `options.years`.

```json
{
  "year": 2027,
  "available": true,
  "list": "Example list name",
  "source": {
    "publisher": "Example publisher",
    "title": "Example office memorandum",
    "url": "https://example.gov.in/holidays-2027.pdf",
    "retrievedOn": "2026-10-20"
  },
  "reminder": "Check this against your company's official holiday list.",
  "holidays": [
    {"date": "2027-03-04", "names": ["Example Holiday A"]},
    {"date": "2027-08-27", "names": ["Example Holiday B", "Example Holiday C"]}
  ]
}
```

- A `year` outside `options.years`, or missing, gets `400` with `Pick {current} or {next}.`
- When no bundled file exists for the year: `200` with `"available": false`, `"source": null` and `"holidays": []`,
  so the user can still plan with their own holidays.
- Two holidays on the same date are returned once with both names (PRD, Validation and errors).

## `POST /api/holidays/parse`

Parses a pasted list: one `YYYY-MM-DD Name` per line, up to 50 lines (PRD, Inputs). Bad lines are reported with
their line number and reason; all other lines still load.

Request:

```json
{"year": 2027, "text": "2027-03-04 Example Holiday A\n2027-02-30 Bad date\n\n2026-12-25 Wrong year\nnot a line"}
```

`text` is at most 8,000 characters (counted as characters, so an emoji is one). The date and name are separated by
spaces or tabs, including no-break spaces. Blank lines are skipped but still count for line numbers. A bad `year`, a longer
`text` or an unreadable body get the errors in the table at the end.

Response `200`:

```json
{
  "holidays": [{"line": 1, "date": "2027-03-04", "name": "Example Holiday A"}],
  "errors": [
    {"line": 2, "reason": "2027-02-30 is not a real date."},
    {"line": 4, "reason": "2026-12-25 is not in 2027."},
    {"line": 5, "reason": "Start the line with a date like 2027-01-26, then the name."}
  ]
}
```

| Line problem | `reason` |
| --- | --- |
| Doesn't start with `YYYY-MM-DD` | `Start the line with a date like {year}-01-26, then the name.` |
| Not a real date | `{text} is not a real date.` |
| Date outside the chosen year | `{date} is not in {year}.` |
| No name after the date | `Add a name after the date.` |
| Name over 80 characters (counted as characters, so an emoji is one) | `Keep the name to 80 characters or fewer.` |
| Name with a control character | `Holiday names can't contain line breaks or control characters.` |
| Line 51 or later | `Only 50 lines can be pasted. This line was not loaded.` |

## `POST /api/plan`

Request (every field has the PRD default in the form; the API requires them all so the request is explicit):

```json
{
  "year": 2027,
  "leaveDays": 12,
  "weeklyOffs": ["SATURDAY", "SUNDAY"],
  "breakStyle": "LONG_WEEKENDS",
  "holidays": [
    {"date": "2027-03-04", "name": "Example Holiday A"},
    {"date": "2027-08-27", "name": "Example Holiday B"}
  ]
}
```

| Field | Rule |
| --- | --- |
| `year` | One of `options.years`. For the current year, only days after today are planned. |
| `leaveDays` | Whole number from 0 to 60. `12.5`, `"12"`, `-1` and `61` are rejected; `60.0` and `6e1` are the whole number 60. |
| `weeklyOffs` | Any days; repeats collapse. Empty is allowed (no weekly off). All seven distinct days is rejected with the PRD message: at least one working day must remain. |
| `breakStyle` | One of the three ids. |
| `holidays` | At most 100 items. `date` in the chosen year, `name` 1 to 80 characters. Duplicate dates are merged, keeping every name. Unticked holidays are simply not sent. |

Response `200`:

```json
{
  "year": 2027,
  "planningFrom": "2027-01-01",
  "planningTo": "2027-12-31",
  "breakStyle": "LONG_WEEKENDS",
  "minBreakDays": 3,
  "summary": {
    "leavesAvailable": 12,
    "leavesUsed": 12,
    "leavesUnused": 0,
    "totalDaysOff": 41,
    "breakCount": 9
  },
  "breaks": [
    {
      "start": "2027-03-04",
      "end": "2027-03-07",
      "days": 4,
      "leaveDates": ["2027-03-05"],
      "holidays": [{"date": "2027-03-04", "names": ["Example Holiday A"]}],
      "daysOffPerLeave": 4.0
    }
  ],
  "freeLongWeekends": [
    {"start": "2027-08-27", "end": "2027-08-29", "days": 3,
     "holidays": [{"date": "2027-08-27", "names": ["Example Holiday B"]}]}
  ],
  "notes": [],
  "days": [
    {"date": "2027-01-01", "kind": "WORKING", "planned": true, "holidayNames": [], "breakIndex": null, "freeLongWeekendIndex": null}
  ]
}
```

- `summary.totalDaysOff` is the total number of days inside breaks (the planner's objective). It counts days just
  outside the year when a break crosses 1 January or 31 December (PRD, Year edges).
- `breaks` are in date order. `start`/`end` may fall in the previous or next year. `daysOffPerLeave` is
  `days / leaveDates.length`, rounded to one decimal.
- `freeLongWeekends` are runs of 3+ days off that need no leave and are not part of a break. They are shown but not
  counted in `summary`.
- `days` has one entry per date of the chosen year (365 or 366), for the 12-month calendar. `kind` is one of
  `WORKING`, `WEEKLY_OFF`, `HOLIDAY`, `LEAVE` (a holiday on a weekly off is `HOLIDAY`). `planned` is `false` for
  dates on or before today in the current year. `breakIndex` and `freeLongWeekendIndex` point into the lists above.
- `notes` explain situations that are not errors (PRD, Validation and errors):

| `code` | When | `message` |
| --- | --- | --- |
| `NO_LEAVE_USED` | `leaveDays` is 0 | `No leave used. These are the long weekends you already get.` |
| `LEAVES_LEFT_OVER` | More leave days than working days left | `{n} leaves left over: every working day is already a leave day.` |
| `NO_BREAK_POSSIBLE` | `leaveDays` > 0 but no break of the minimum length can be made | `No break of {m}+ days fits {n} leaves with these holidays and weekly offs.` plus, unless the style is already Long weekends, ` Try a shorter break style.` |
| `NO_DAYS_LEFT` | Current year and today is 31 December, so no day is left to plan | `No days left to plan in {year}. Pick {next}.` |

At most one note is returned, chosen in this order: `NO_DAYS_LEFT`, `NO_LEAVE_USED`, `NO_BREAK_POSSIBLE`,
`LEAVES_LEFT_OVER`. When the plan has a break and leaves to spare, every working day in the window is already a leave
(a spare leave next to a break would otherwise make it longer), so spare leaves are always `LEAVES_LEFT_OVER`. If the
window is so short that even all of it can't reach the minimum break length, the note is `NO_BREAK_POSSIBLE`.

With no days left (`NO_DAYS_LEFT`), `planningFrom` and `planningTo` are `null`, `breaks` and `freeLongWeekends` are
empty, `summary` counts are 0 (with `leavesAvailable` and `leavesUnused` equal to `leaveDays`), and every entry in
`days` has `planned: false`.

## `POST /api/plan/ics`

Same request body as `/api/plan`. Returns `200` with `Content-Type: text/calendar; charset=utf-8` and
`Content-Disposition: attachment; filename="leavewise-2027.ics"`.

- One `VEVENT` per break: `DTSTART;VALUE=DATE` is the first day, `DTEND;VALUE=DATE` the day **after** the last day
  (RFC 5545 all-day events), `SUMMARY` like `Break: 4 days off (1 leave)`, `DESCRIPTION` listing the leave dates
  (`Leave: Fri, 5 Mar 2027`) and holidays inside.
- The calendar has `VERSION:2.0`, `PRODID:-//LeaveWise//Leave plan//EN` and `CALSCALE:GREGORIAN`.
- `UID` is derived from the break dates (`<start>-<end>@leavewise`), so importing the same plan again updates the
  events instead of duplicating them. `DTSTAMP` (required by RFC 5545) is the download time in UTC, so two downloads
  differ only in that line.
- CRLF line endings, lines folded at 75 octets, text escaped per RFC 5545 section 3.3.11.
- With no breaks, the file has the calendar headers and no events (most calendar apps import it as empty;
  RFC 5545 strictly asks for at least one component, a trade-off accepted to keep "no breaks" honest).
- Text from users is escaped: every line break form becomes `\n` and other control characters are dropped, so a
  holiday name can never start a new line in the file.

## Nice to have (deferred)

The PRD's Nice-to-have results are deferred until every Must is complete and polished (the owner's brief). Planned
shape, so they can be added without breaking the contract:

| PRD part | Planned addition |
| --- | --- |
| Free long weekends | Already in `/api/plan` (`freeLongWeekends`), because the PRD's 0-leave case needs it; the UI shows them only in that case until the Nice-to-haves are built. |
| Other good breaks | `otherBreaks` in the `/api/plan` response: up to 10 candidate breaks not in the plan, ranked by days off per leave (ties: earlier start). |
| Share link | No API change: the page keeps the inputs in its URL query (`?y=2027&l=12&w=SA,SU&s=LW&h=...`) and plans on load. |

## Page copy beyond the PRD (browser only)

From the Claude Design canvas "LeaveWise UI redesign" (owner-approved 2026-10-10, ADR 0008 amendments). The API
responses above are unchanged; PRD messages stay word for word.

| Where | Text |
| --- | --- |
| Header | "No login", "Nothing stored" (desktop pills); "No login · nothing stored" (phone) |
| Inputs heading | "Turn your leaves into the longest breaks" / desktop: "Enter your balance. We place every leave day for the most days off." / phone: "Every input has a default. Enter your leave days and tap Plan." |
| Leave-day stepper | "One fewer leave day", "One more leave day" (button names); "0 to 60" (desktop), "Whole number from 0 to 60." (phone) |
| Break styles | "3 or more days off", "5 or more days off", "9 or more days off" |
| Holidays (desktop) | "Holidays · {n} of {m} on", "Edit list" / "Done" |
| Holidays (phone) | Row: "Holidays", "{n} of {m} on · tap to review or add"; sheet: "Holidays in {year}", "Done" |
| No bundled list | "No official list for {year} is bundled yet." / "Add your company's holidays below, one by one or as a pasted list." / "Add one", "Paste a list" |
| Adding holidays | Tabs "Add one", "Paste a list"; "Holiday date", "Holiday name", "Add holiday"; "One holiday per line: date, then name" / "Up to 50 lines, like {year}-01-26 Republic Day", "Load holidays"; result "{n} holidays added. {k} lines didn't load." then "Line {n}: {reason}" |
| Phone plan bar | "{year} · {n} leaves · {weekly offs} off · {break style}"; after a plan, "See your plan" |
| Results before the first plan (desktop) | "Your plan shows here" / "Tap Plan my leaves to see your breaks, the days off they give and the whole year on a calendar." |
| Summary | "Your {year} plan"; "{n} leaves → {d} days off"; "across {b} breaks. Every leave is used." or "… {u} leaves unused."; figures "Leaves used" ("{n} of {balance}"), "Days off in breaks", "Breaks", "Days off per leave" (rounded half up like each break's `daysOffPerLeave`; the phone shows the last two); "Add to calendar (.ics)" |
| 0 leave days | "{n} free long weekend(s)" with the `NO_LEAVE_USED` note, then "Free long weekends" |
| `NO_BREAK_POSSIBLE` | The note, then one button per shorter style: "Try Short trips", "Try Long weekends" (a tap plans again with it; disabled while a plan or the holiday list is loading; an invalid input sends nothing and keeps the style); "{u} leaves unused." |
| Inputs changed | "Inputs changed. Tap Plan my leaves to update this plan." with "Plan again" |
| Breaks | "Your breaks", "In date order"; a row: dates, "{d} days off · leave {dates}", holidays or "—", "{ratio}" "per leave"; phone: "Leave {dates} · {holidays}", "{d}" "days off", "Show all {n} breaks" / "Show fewer breaks" |
| Calendar | "{year} at a glance" (desktop), "{Month} {year}" with "Previous month" / "Next month" and month chips (phone); legend "Leave", "Holiday", "Weekly off", "In a break" ("Free long weekend" with 0 leaves); day panel "{Fri, 5 Mar} · {Leave day / Holiday, name / Weekly off / Working day}" and "Break {n}: {range}, {d} days off with {holidays}", "Free long weekend: {range}", "Already passed, so it is not planned.", "Not part of a break."; before a tap "Tap a day to see what it is." / "Arrow keys move between days." |

## `GET /actuator/health`

`200 {"status":"UP"}` for the host's health check (exactly that body; Kubernetes probe groups are off). No other
actuator endpoint is exposed, and the endpoint is not rate limited.

## Errors

Every error is `application/problem+json`. `detail` is the message to show; `errors` lists each field problem;
`instance` (added by Spring) is the request path. Known exception: a URL with a malformed percent-escape such as
`/api/%zz` is refused by the servlet container with its own plain 400 page before the app sees it.

```json
{
  "type": "urn:leavewise:problem:invalid-input",
  "title": "Check your inputs",
  "status": 400,
  "detail": "Enter a whole number from 0 to 60.",
  "errors": [{"field": "leaveDays", "message": "Enter a whole number from 0 to 60."}]
}
```

| Situation | Status | `type` suffix | `message` |
| --- | --- | --- | --- |
| `leaveDays` not a whole number, below 0 or above 60 | 400 | `invalid-input` | `Enter a whole number from 0 to 60.` (PRD) |
| All seven days are weekly offs | 400 | `invalid-input` | `Keep at least one working day in the week.` (PRD) |
| `year` not the current or next year | 400 | `invalid-input` | `Pick {current} or {next}.` |
| `breakStyle` missing or unknown | 400 | `invalid-input` | `Pick a break style: Long weekends, Short trips or Big trips.` |
| A holiday date missing, malformed or outside the year | 400 | `invalid-input` | `Fill in the holiday date.` / `Write holiday dates like {year}-01-26.` / `Holiday {date} is not in {year}.` |
| A holiday name blank, too long, or with line breaks or control characters | 400 | `invalid-input` | `Give each holiday a name.` / `Keep holiday names to 80 characters or fewer.` / `Holiday names can't contain line breaks or control characters.` |
| More than 100 holidays | 400 | `invalid-input` | `Use at most 100 holidays.` |
| A required field missing or `null`, or a weekly off that isn't a day name | 400 | `invalid-input` | `Fill in {field}.` / `Weekly offs must be day names, like SATURDAY.` |
| Pasted `text` over 8,000 characters, or not text | 400 | `invalid-input` | `Paste at most 50 lines (8,000 characters).` / `Paste the holiday list as text.` |
| Body is not valid JSON, or repeats a key | 400 | `unreadable` | `The request could not be read. Reload the page and try again.` |
| Body over 32 KB | 413 | `too-large` | `The request is too large. Paste at most 50 holiday lines.` |
| Rate limit reached | 429 | `rate-limited` | `Too many requests. Wait a minute and try again.` |
| Unknown path under `/api` | 404 | `not-found` | `There is nothing here.` |
| Wrong HTTP method | 405 | `method-not-allowed` | `Use the method docs/API.md lists.` |
| `Accept` header the endpoint can't answer | 406 | `not-acceptable` | `Accept application/json (or text/calendar for the .ics download).` |
| Body not sent as JSON | 415 | `unsupported-media-type` | `Send JSON with Content-Type: application/json.` |
| Unexpected server error | 500 | `internal` | `Something went wrong on our side. Try again.` (details go to the server log without inputs) |

Messages marked (PRD) are word for word from the PRD; the others are this project's choices, recorded in
`docs/ai/PROGRESS.md` for review at STOP 1.
