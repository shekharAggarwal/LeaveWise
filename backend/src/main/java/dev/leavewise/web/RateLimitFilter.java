package dev.leavewise.web;

import dev.leavewise.api.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token bucket per client IP on /api/** (docs/API.md: 60 requests a minute, burst 20). The IP is
 * {@code getRemoteAddr()}; behind the host's proxy the server's forward-headers strategy sets it
 * from X-Forwarded-For. Only the bucket state is kept, never request content.
 */
class RateLimitFilter extends OncePerRequestFilter {

  /** Bounds memory: idle full buckets are dropped once the map grows past this. */
  static final int MAX_TRACKED_CLIENTS = 10_000;

  private final double capacity;
  private final double tokensPerNano;
  private final ProblemWriter problems;
  private final Clock clock;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

  RateLimitFilter(int requestsPerMinute, int burst, ProblemWriter problems, Clock clock) {
    this.capacity = burst;
    this.tokensPerNano = requestsPerMinute / 60e9;
    this.problems = problems;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = nanos();
    if (buckets.size() > MAX_TRACKED_CLIENTS) {
      buckets.values().removeIf(b -> b.isFull(now));
    }
    Bucket bucket = buckets.computeIfAbsent(request.getRemoteAddr(), ip -> new Bucket(now));
    long waitNanos = bucket.take(now);
    if (waitNanos > 0) {
      response.setHeader("Retry-After", String.valueOf((long) Math.ceil(waitNanos / 1e9)));
      problems.write(response, Problems.rateLimited());
      return;
    }
    chain.doFilter(request, response);
  }

  private long nanos() {
    return clock.instant().getEpochSecond() * 1_000_000_000L + clock.instant().getNano();
  }

  private final class Bucket {
    private double tokens = capacity;
    private long updated;

    Bucket(long now) {
      this.updated = now;
    }

    /** Takes one token; returns 0 on success, else the nanoseconds until one is available. */
    synchronized long take(long now) {
      refill(now);
      if (tokens >= 1) {
        tokens -= 1;
        return 0;
      }
      return Math.max(1, (long) Math.ceil((1 - tokens) / tokensPerNano));
    }

    synchronized boolean isFull(long now) {
      refill(now);
      return tokens >= capacity;
    }

    private void refill(long now) {
      tokens = Math.min(capacity, tokens + (now - updated) * tokensPerNano);
      updated = now;
    }
  }
}
