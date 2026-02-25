package org.smalltech.hashtaglocal_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.model.UserRole;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AccessTokenAuthFilter extends OncePerRequestFilter {

  private final UserAuthSessionRepository userAuthSessionRepository;

  public AccessTokenAuthFilter(UserAuthSessionRepository userAuthSessionRepository) {
    this.userAuthSessionRepository = userAuthSessionRepository;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String authHeader = request.getHeader("Authorization");

    // If no Bearer token is present, continue without setting auth context.
    // Spring Security's authorizeHttpRequests rules will reject unauthenticated
    // access to protected endpoints; public endpoints proceed normally.
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      filterChain.doFilter(request, response);
      return;
    }

    String accessToken = authHeader.substring(7);
    System.out.println("🔐 Authorization header = " + authHeader);
    System.out.println("🔐 Extracted accessToken = " + accessToken);
    UserAuthSessionEntity session =
        userAuthSessionRepository
            .findByAccessToken(accessToken)
            .filter(UserAuthSessionEntity::getIsActive)
            .filter(
                s ->
                    s.getAccessTokenExpiryTs() == null
                        || s.getAccessTokenExpiryTs() > Instant.now().getEpochSecond())
            .orElse(null);

    if (session == null) {
      System.out.println("SESSION NOT FOUND");
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }

    System.out.println("✅ Session found");
    System.out.println("   isActive = " + session.getIsActive());
    System.out.println("   expiry  = " + session.getAccessTokenExpiryTs());
    System.out.println("   now     = " + Instant.now().getEpochSecond());

    // Build role-based authorities so Spring Security can enforce hasRole("ADMIN") etc.
    UserRole role =
        session.getUser().getRole() != null ? session.getUser().getRole() : UserRole.USER;
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(session.getUser().getId(), null, authorities);

    SecurityContextHolder.getContext().setAuthentication(authentication);

    filterChain.doFilter(request, response);
  }
}
