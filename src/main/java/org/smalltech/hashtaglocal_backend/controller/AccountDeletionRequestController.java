package org.smalltech.hashtaglocal_backend.controller;

import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.response.AccountDeletionRequestResponseData;
import org.smalltech.hashtaglocal_backend.service.AccountDeletionRequestService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created to expose an in-app account deletion endpoint
 */
@RestController
@RequestMapping("/account")
public class AccountDeletionRequestController {

  private final AccountDeletionRequestService accountDeletionRequestService;

  public AccountDeletionRequestController(
      AccountDeletionRequestService accountDeletionRequestService) {
    this.accountDeletionRequestService = accountDeletionRequestService;
  }

  /**
   * Lets an authenticated user initiate App Store-compliant account deletion from inside the app.
   */
  @PostMapping("/delete-request")
  public ResponseEntity<NewAPIResponse<AccountDeletionRequestResponseData>> requestDeletion(
      @AuthenticationPrincipal Long userId) {
    AccountDeletionRequestResponseData responseData =
        accountDeletionRequestService.requestDeletion(userId);

    return ResponseEntity.ok(
        NewAPIResponse.<AccountDeletionRequestResponseData>builder().data(responseData).build());
  }
}
