package dev.leavewise.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * Code review findings 4 and 5 (Phase 3): the token bucket refills with time, reports the real wait
 * in Retry-After, survives a clock that steps back, and keys IPv6 clients by their /64.
 */
class RateLimitFilterTest {

  private static final long SECOND = 1_000_000_000L;

  private final AtomicLong now = new AtomicLong(42 * SECOND);
  // 60 a minute = one token per second; burst 2.
  private final RateLimitFilter filter =
      new RateLimitFilter(60, 2, new ProblemWriter(JsonMapper.builder().build()), now::get);

  private MockHttpServletResponse call(String ip) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/options");
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  void burstThenLimitedThenRefilledAfterOneSecond() throws Exception {
    assertThat(call("198.51.100.7").getStatus()).isEqualTo(200);
    assertThat(call("198.51.100.7").getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = call("198.51.100.7");
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("1");

    now.addAndGet(SECOND);
    assertThat(call("198.51.100.7").getStatus()).as("one token refilled after 1 s").isEqualTo(200);
    assertThat(call("198.51.100.7").getStatus()).isEqualTo(429);
  }

  @Test
  void retryAfterIsTheCeilingOfTheWait() throws Exception {
    call("198.51.100.8");
    call("198.51.100.8");
    now.addAndGet(SECOND / 4); // a quarter token back; 0.75 s still to wait
    assertThat(call("198.51.100.8").getHeader("Retry-After")).isEqualTo("1");
  }

  @Test
  void aClockSteppingBackDoesNotTakeTokens() throws Exception {
    call("198.51.100.9");
    now.addAndGet(-5 * SECOND);
    assertThat(call("198.51.100.9").getStatus())
        .as("a backwards step must not drain the bucket")
        .isEqualTo(200);
  }

  @Test
  void ipv6ClientsShareOneBucketPerSlash64() throws Exception {
    assertThat(call("2001:db8:1:2::1").getStatus()).isEqualTo(200);
    assertThat(call("2001:db8:1:2::ffff").getStatus()).isEqualTo(200);
    assertThat(call("2001:db8:1:2:abcd::9").getStatus())
        .as("same /64, so the same bucket")
        .isEqualTo(429);
    assertThat(call("2001:db8:1:3::1").getStatus()).as("another /64").isEqualTo(200);
  }

  @Test
  void ipv4ClientsAreKeyedByFullAddress() throws Exception {
    call("192.0.2.1");
    call("192.0.2.1");
    assertThat(call("192.0.2.2").getStatus()).isEqualTo(200);
  }
}
