package dev.leavewise.web;

import dev.leavewise.api.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token bucket per client on /api/** (docs/API.md: 60 requests a minute, burst 20). Registered for
 * {@code /api/*} so the container, not a string prefix, decides what is an API path. Clients are
 * keyed by IPv4 address or IPv6 /64 prefix; behind the host's proxy the server's forward-headers
 * strategy sets the address from X-Forwarded-For. Only bucket state is kept, never request content.
 */
class RateLimitFilter extends OncePerRequestFilter {

  /**
   * Bounds memory: once the map grows past this, idle full buckets are swept (at most once a
   * second).
   */
  static final int MAX_TRACKED_CLIENTS = 10_000;

  private static final long SECOND = 1_000_000_000L;

  private final double capacity;
  private final double tokensPerNano;
  private final ProblemWriter problems;
  private final LongSupplier nanoTime;
  private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final AtomicLong lastSweep = new AtomicLong(Long.MIN_VALUE);

  /**
   * @param nanoTime a monotonic clock in nanoseconds ({@code System::nanoTime} in production)
   */
  RateLimitFilter(int requestsPerMinute, int burst, ProblemWriter problems, LongSupplier nanoTime) {
    this.capacity = burst;
    this.tokensPerNano = requestsPerMinute / 60.0 / SECOND;
    this.problems = problems;
    this.nanoTime = nanoTime;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long now = nanoTime.getAsLong();
    sweepIfNeeded(now);
    Bucket bucket =
        buckets.computeIfAbsent(clientKey(request.getRemoteAddr()), k -> new Bucket(now));
    long waitNanos = bucket.take(now);
    if (waitNanos > 0) {
      response.setHeader("Retry-After", String.valueOf((waitNanos + SECOND - 1) / SECOND));
      problems.write(response, Problems.rateLimited());
      return;
    }
    chain.doFilter(request, response);
  }

  private void sweepIfNeeded(long now) {
    long last = lastSweep.get();
    if (buckets.size() > MAX_TRACKED_CLIENTS
        && (last == Long.MIN_VALUE || now - last >= SECOND)
        && lastSweep.compareAndSet(last, now)) {
      buckets.values().removeIf(b -> b.isFull(now));
    }
  }

  /** IPv4 as is; IPv6 by its /64 prefix, since one client commonly holds a whole /64. */
  static String clientKey(String remoteAddr) {
    if (remoteAddr == null || !remoteAddr.contains(":")) {
      return String.valueOf(remoteAddr);
    }
    try {
      byte[] bytes = InetAddress.getByName(remoteAddr).getAddress();
      if (bytes.length != 16) {
        return remoteAddr;
      }
      StringBuilder key = new StringBuilder("v6:");
      for (int i = 0; i < 8; i++) {
        key.append(String.format("%02x", bytes[i]));
      }
      return key.toString();
    } catch (UnknownHostException e) {
      return remoteAddr;
    }
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
      long elapsed = Math.max(0, now - updated); // a clock stepping back never removes tokens
      tokens = Math.min(capacity, tokens + elapsed * tokensPerNano);
      updated = Math.max(updated, now);
    }
  }
}
