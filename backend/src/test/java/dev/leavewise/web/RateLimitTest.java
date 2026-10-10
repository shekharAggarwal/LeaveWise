package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import dev.leavewise.api.ApiTestConfig;
import dev.leavewise.api.Reply;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Its own context: a burst of 3 so the limit is reached quickly; the clock never moves. */
@SpringBootTest(
    properties = {
      "leavewise.holidays.location=classpath*:fixtures/holidays/india-*.json",
      "leavewise.holidays.allow-test-data=true",
      "leavewise.rate-limit.requests-per-minute=60",
      "leavewise.rate-limit.burst=3"
    })
@AutoConfigureMockMvc
@Import(ApiTestConfig.class)
class RateLimitTest {

  @Autowired MockMvc mvc;

  private Reply from(String ip, String url) throws Exception {
    MockHttpServletRequestBuilder request =
        get(url)
            .with(
                r -> {
                  r.setRemoteAddr(ip);
                  return r;
                });
    return new Reply(mvc.perform(request).andReturn());
  }

  @Test
  void requestsOverTheBurstGet429WithRetryAfterAndProblemJson() throws Exception {
    String ip = "203.0.113.10";
    for (int i = 1; i <= 3; i++) {
      assertThat(from(ip, "/api/options").status())
          .as("request %d of the burst of 3 must pass", i)
          .isEqualTo(200);
    }

    Reply limited = from(ip, "/api/options");

    limited.assertProblem(429, "rate-limited", "Too many requests. Wait a minute and try again.");
    assertThat(limited.header("Retry-After"))
        .as("docs/API.md: over the limit gets Retry-After (seconds)")
        .isNotNull()
        .matches("\\d+");
    assertThat(Integer.parseInt(limited.header("Retry-After"))).isBetween(1, 60);
  }

  @Test
  void aDifferentIpIsNotLimited() throws Exception {
    String busy = "203.0.113.20";
    for (int i = 0; i < 4; i++) {
      from(busy, "/api/options");
    }
    assertThat(from(busy, "/api/options").status()).as("the busy IP is limited").isEqualTo(429);

    assertThat(from("203.0.113.21", "/api/options").status())
        .as("the limit is per client IP, so another IP still gets through")
        .isEqualTo(200);
  }

  @Test
  void onlyApiPathsAreLimited() throws Exception {
    String ip = "203.0.113.30";
    for (int i = 0; i < 6; i++) {
      from(ip, "/api/options");
    }

    assertThat(from(ip, "/actuator/health").status())
        .as("docs/API.md: the limit is on /api/**, the host health check must keep working")
        .isEqualTo(200);
    assertThat(from(ip, "/some/client/route").status())
        .as("the page itself is not rate limited")
        .isNotEqualTo(429);
  }
}
