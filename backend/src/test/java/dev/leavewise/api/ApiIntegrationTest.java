package dev.leavewise.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base for the MockMvc tests: fixture holiday lists, a movable clock (reset to 2026-10-09 before
 * each test) and limits high enough that only the rate-limit test ever sees a 429.
 */
@SpringBootTest(
    properties = {
      "leavewise.holidays.location=classpath*:fixtures/holidays/india-*.json",
      "leavewise.holidays.allow-test-data=true",
      "leavewise.rate-limit.requests-per-minute=100000",
      "leavewise.rate-limit.burst=100000"
    })
@AutoConfigureMockMvc
@Import(ApiTestConfig.class)
public abstract class ApiIntegrationTest {

  @Autowired protected MockMvc mvc;
  @Autowired protected ApiTestConfig.MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(ApiTestConfig.DEFAULT_NOW);
  }

  protected Reply send(MockHttpServletRequestBuilder request) throws Exception {
    return new Reply(mvc.perform(request).andReturn());
  }

  protected Reply getJson(String url) throws Exception {
    return send(get(url));
  }

  protected Reply postJson(String url, String body) throws Exception {
    return send(jsonPost(url, body));
  }

  protected static MockHttpServletRequestBuilder jsonPost(String url, String body) {
    return post(url)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body.getBytes(StandardCharsets.UTF_8));
  }

  protected Reply plan(PlanJson request) throws Exception {
    return postJson("/api/plan", request.json());
  }
}
