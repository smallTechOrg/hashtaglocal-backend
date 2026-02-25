package org.smalltech.hashtaglocal_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  /**
   * Use the Spring-managed ObjectMapper (with SNAKE_CASE naming strategy) for Swagger schema
   * generation, so that property names in the API docs match the actual JSON output.
   */
  @Bean
  public ModelResolver modelResolver(ObjectMapper objectMapper) {
    return new ModelResolver(objectMapper);
  }

  @Bean
  public OpenAPI customOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Hashtag Local Backend API")
                .version("0.0.1-SNAPSHOT")
                .description("API documentation for Hashtag Local Backend")
                .contact(new Contact().name("Hashtag Local").email("contact@smalltech.in"))
                .license(
                    new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"));
  }
}
