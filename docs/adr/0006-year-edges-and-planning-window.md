# 0006. Year edges and planning from tomorrow

Status: Accepted (2026-10-09)

## Context

PRD rules: "leave days stay inside the chosen year, but a break also counts the weekly offs and known holidays just
before 1 January and just after 31 December", and "for the current year, only days after today are planned". The
edge case list includes "a break that crosses into the next or previous year" and "current year, planning from today".

## Decision

- **Planning window**: from 1 January (next year) or from tomorrow (current year) to 31 December. Leave days are only
  placed on working days inside the window. "Today" comes from an injected `Clock` in Asia/Kolkata.
- **Trailing edge**: the horizon extends past 31 December over the unbroken run of days off that starts on 1 January
  of the next year (weekly offs, plus holidays from the next year's bundled list if it exists). The first working day
  ends it.
- **Leading edge**: when the window starts on 1 January, the horizon extends back over the unbroken run of days off
  ending on 31 December of the previous year (weekly offs, plus the previous year's bundled holidays if they exist).
- **Current year**: when the window starts tomorrow, there is no leading edge. Days up to and including today are not
  counted, even if they are days off, because they're already over. *(This is an interpretation; raised at STOP 1.)*
- "Known holidays" outside the chosen year come only from the bundled lists; the user's custom holidays must be in
  the chosen year (PRD, Validation and errors). If the adjacent year's list isn't bundled, only weekly offs count.

## Consequences

- Break dates and `totalDaysOff` can include days outside the year; the API returns full `start`/`end` dates.
- The 12-month calendar shows only the chosen year; a break that crosses the edge is labelled with its full dates.
- Leap years need no special code because all date arithmetic uses `java.time.LocalDate`; 2028 is tested anyway.
