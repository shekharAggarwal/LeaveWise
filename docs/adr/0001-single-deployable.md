# 0001. One Spring Boot jar serves the page and the API

Status: Accepted (2026-10-09)

## Context

The PRD asks for "one Spring Boot app [that] serves both the React page and the API at a public URL for the
judges". The owner deploys it alone, on a free or cheap host, within three weeks.

## Decision

Build the React page with Vite and package the output into the Spring Boot jar's static resources. A multi-stage
Dockerfile runs `npm ci && npm run build`, then `./mvnw package` with the built page copied in, then runs the jar on
a JRE image. Spring Boot forwards every non-API, non-file path to `index.html`.

## Consequences

- One image, one URL, one health check, no CORS: the page calls `/api/*` on its own origin, so the CSP can be
  `default-src 'self'`.
- Frontend and backend release together; there's no independent frontend deploy. That's fine for a one-page MVP.
- In development the two run separately (`npm run dev` proxies `/api` to port 8080).
