package dev.leavewise.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MvcResult;

/** A response with AssertJ-friendly helpers, so failures carry a readable message. */
public record Reply(MvcResult result) {

  public MockHttpServletResponse response() {
    return result.getResponse();
  }

  public int status() {
    return response().getStatus();
  }

  public String body() {
    try {
      return response().getContentAsString(StandardCharsets.UTF_8);
    } catch (java.io.UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  public String header(String name) {
    return response().getHeader(name);
  }

  public String contentType() {
    return response().getContentType();
  }

  /** Reads a JSON path from the body. */
  public <T> T at(String path) {
    try {
      return JsonPath.read(body(), path);
    } catch (RuntimeException e) {
      throw new AssertionError(
          "Expected JSON path "
              + path
              + " in the response, but got status "
              + status()
              + " with body: "
              + body(),
          e);
    }
  }

  /** Asserts an RFC 9457 problem with the given status, type suffix and detail. */
  public Reply assertProblem(int status, String typeSuffix, String detail) {
    assertThat(status()).as("HTTP status of %s", body()).isEqualTo(status);
    assertThat(contentType())
        .as("errors must be application/problem+json (docs/API.md, Errors)")
        .startsWith("application/problem+json");
    assertThat((String) at("$.type"))
        .as("problem type")
        .isEqualTo("urn:leavewise:problem:" + typeSuffix);
    assertThat((String) at("$.title")).as("problem title").isNotBlank();
    assertThat((Integer) at("$.status")).as("status field in body").isEqualTo(status);
    assertThat((String) at("$.detail")).as("detail is the message to show").isEqualTo(detail);
    assertThat(body()).as("errors array must be present").contains("\"errors\":[");
    return this;
  }
}
