package org.smalltech.hashtaglocal_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration for CORS (Cross-Origin Resource Sharing) to allow requests from
 * frontend applications running on different origins.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/**").allowedOriginPatterns("*")
				.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH").allowedHeaders("*")
				.allowCredentials(true).maxAge(3600); // Cache preflight response for 1 hour
	}
}
