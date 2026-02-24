package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.AuthRefreshRequest;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.service.AuthRefreshService;
import org.smalltech.hashtaglocal_backend.service.GoogleAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Google OAuth APIs")
public class AuthController {

	private final AuthRefreshService authRefreshService;
	private final GoogleAuthService googleAuthService;

	public AuthController(AuthRefreshService authRefreshService, GoogleAuthService googleAuthService) {
		this.authRefreshService = authRefreshService;
		this.googleAuthService = googleAuthService;
	}

	@GetMapping("/google/callback")
	@Operation(summary = "Google OAuth callback")
	public ResponseEntity<APIResponse> googleCallback(@RequestParam("code") String code,
			@RequestParam(value = "code_verifier", required = false) String codeVerifier) {

		System.out.println("➡️ /auth/google/callback hit");
		System.out.println("Auth Code: " + code);

		return ResponseEntity.ok(googleAuthService.handleAuthorizationCode(code, codeVerifier));
	}

	@GetMapping("/google/token")
	@Operation(summary = "Authenticate using Google access token")
	public ResponseEntity<APIResponse> authenticateWithAccessToken(@RequestParam("access_token") String accessToken) {

		System.out.println("➡️ /auth/google/token hit");
		System.out.println("Google Access Token: " + accessToken);

		return ResponseEntity.ok(googleAuthService.handleAccessToken(accessToken));
	}

	@PostMapping("/refresh")
	@Operation(summary = "Refresh access and refresh tokens")
	@ApiResponse(responseCode = "200", description = "Tokens refreshed successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NewAPIResponse.class)))
	public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(
			@Valid @RequestBody AuthRefreshRequest request) {

		System.out.println("/auth/refresh hit");

		var tokenData = authRefreshService.refreshTokens(request.getRefreshToken());

		return ResponseEntity.ok(NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
	}
}
