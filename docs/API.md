# API

Base path `/api`. JSON in and out (UTF-8). Nothing is stored and no request is logged. The browser checks inputs
for fast feedback, and the server checks them again; the server is the authority.

> All dates and holiday names in the examples below are **made-up example values**, not real holiday data.

## Conventions

| Topic | Rule |
| --- | --- |
| Dates | ISO `YYYY-MM-DD` (calendar dates, no time zone). The server's "today" is in Asia/Kolkata. |
| Weekly offs | `MONDAY` … `SUNDAY` (Java `DayOfWeek` names). Order and duplicates don't matter. |
| Break styles | `LONG_WEEKENDS` (3+ days), `SHORT_TRIPS` (5+ days), `BIG_TRIPS` (9+ days). |
| Request size | Bodies over 32 KB get `413`. |
| Rate limit | 60 requests per minute per client IP on `/api/**`, with a burst of 20; over it gets `429` and `Retry-After`. |
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
    {"date": "2027-08-19", "names": ["Example Holiday B", "Example Holiday C"]}
  ]
}
```

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

`text` is at most 8,000 characters. Blank lines are skipped but still count for line numbers.

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
| Name over 80 characters | `Keep the name under 80 characters.` |
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
    {"date": "2027-08-19", "name": "Example Holiday B"}
  ]
}
```

| Field | Rule |
| --- | --- |
| `year` | One of `options.years`. For the current year, only days after today are planned. |
| `leaveDays` | Whole number from 0 to 60. `12.5`, `"12"`, `-1` and `61` are rejected. |
| `weeklyOffs` | 0 to 6 distinct days; at least one working day must remain in the week. |
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
    {"start": "2027-08-19", "end": "2027-08-22", "days": 4,
     "holidays": [{"date": "2027-08-19", "names": ["Example Holiday B"]}]}
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
| `LEAVES_UNUSED` | Some leaves would not make any break longer | `{n} leaves unused: no break gets longer with them.` |
| `NO_BREAK_POSSIBLE` | `leaveDays` > 0 but no break of the minimum length can be made | `No break of {m}+ days fits {n} leaves with these holidays and weekly offs.` plus, unless the style is already Long weekends, ` Try a shorter break style.` |

## `POST /api/plan/ics`

Same request body as `/api/plan`. Returns `200` with `Content-Type: text/calendar; charset=utf-8` and
`Content-Disposition: attachment; filename="leavewise-2027.ics"`.

- One `VEVENT` per break: `DTSTART;VALUE=DATE` is the first day, `DTEND;VALUE=DATE` the day **after** the last day
  (RFC 5545 all-day events), `SUMMARY` like `Break: 4 days off (1 leave)`, `DESCRIPTION` listing the leave dates
  (`Leave: Fri, 5 Mar 2027`) and holidays inside.
- `UID` is derived from the break dates, so downloading the same plan twice gives the same file.
- CRLF line endings, lines folded at 75 octets, text escaped per RFC 5545 section 3.3.11.
- With no breaks, the file is still valid and has no events.

## `GET /actuator/health`

`200 {"status":"UP"}` for the host's health check. No other actuator endpoint is exposed.

## Errors

Every error is `application/problem+json`. `detail` is the message to show; `errors` lists each field problem.

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
| A holiday date outside the year, or a bad name | 400 | `invalid-input` | `Holiday {date} is not in {year}.` / `Keep holiday names between 1 and 80 characters.` |
| More than 100 holidays | 400 | `invalid-input` | `Use at most 100 holidays.` |
| Body is not valid JSON | 400 | `unreadable` | `The request could not be read. Reload the page and try again.` |
| Body over 32 KB | 413 | `too-large` | `The request is too large. Paste at most 50 holiday lines.` |
| Rate limit reached | 429 | `rate-limited` | `Too many requests. Wait a minute and try again.` |
| Unexpected server error | 500 | `internal` | `Something went wrong on our side. Try again.` (details go to the server log without inputs) |

Messages marked (PRD) are word for word from the PRD; the others are this project's choices, recorded in
`docs/ai/PROGRESS.md` for review at STOP 1.
