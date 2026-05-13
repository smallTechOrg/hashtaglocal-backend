package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.AppleAuthRequest;
import org.smalltech.hashtaglocal_backend.model.request.AuthRefreshRequest;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.service.AppleAuthService;
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
@Tag(name = "Authentication", description = "OAuth APIs")
public class AuthController {

  private final AuthRefreshService authRefreshService;
  private final GoogleAuthService googleAuthService;
  private final AppleAuthService appleAuthService;

  public AuthController(
      AuthRefreshService authRefreshService,
      GoogleAuthService googleAuthService,
      AppleAuthService appleAuthService) {
    this.authRefreshService = authRefreshService;
    this.googleAuthService = googleAuthService;
    this.appleAuthService = appleAuthService;
  }

  @GetMapping("/google/callback")
  @Operation(summary = "Google OAuth callback")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> googleCallback(
      @RequestParam("code") String code,
      @RequestParam(value = "code_verifier", required = false) String codeVerifier,
      @RequestParam(value = "redirect_uri", required = false) String redirectUri) {

    System.out.println("➡️ /auth/google/callback hit");
    System.out.println("Auth Code: " + code);

    var tokenData = googleAuthService.handleAuthorizationCode(code, codeVerifier, redirectUri);

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @GetMapping("/google/token")
  @Operation(summary = "Authenticate using Google access token")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> authenticateWithAccessToken(
      @RequestParam("access_token") String accessToken) {

    System.out.println("➡️ /auth/google/token hit");
    System.out.println("Google Access Token: " + accessToken);

    var tokenData = googleAuthService.handleAccessToken(accessToken);

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @PostMapping("/apple")
  @Operation(summary = "Authenticate using Apple identity token")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> authenticateWithApple(
      @Valid @RequestBody AppleAuthRequest request) {

    System.out.println("➡️ /auth/apple hit");

    var tokenData =
        appleAuthService.handleIdentityToken(request.getIdentityToken(), request.getFullName());

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access and refresh tokens")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(
      @Valid @RequestBody AuthRefreshRequest request) {

    System.out.println("/auth/refresh hit");

    AuthTokenResponseData tokenData = authRefreshService.refreshTokens(request.getRefreshToken());

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }
}
