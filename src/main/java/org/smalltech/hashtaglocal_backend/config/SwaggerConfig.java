package org.smalltech.hashtaglocal_backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
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
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
        .tags(List.of(new Tag().name("Actuator").description("Spring Boot Actuator endpoints")));
  }

  @Bean
  public GroupedOpenApi actuatorApi() {
    return GroupedOpenApi.builder()
        .group("actuator")
        .pathsToMatch("/actuator/**")
        .addOperationCustomizer(actuatorOperationCustomizer())
        .build();
  }

  @Bean
  public GroupedOpenApi publicApi() {
    return GroupedOpenApi.builder()
        .group("public-api")
        .pathsToExclude("/actuator/**")
        .addOperationCustomizer(publicApiOperationCustomizer())
        .build();
  }

  @Bean
  public OperationCustomizer actuatorOperationCustomizer() {
    return (operation, handlerMethod) -> {
      if (operation.getOperationId() != null && operation.getOperationId().contains("health")) {
        enhanceHealthEndpointExamples(operation);
      }
      return operation;
    };
  }

  @Bean
  public OperationCustomizer publicApiOperationCustomizer() {
    return (operation, handlerMethod) -> {
      // Enhance examples for all public API endpoints
      enhanceResponseExamples(operation);
      return operation;
    };
  }

  private void enhanceHealthEndpointExamples(io.swagger.v3.oas.models.Operation operation) {
    ApiResponses responses = operation.getResponses();
    if (responses == null) {
      responses = new ApiResponses();
      operation.setResponses(responses);
    }

    ApiResponse healthResponse = responses.get("200");
    if (healthResponse == null) {
      healthResponse = new ApiResponse();
      responses.addApiResponse("200", healthResponse);
    }

    Content content = healthResponse.getContent();
    if (content == null) {
      content = new Content();
      healthResponse.setContent(content);
    }

    MediaType jsonMediaType = content.get("application/json");
    if (jsonMediaType == null) {
      jsonMediaType = new MediaType();
      content.addMediaType("application/json", jsonMediaType);
    }

    // Add realistic example for health check response
    Example healthExample = new Example();
    healthExample.setValue(
        """
				{
				  "status": "UP",
				  "components": {
				    "db": {
				      "status": "UP",
				      "details": {
				        "database": "PostgreSQL",
				        "validationQuery": "isValid()"
				      }
				    },
				    "ping": {
				      "status": "UP"
				    }
				  }
				}
				""");

    if (jsonMediaType.getExamples() == null) {
      jsonMediaType.setExamples(Map.of("default", healthExample));
    } else {
      jsonMediaType.getExamples().put("default", healthExample);
    }
  }

  private void enhanceResponseExamples(io.swagger.v3.oas.models.Operation operation) {
    // This method can be used to enhance examples for all public API endpoints
    // SpringDoc will automatically generate examples from response schemas,
    // but we can add custom examples here if needed
  }
}
