package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.smalltech.hashtaglocal_backend.model.request.AppleAuthRequest;
import org.smalltech.hashtaglocal_backend.model.request.AuthRefreshRequest;
import org.smalltech.hashtaglocal_backend.model.request.OAuthRequest;
import org.smalltech.hashtaglocal_backend.model.response.AuthTokenResponseData;
import org.smalltech.hashtaglocal_backend.resolver.OAuthServiceResolver;
import org.smalltech.hashtaglocal_backend.service.AuthRefreshService;
import org.smalltech.hashtaglocal_backend.service.OAuthService;
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

  private static final String GOOGLE_PROVIDER = "google";
  private static final String APPLE_PROVIDER = "apple";

  private final AuthRefreshService authRefreshService;
  private final OAuthServiceResolver oAuthServiceResolver;

  public AuthController(
      AuthRefreshService authRefreshService, OAuthServiceResolver oAuthServiceResolver) {
    this.authRefreshService = authRefreshService;
    this.oAuthServiceResolver = oAuthServiceResolver;
  }

  @GetMapping("/google/callback")
  @Operation(summary = "Google OAuth callback")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> googleCallback(
      @RequestParam("code") String code,
      @RequestParam(value = "code_verifier", required = false) String codeVerifier,
      @RequestParam(value = "redirect_uri", required = false) String redirectUri,
      @RequestParam(value = "platform", required = false) Platform platform,
      @RequestParam(value = "device_id", required = false) String deviceId) {

    System.out.println("/auth/google/callback hit");
    System.out.println("Auth Code: " + code);

    OAuthRequest request =
        OAuthRequest.builder()
            .code(code)
            .codeVerifier(codeVerifier)
            .redirectUri(redirectUri)
            .platform(platform)
            .deviceId(deviceId)
            .build();

    var tokenData = resolveAuthService(GOOGLE_PROVIDER).authenticate(request);

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @GetMapping("/google/token")
  @Operation(summary = "Authenticate using Google access token")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> authenticateWithAccessToken(
      @RequestParam("access_token") String accessToken,
      @RequestParam(value = "platform", required = false) Platform platform,
      @RequestParam(value = "device_id", required = false) String deviceId) {

    System.out.println("/auth/google/token hit");
    System.out.println("Google Access Token: " + accessToken);

    OAuthRequest request =
        OAuthRequest.builder()
            .accessToken(accessToken)
            .platform(platform)
            .deviceId(deviceId)
            .build();

    var tokenData = resolveAuthService(GOOGLE_PROVIDER).authenticate(request);

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @PostMapping("/apple")
  @Operation(summary = "Authenticate using Apple identity token")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> authenticateWithApple(
      @Valid @RequestBody AppleAuthRequest request) {

    System.out.println("/auth/apple hit");

    OAuthRequest oAuthRequest =
        OAuthRequest.builder()
            .identityToken(request.getIdentityToken())
            .fullName(request.getFullName())
            .notificationToken(request.getNotificationToken())
            .platform(request.getPlatform())
            .deviceId(request.getDeviceId())
            .build();

    var tokenData = resolveAuthService(APPLE_PROVIDER).authenticate(oAuthRequest);

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  @PostMapping("/refresh")
  @Operation(summary = "Refresh access and refresh tokens")
  public ResponseEntity<NewAPIResponse<AuthTokenResponseData>> refresh(
      @Valid @RequestBody AuthRefreshRequest request) {

    System.out.println("/auth/refresh hit");

    AuthTokenResponseData tokenData =
        authRefreshService.refreshTokens(
            request.getRefreshToken(), request.getNotificationToken(), request.getDeviceId());

    return ResponseEntity.ok(
        NewAPIResponse.<AuthTokenResponseData>builder().data(tokenData).build());
  }

  private OAuthService resolveAuthService(String providerType) {
    return oAuthServiceResolver.resolve(providerType);
  }
}
