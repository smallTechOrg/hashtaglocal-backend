
package org.smalltech.hashtaglocal_backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

		String authHeader = request.getHeader("Authorization");

		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			return;
		}

		String accessToken = authHeader.substring(7);
		System.out.println("🔐 Authorization header = " + authHeader);
		System.out.println("🔐 Extracted accessToken = " + accessToken);
		UserAuthSessionEntity session = userAuthSessionRepository.findByAccessToken(accessToken)
				.filter(UserAuthSessionEntity::getIsActive).filter(s -> s.getAccessTokenExpiryTs() == null
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

		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
				session.getUser().getId(), null, Collections.emptyList());

		SecurityContextHolder.getContext().setAuthentication(authentication);

		filterChain.doFilter(request, response);
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// Apply security ONLY to protected APIs
		String uri = request.getRequestURI();
		String method = request.getMethod();
		if ("POST".equals(method) && "/api/v1/issue".equals(uri)) {
			return false; // Apply filter
		}
		if ("GET".equals(method) && "/api/v1/media/upload-url".equals(uri)) {
			return false; // Apply filter
		}
		if ("PUT".equals(method) && uri.startsWith("/api/v1/issue/")) {
			return false; // Apply filter
		}
		return true; // Do not apply filter
	}
}
