package org.smalltech.hashtaglocal_backend.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Configures forwarded prefix handling for deployments behind a reverse proxy that rewrites the URL
 * path (e.g., GCP load balancer rewriting /local/* to /*).
 *
 * <p>When {@code app.forwarded-prefix} is set (e.g., {@code /local}), this config registers two
 * filters:
 *
 * <ol>
 *   <li>A filter that injects the {@code X-Forwarded-Prefix} header into the request
 *   <li>Spring's {@link ForwardedHeaderFilter} which processes forwarded headers and adjusts the
 *       context path accordingly
 * </ol>
 *
 * <p>This ensures that Swagger UI and other URL-generating components produce correct URLs that
 * include the proxy prefix.
 *
 * <p>Set via environment variable: {@code APP_FORWARDED_PREFIX=/local}
 */
@Configuration
public class ForwardedPrefixConfig {

  @Bean
  @ConditionalOnProperty("app.forwarded-prefix")
  public FilterRegistrationBean<Filter> forwardedPrefixInjectionFilter(
      @Value("${app.forwarded-prefix}") String prefix) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(
        new Filter() {
          @Override
          public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
              throws IOException, ServletException {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletRequestWrapper wrappedRequest =
                new HttpServletRequestWrapper(httpRequest) {
                  @Override
                  public String getHeader(String name) {
                    if ("X-Forwarded-Prefix".equalsIgnoreCase(name)) {
                      return prefix;
                    }
                    return super.getHeader(name);
                  }

                  @Override
                  public Enumeration<String> getHeaders(String name) {
                    if ("X-Forwarded-Prefix".equalsIgnoreCase(name)) {
                      return Collections.enumeration(List.of(prefix));
                    }
                    return super.getHeaders(name);
                  }

                  @Override
                  public Enumeration<String> getHeaderNames() {
                    List<String> names = Collections.list(super.getHeaderNames());
                    names.add("X-Forwarded-Prefix");
                    return Collections.enumeration(names);
                  }
                };
            chain.doFilter(wrappedRequest, response);
          }
        });
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.setName("forwardedPrefixInjectionFilter");
    return registration;
  }

  @Bean
  @ConditionalOnProperty("app.forwarded-prefix")
  public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {
    FilterRegistrationBean<ForwardedHeaderFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new ForwardedHeaderFilter());
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
    registration.setName("forwardedHeaderFilter");
    return registration;
  }
}
