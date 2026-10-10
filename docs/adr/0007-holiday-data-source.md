# 0007. Holiday data from an official Government of India source only

Status: Accepted (2026-10-09); amended 2026-10-10 (company list for 2026, see Amendment)

## Context

The PRD default is the "India public holiday list for that year", with "one bundled JSON file per year with its
source noted". The owner's rule: holiday dates come only from an official Government of India source, with the source
URL in each file, and nothing is invented. The PRD's first open question asks whether to preload the central
government list or a typical private-company list.

## Decision

- Preload the **Central Government holidays** published each year by the Department of Personnel and Training
  (DoPT) office memorandum for central government offices in Delhi/New Delhi: the compulsory gazetted holidays plus
  the optional ones chosen for Delhi. Restricted holidays are not preloaded. *(Recommended answer to open question 1,
  for the owner to confirm at STOP 1.)* There is no official "typical private-company list", so that option can't
  meet the sourcing rule; users untick or add holidays instead, as the PRD journey already allows.
- File format, one per year, `backend/src/main/resources/holidays/india-YYYY.json`:

```json
{
  "country": "IN",
  "year": 2027,
  "list": "Central Government holidays, Delhi/New Delhi (DoPT)",
  "source": {
    "publisher": "Department of Personnel and Training, Government of India",
    "title": "<the office memorandum's title and number>",
    "url": "https://<an official gov.in or nic.in address>",
    "retrievedOn": "YYYY-MM-DD"
  },
  "holidays": [{"date": "YYYY-MM-DD", "name": "<name as published>"}]
}
```

- The loader refuses a file at startup if `source.url` isn't `https://` on a `gov.in` or `nic.in` host, if any date
  is outside `year`, or if it carries `"testData": true`. Test fixtures live only under `src/test/resources`, are
  marked `"testData": true`, and use invented names such as "Fixture Holiday", so they can't pass as real data.
- Building the real files is a LAPTOP step, because government sites aren't reachable from the cloud VM. Any date that
  can't be checked against the source document goes into `docs/HUMAN_TODO.md` instead of the file.
- The page shows the source and reminds users to check their company's official list (PRD).

## Consequences

- Until the laptop step, the app has no bundled list (`available: false`) and users add their own holidays.
- The preloaded list fits central government staff exactly and is only a starting point for private employers; the
  reminder and the untick/add/paste controls cover the difference.

## Amendment (2026-10-10): the owner's company list for 2026, floaters unticked

The owner shared FiftyFive Technologies' 2026 holiday calendar from Keka (a screenshot) and asked to prefill it. Asked
whether that fits this ADR's government-only rule, the owner chose to bundle it as a company list, with floater leaves
listed but unticked (docs/ai/REVIEW_LOG.md, 2026-10-10). So:

- A file may set `"kind": "company"` (default `"government"`). A company list's `source.url` must be https on any
  host; a government list still needs a gov.in or nic.in host. Any other `kind` is refused at startup.
- A holiday may set `"optional": true` (a floater). `GET /api/holidays` returns it with `optional: true`; the page
  lists it unticked with a "Floater" tag. Floaters of an adjacent year never extend a break across 1 January
  (ADR 0006), since nobody has chosen them.
- `holidays/india-2026.json` is "FiftyFive Technologies holidays 2026": 24 holidays (11 fixed, 13 floaters),
  transcribed from the owner's screenshot with names exactly as shown; every date's weekday was checked against the
  weekday the screenshot shows (all 24 match). Source: FiftyFive Technologies Private Limited, Keka holiday calendar,
  https://fiftyfive.keka.com, retrieved 2026-10-10.
- 2027 has no bundled list yet; the government list (or the company's 2027 calendar) is still a laptop step.
