package dev.leavewise.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.json.JsonMapper;

/**
 * Registers the web filters in order: headers first, so even refusals carry them. The limits use
 * container URL patterns, which match the decoded path without ;parameters (code review, Phase 3).
 */
@Configuration
class WebConfig {

  @Bean
  FilterRegistrationBean<SecurityHeadersFilter> securityHeaders() {
    FilterRegistrationBean<SecurityHeadersFilter> bean =
        new FilterRegistrationBean<>(new SecurityHeadersFilter());
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimit(
      @Value("${leavewise.rate-limit.requests-per-minute}") int perMinute,
      @Value("${leavewise.rate-limit.burst}") int burst,
      JsonMapper json) {
    FilterRegistrationBean<RateLimitFilter> bean =
        new FilterRegistrationBean<>(
            new RateLimitFilter(perMinute, burst, new ProblemWriter(json), System::nanoTime));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    return bean;
  }

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSize(
      @Value("${leavewise.request.max-bytes}") long maxBytes, JsonMapper json) {
    FilterRegistrationBean<RequestSizeFilter> bean =
        new FilterRegistrationBean<>(new RequestSizeFilter(maxBytes, new ProblemWriter(json)));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    return bean;
  }
}
