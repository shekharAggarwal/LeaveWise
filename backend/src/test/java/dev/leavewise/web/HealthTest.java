package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.leavewise.api.ApiIntegrationTest;
import dev.leavewise.api.Reply;
import org.junit.jupiter.api.Test;

class HealthTest extends ApiIntegrationTest {

  @Test
  void healthIsUp() throws Exception {
    Reply r = getJson("/actuator/health");

    assertThat(r.status()).as("docs/API.md: GET /actuator/health").isEqualTo(200);
    assertThat((String) r.at("$.status")).isEqualTo("UP");
  }

  @Test
  void otherActuatorEndpointsAreNotExposed() throws Exception {
    for (String path :
        new String[] {
          "/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/mappings"
        }) {
      Reply r = getJson(path);
      assertThat(r.body())
          .as(
              "%s must not return actuator data (docs/API.md: no other actuator endpoint is exposed)",
              path)
          .doesNotContain("propertySources")
          .doesNotContain("activeProfiles")
          .doesNotContain("\"contexts\"")
          .doesNotContain("dispatcherServlet");
      assertThat(r.contentType())
          .as("%s must not answer with actuator JSON", path)
          .satisfiesAnyOf(
              t -> assertThat(t).isNull(), t -> assertThat(t).doesNotContain("actuator"));
    }
  }
}
