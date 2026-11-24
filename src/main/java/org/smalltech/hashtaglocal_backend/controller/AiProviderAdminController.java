package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.AiProviderConfigRequest;
import org.smalltech.hashtaglocal_backend.model.response.AiConnectionTestData;
import org.smalltech.hashtaglocal_backend.model.response.AiProviderConfigData;
import org.smalltech.hashtaglocal_backend.service.AiProviderConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The AI provider used for quiz generation, editable at runtime. All routes are under {@code
 * /admin/ai-provider}, so the {@code /admin/**} rule in SecurityConfig restricts them to
 * ROLE_ADMIN. No route ever returns the API key or its ciphertext.
 */
@RestController
@RequestMapping("/admin/ai-provider")
@Tag(
    name = "Admin — AI Provider",
    description =
        "Provider, endpoint, model and key used for quiz generation. Takes effect without"
            + " a restart.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AiProviderAdminController {

  private final AiProviderConfigService service;

  @GetMapping
  @Operation(
      summary = "Current AI provider config",
      description =
          "Returns has_key and key_last4, never the key. source is ENV when nothing is"
              + " saved and the .env chain is used.")
  public ResponseEntity<NewAPIResponse<AiProviderConfigData>> get() {
    return ok(service.get());
  }

  @PutMapping
  @Operation(
      summary = "Save the AI provider config",
      description =
          "A blank api_key keeps the saved key (same provider only). A blank endpoint"
              + " uses the provider's default.")
  public ResponseEntity<NewAPIResponse<AiProviderConfigData>> save(
      @Valid @RequestBody AiProviderConfigRequest request,
      @AuthenticationPrincipal Long adminUserId) {
    return ok(service.save(request, adminUserId));
  }

  @DeleteMapping
  @Operation(
      summary = "Reset to .env",
      description = "Deletes the saved config; quiz generation goes back to the .env chain.")
  public ResponseEntity<NewAPIResponse<AiProviderConfigData>> reset(
      @AuthenticationPrincipal Long adminUserId) {
    return ok(service.reset(adminUserId));
  }

  @PostMapping("/test")
  @Operation(
      summary = "Test connection",
      description =
          "One minimal call with the submitted values; blank fields use the saved"
              + " config. Saves nothing.")
  public ResponseEntity<NewAPIResponse<AiConnectionTestData>> test(
      @Valid @RequestBody AiProviderConfigRequest request) {
    return ok(service.test(request));
  }

  private static <T> ResponseEntity<NewAPIResponse<T>> ok(T data) {
    return ResponseEntity.ok(NewAPIResponse.<T>builder().data(data).build());
  }
}
