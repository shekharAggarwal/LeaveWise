# 0007. Holiday data from an official Government of India source only

Status: Accepted (2026-10-09)

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
