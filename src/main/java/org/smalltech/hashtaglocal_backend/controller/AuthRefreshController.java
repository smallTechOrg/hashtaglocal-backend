package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.request.AuthRefreshRequest;
import org.smalltech.hashtaglocal_backend.service.AuthRefreshService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/refresh")
@Tag(name = "Authentication", description = "Auth refresh APIs")
public class AuthRefreshController {

	private final AuthRefreshService authRefreshService;

	public AuthRefreshController(AuthRefreshService authRefreshService) {
		this.authRefreshService = authRefreshService;
	}

	@PostMapping
	@Operation(summary = "Refresh access and refresh tokens")
	@ApiResponse(responseCode = "200", description = "Tokens refreshed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public ResponseEntity<APIResponse> refresh(@Valid @RequestBody AuthRefreshRequest request) {

		System.out.println("/auth/refresh hit");

		return ResponseEntity.ok(authRefreshService.refreshTokens(request.getRefreshToken()));
	}
}
