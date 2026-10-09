# 0002. No database; holiday lists are bundled JSON resources

Status: Accepted (2026-10-09)

## Context

The PRD says "no login, no database, no tracking cookies" and "one bundled JSON file per year with its source
noted". Saved plans and accounts are out of scope.

## Decision

The server is stateless. Each request carries every input. Holiday lists ship as classpath resources
`holidays/india-YYYY.json`, loaded and validated once at startup (every file must name an official source URL;
see ADR 0007). The per-IP rate limiter keeps a small in-memory map with expiry, which is the only server state.

## Consequences

- Nothing to back up, migrate or secure at rest; privacy is simple because there's nothing to leak.
- Updating a holiday list means a new release. That's acceptable: lists change once a year.
- The rate limit is per instance. With one instance (the plan) it's exact; behind several it would be approximate.
