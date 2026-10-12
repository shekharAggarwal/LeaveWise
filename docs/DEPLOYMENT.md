# Deployment

LeaveWise is one Docker image: a Spring Boot jar that serves the React page and the API on one port (ADR 0001). It
stores nothing (ADR 0002) and needs no secrets, so deploying it means building the image and running it behind
HTTPS. The default host is Render, set up by [`render.yaml`](../render.yaml).

## Environments

| Environment | Where | How it runs | Holiday data |
| --- | --- | --- | --- |
| Development | Laptop | `cd backend && ./mvnw spring-boot:run` (port 8080) and `cd frontend && npm run dev` (Vite, proxies `/api` to 8080) | Bundled files in `backend/src/main/resources/holidays/` |
| Local container | Laptop or CI | `docker compose up` ([`compose.yaml`](../compose.yaml): builds the image each time, port 8080, 512 MB like Render Free), or `docker build -t leavewise . && docker run --rm -p 8080:8080 leavewise` | Bundled files, baked into the jar |
| CI | GitHub Actions | `.github/workflows/ci.yml`: quality gate, dependency scan, image build and smoke test; nothing is pushed or deployed | Test fixtures for tests; bundled files in the image |
| Production | Render, one web service | `render.yaml`, deployed from `main` after CI passes | Bundled files, baked into the jar |

## Configuration

Every setting has a working default in `backend/src/main/resources/application.properties`. Override any of them
with an environment variable (Spring's relaxed binding: `leavewise.rate-limit.burst` becomes
`LEAVEWISE_RATELIMIT_BURST`; checked on the image: a burst of 3 gave `200 200 200 429`). None is a secret.

| Setting | Default | Notes |
| --- | --- | --- |
| `PORT` | `8080` | Render sets it; the app listens on it. |
| `LEAVEWISE_RATELIMIT_REQUESTSPERMINUTE` | `60` | Steady rate per client IP for `/api/*`. |
| `LEAVEWISE_RATELIMIT_BURST` | `20` | Requests a client can make at once before the steady rate applies. |
| `LEAVEWISE_REQUEST_MAXBYTES` | `32768` | Largest request body, in bytes. |
| `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` | Tomcat's default (private and loopback addresses) | Which peers may set `X-Forwarded-For`. See below. |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError` (in the image) | The JVM sizes its heap from the container's memory. Measured: about 150 MB used under a 512 MB limit after 50 full-year plans. |

The holiday files are part of the jar, so changing holiday data means a new commit and deploy, never a setting.
`leavewise.holidays.allow-test-data` stays `false` in every deployed environment.

### Client IP, the rate limit and the proxy

The rate limit keys on the client IP. Behind a host's proxy, Tomcat takes that IP from `X-Forwarded-For`, but only
when the request comes from a trusted proxy (by default any private or loopback address). This means:

- **On Render**, requests reach the app only through Render's proxy, so the header is set by Render. After the first
  deploy, run the check under "After every deploy" to confirm that two different devices get separate limits. If
  they share one limit, Render's proxy is reaching the app from an address Tomcat doesn't trust: set
  `SERVER_TOMCAT_REMOTEIP_INTERNALPROXIES` to a regular expression matching it.
- **Anywhere else**, never publish the app's port except through the proxy. A client that can reach the container
  directly from a private network could set `X-Forwarded-For` itself and get a fresh limit with each value.

## Deploy to Render (first time)

Render was chosen because it builds a Dockerfile straight from GitHub, gives HTTPS on an `onrender.com` address,
and has a free instance type. Plan and price facts could not be checked on render.com from the build VM (the
site is blocked there). Third-party pages dated 2026 say: free web services sleep after 15 minutes without traffic
and take up to about a minute to wake; the Starter instance (512 MB, 0.5 CPU, always on) is about $7 a month. Check
both on render.com/pricing before choosing.

Steps (owner only: they create an account and may cost money if a paid plan is picked):

1. Merge `claude/inspiring-darwin-2m8wjb` into `main` on GitHub, and check that the CI workflow is green on `main`.
2. Sign in to [Render](https://render.com) with GitHub, and give Render access to this repository only.
3. Optional, before step 4: choose a region nearer India. Check Render's region list (the region picker on a new
   web service, and the `region` values in Render's Blueprint reference); if one is nearer India than the default,
   add `region: <value>` under `plan` in `render.yaml`, commit and push to `main`. The region can't be changed after
   the service exists.
4. In the Render Dashboard: **New** > **Blueprint**, pick this repository and branch `main`. Render reads
   `render.yaml` and shows one web service, `leavewise`, on the Free plan. Apply it.
5. Wait for the first build and deploy (the build runs the Dockerfile, about 1 to 2 minutes when the build cache
   is warm, longer the first time). The service turns **Live** when `/actuator/health` answers `UP`.
6. Open the `https://leavewise-....onrender.com` address and run the checks below.
7. Put the address in `README.md` (the demo link placeholder) and commit.

Later deploys are automatic: each commit on `main` deploys once its GitHub checks pass (`autoDeployTrigger:
checksPass`).

## After every deploy

1. `curl -s https://<address>/actuator/health` returns `{"status":"UP"}`.
2. The page loads on a phone and on a laptop; plan with the defaults; the results and calendar show; the `.ics`
   download opens in a calendar app.
3. Headers: `curl -sI https://<address>/` shows `Content-Security-Policy`, `Strict-Transport-Security`,
   `X-Content-Type-Options: nosniff` and `Referrer-Policy: no-referrer`.
4. Real-browser check from the laptop: `cd frontend && npm run check:browser -- https://<address>` passes.
5. Rate limit per client: from the laptop run
   `for i in $(seq 1 25); do curl -s -o /dev/null -w '%{http_code} ' https://<address>/api/options; done`;
   the last few are `429`. Straight away, open the page on a phone on mobile data: it must load normally. If the
   phone also gets "Too many requests", see "Client IP, the rate limit and the proxy".

## Roll back

Revert the bad commit on `main`; once CI passes, Render deploys the reverted code. Render's Dashboard also keeps the
service's deploy history, from which an earlier deploy can be restored.

## Building behind a TLS-inspecting proxy

Corporate networks and the cloud build VM re-sign HTTPS traffic. The Dockerfile accepts the proxy's CA certificates
as a build secret and a proxy address as a build argument; neither ends up in the final image:

```sh
docker build \
  --secret id=proxy_ca,src=/path/to/proxy-ca.pem \
  --build-arg HTTPS_PROXY=http://proxy.example:3128 \
  -t leavewise .
```

Maven ignores `HTTPS_PROXY`, so the build writes it into a temporary Maven `settings.xml` (host and port only, no
credentials). In the cloud VM the proxy listens on localhost, so the build there also needs `--network host`.
