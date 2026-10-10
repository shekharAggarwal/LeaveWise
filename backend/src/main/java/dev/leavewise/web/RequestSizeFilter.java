package dev.leavewise.web;

import dev.leavewise.api.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses API request bodies over the limit with 413: up front from Content-Length, and while
 * reading when the length isn't declared (chunked uploads). Registered for {@code /api/*}.
 */
class RequestSizeFilter extends OncePerRequestFilter {

  private final long maxBytes;
  private final ProblemWriter problems;

  RequestSizeFilter(long maxBytes, ProblemWriter problems) {
    this.maxBytes = maxBytes;
    this.problems = problems;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (request.getContentLengthLong() > maxBytes) {
      problems.write(response, Problems.tooLarge());
      return;
    }
    chain.doFilter(new LimitedRequest(request, maxBytes), response);
  }

  private static final class LimitedRequest extends HttpServletRequestWrapper {
    private final long maxBytes;
    private ServletInputStream stream;

    LimitedRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (stream == null) {
        stream = new LimitedStream(super.getInputStream(), maxBytes);
      }
      return stream;
    }

    @Override
    public java.io.BufferedReader getReader() throws IOException {
      String encoding = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
      return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(), encoding));
    }
  }

  private static final class LimitedStream extends ServletInputStream {
    private final ServletInputStream in;
    private final long maxBytes;
    private long read;

    LimitedStream(ServletInputStream in, long maxBytes) {
      this.in = in;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int b = in.read();
      if (b >= 0) {
        count(1);
      }
      return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int n = in.read(buffer, offset, length);
      if (n > 0) {
        count(n);
      }
      return n;
    }

    private void count(int n) throws RequestTooLargeException {
      read += n;
      if (read > maxBytes) {
        throw new RequestTooLargeException();
      }
    }

    @Override
    public boolean isFinished() {
      return in.isFinished();
    }

    @Override
    public boolean isReady() {
      return in.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      in.setReadListener(listener);
    }
  }
}
