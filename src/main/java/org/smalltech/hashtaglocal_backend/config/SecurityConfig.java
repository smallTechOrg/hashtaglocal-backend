package org.smalltech.hashtaglocal_backend.config;

import java.util.Arrays;
import java.util.List;
import org.smalltech.hashtaglocal_backend.security.AccessTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

  private final AccessTokenAuthFilter accessTokenAuthFilter;

  public SecurityConfig(AccessTokenAuthFilter accessTokenAuthFilter) {
    this.accessTokenAuthFilter = accessTokenAuthFilter;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    config.setMaxAge(3600L);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http.cors(Customizer.withDefaults())
        .csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/issue")
                    .authenticated()
                    // account deletion endpoint must be authenticated so only the owning user can
                    // initiate it
                    .requestMatchers(HttpMethod.POST, "/account/delete-request")
                    .authenticated()
                    .requestMatchers(HttpMethod.POST, "/api/v1/portal/**")
                    .hasRole("ADMIN")
                    .requestMatchers(HttpMethod.GET, "/api/v1/media/upload-url")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/issue/**")
                    .authenticated()
                    .requestMatchers("/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .permitAll())
        .exceptionHandling(
            ex -> {
              ex.authenticationEntryPoint(
                  new org.springframework.security.web.authentication.HttpStatusEntryPoint(
                      org.springframework.http.HttpStatus.UNAUTHORIZED));
              ex.accessDeniedHandler(
                  (request, response, accessDeniedException) -> {
                    response.setStatus(org.springframework.http.HttpStatus.FORBIDDEN.value());
                  });
            })
        .addFilterBefore(accessTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
