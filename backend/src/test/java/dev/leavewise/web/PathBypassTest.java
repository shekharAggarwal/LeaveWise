package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.api.ApiTestConfig;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Code review finding 1 (Phase 3): the filters must apply to every path the container routes to the
 * API, not only to the raw URI prefix "/api/". Runs against a real server because MockMvc doesn't
 * decode paths or strip path parameters the way the container does.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "leavewise.holidays.location=classpath*:fixtures/holidays/india-*.json",
      "leavewise.holidays.allow-test-data=true",
      "leavewise.rate-limit.requests-per-minute=60",
      "leavewise.rate-limit.burst=3"
    })
@Import(ApiTestConfig.class)
class PathBypassTest {

  @LocalServerPort int port;

  private final HttpClient http = HttpClient.newHttpClient();

  // Each test uses its own client IP so their rate-limit buckets don't interfere. The server
  // trusts X-Forwarded-For from localhost (server.forward-headers-strategy=native).
  private HttpResponse<String> send(String ip, String method, String path, String body)
      throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", ip);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void encodedApiPathIsStillRateLimited() throws Exception {
    List<Integer> statuses = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      statuses.add(send("203.0.113.1", "GET", "/%61pi/options", null).statusCode());
    }
    assertThat(statuses)
        .as("PRD: a rate limit per IP, whatever the spelling of /api")
        .contains(429);
  }

  @Test
  void pathParameterDoesNotSkipTheSizeLimit() throws Exception {
    String big = "{\"year\":2027,\"text\":\"" + "x".repeat(40_000) + "\"}";
    HttpResponse<String> response = send("203.0.113.2", "POST", "/api;x=1/holidays/parse", big);
    assertThat(response.statusCode()).as("PRD: a request size limit").isEqualTo(413);
  }

  @Test
  void encodedApiPathGetsNoStore() throws Exception {
    HttpResponse<String> response = send("203.0.113.3", "GET", "/%61pi/holidays?year=2027", null);
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
  }

  @Test
  void undeclaredLengthBodyOverTheLimitIs413() throws Exception {
    // A streamed body has no Content-Length, so the limit must apply while reading.
    byte[] chunk = "x".repeat(1024).getBytes(java.nio.charset.StandardCharsets.UTF_8);
    List<byte[]> parts = new ArrayList<>();
    parts.add("{\"year\":2027,\"text\":\"".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    for (int i = 0; i < 40; i++) {
      parts.add(chunk);
    }
    parts.add("\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/holidays/parse"))
            .header("Content-Type", "application/json")
            .header("X-Forwarded-For", "203.0.113.4")
            .POST(HttpRequest.BodyPublishers.ofByteArrays(parts))
            .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(413);
  }
}
