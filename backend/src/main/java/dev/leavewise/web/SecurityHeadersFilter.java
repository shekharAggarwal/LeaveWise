package dev.leavewise.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security headers on every response, errors and static files included (docs/API.md, Conventions).
 */
class SecurityHeadersFilter extends OncePerRequestFilter {

  static final String CSP =
      "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; "
          + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    response.setHeader("Content-Security-Policy", CSP);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
    if (request.isSecure()) {
      response.setHeader("Strict-Transport-Security", "max-age=31536000");
    }
    if (request.getRequestURI().startsWith("/api/")) {
      response.setHeader("Cache-Control", "no-store");
    }
    chain.doFilter(request, response);
  }
}
