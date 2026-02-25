package org.smalltech.hashtaglocal_backend.config;

import org.smalltech.hashtaglocal_backend.security.AccessTokenAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

  private final AccessTokenAuthFilter accessTokenAuthFilter;

  public SecurityConfig(AccessTokenAuthFilter accessTokenAuthFilter) {
    this.accessTokenAuthFilter = accessTokenAuthFilter;
  }

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

    http.csrf(csrf -> csrf.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.POST, "/api/v1/issue")
                    .authenticated()
                    .requestMatchers(HttpMethod.GET, "/api/v1/media/upload-url")
                    .authenticated()
                    .requestMatchers(HttpMethod.PUT, "/api/v1/issue/**")
                    .authenticated()
                    .anyRequest()
                    .permitAll())
        .addFilterBefore(accessTokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }
}
