# syntax=docker/dockerfile:1.7
# One image serves the page and the API (ADR 0001).
#   docker build -t leavewise .
#   docker run --rm -p 8080:8080 leavewise
# Tests are not run here: CI runs scripts/quality-gate.sh before building the image.
# Behind a TLS-inspecting proxy, pass its CA certificates (one PEM file) as a build secret:
#   docker build --secret id=proxy_ca,src=/path/to/proxy-ca.crt -t leavewise .
# (docs/DEPLOYMENT.md). Without the secret, the build uses the images' own trust stores.

# 1. The React page (Node 22, the version in package.json engines).
FROM node:22.22.0-alpine3.22 AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json frontend/.npmrc ./
RUN --mount=type=secret,id=proxy_ca,required=false \
    if [ -f /run/secrets/proxy_ca ]; then export NODE_EXTRA_CA_CERTS=/run/secrets/proxy_ca; fi; \
    npm ci --no-fund --no-audit
COPY frontend/ ./
# Also runs scripts/check-dist-csp.mjs, so an image never ships a page that breaks the CSP.
RUN npm run build

# 2. The Spring Boot jar with the page in its static resources (Maven 3.9.11, as in the wrapper).
FROM maven:3.9.11-eclipse-temurin-21-alpine AS backend
WORKDIR /src/backend
RUN --mount=type=secret,id=proxy_ca,required=false \
    if [ -f /run/secrets/proxy_ca ]; then \
      mkdir /tmp/ca && cd /tmp/ca && \
      awk '/BEGIN CERTIFICATE/{n++} n{print > ("cert" n ".pem")}' /run/secrets/proxy_ca && \
      for cert in cert*.pem; do \
        keytool -importcert -noprompt -cacerts -storepass changeit -alias "build-proxy-$cert" -file "$cert"; \
      done && cd / && rm -rf /tmp/ca; \
    fi
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist ./src/main/resources/static
# Maven ignores the HTTPS_PROXY variable, so a proxy passed as --build-arg HTTPS_PROXY=http://host:port
# becomes a settings.xml proxy (no credentials).
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "${HTTPS_PROXY:-}" ]; then \
      proxy="${HTTPS_PROXY#*://}"; proxy="${proxy%/}"; \
      printf '<settings><proxies><proxy><id>build</id><active>true</active><protocol>https</protocol><host>%s</host><port>%s</port></proxy></proxies></settings>\n' \
        "${proxy%:*}" "${proxy##*:}" > /tmp/settings.xml; \
    else \
      printf '<settings/>\n' > /tmp/settings.xml; \
    fi; \
    mvn -B -ntp -s /tmp/settings.xml package -DskipTests

# 3. Runtime: a JRE only, a non-root user, the port from $PORT (hosts like Render set it).
FROM eclipse-temurin:21.0.12.1_1-jre-alpine
RUN addgroup -S leavewise && adduser -S -G leavewise leavewise
WORKDIR /app
COPY --from=backend /src/backend/target/leavewise.jar ./leavewise.jar
USER leavewise
ENV PORT=8080 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO /dev/null "http://127.0.0.1:${PORT}/actuator/health" || exit 1
ENTRYPOINT ["java", "-jar", "/app/leavewise.jar"]
