package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.service.GoogleAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/google")
@Tag(name = "Authentication", description = "Google OAuth APIs")
public class GoogleAuthController {

	private final GoogleAuthService googleAuthService;

	public GoogleAuthController(GoogleAuthService googleAuthService) {
		this.googleAuthService = googleAuthService;
	}

	@GetMapping("/callback")
	@Operation(summary = "Google OAuth callback")
	public ResponseEntity<APIResponse> googleCallback(@RequestParam("code") String code,
			@RequestParam(value = "code_verifier", required = false) String codeVerifier) {

		System.out.println("➡️ /auth/google/callback hit");
		System.out.println("Auth Code: " + code);

		return ResponseEntity.ok(googleAuthService.handleAuthorizationCode(code, codeVerifier));
	}

	@GetMapping("/token")
	@Operation(summary = "Authenticate using Google access token")
	public ResponseEntity<APIResponse> authenticateWithAccessToken(@RequestParam("access_token") String accessToken) {

		System.out.println("➡️ /auth/google/token hit");
		System.out.println("Google Access Token: " + accessToken);

		return ResponseEntity.ok(googleAuthService.handleAccessToken(accessToken));
	}
}
