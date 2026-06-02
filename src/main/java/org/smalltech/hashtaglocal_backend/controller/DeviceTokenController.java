package org.smalltech.hashtaglocal_backend.controller;

import org.smalltech.hashtaglocal_backend.dto.RegisterDeviceTokenRequest;
import org.smalltech.hashtaglocal_backend.dto.RemoveDeviceTokenRequest;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.service.DeviceTokenService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/device-token")
public class DeviceTokenController {

  private final DeviceTokenService deviceTokenService;

  public DeviceTokenController(DeviceTokenService deviceTokenService) {
    this.deviceTokenService = deviceTokenService;
  }

  @PostMapping
  public ResponseEntity<NewAPIResponse<RegisterDeviceTokenRequest>> register(
      @AuthenticationPrincipal Long userId, @RequestBody RegisterDeviceTokenRequest request) {
    deviceTokenService.register(userId, request.getToken(), request.getPlatform());
    return ResponseEntity.ok(NewAPIResponse.<RegisterDeviceTokenRequest>builder().data(request).build());
  }

  @DeleteMapping
  public ResponseEntity<NewAPIResponse<RemoveDeviceTokenRequest>> remove(
      @AuthenticationPrincipal Long userId, @RequestBody RemoveDeviceTokenRequest request) {
    deviceTokenService.remove(userId, request.getPlatform());
    return ResponseEntity.ok(NewAPIResponse.<RemoveDeviceTokenRequest>builder().data(request).build());
  }
}
