package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.QuizAttemptRequest;
import org.smalltech.hashtaglocal_backend.model.response.BulletinData;
import org.smalltech.hashtaglocal_backend.model.response.QuizAttemptResultData;
import org.smalltech.hashtaglocal_backend.service.BulletinService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing city bulletin: today's weather + summary + quiz for a locality, and quiz attempt
 * submission. Reads never trigger generation — content appears after the daily 8 AM job has covered
 * the locality.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(
    name = "City Bulletin",
    description = "Daily locality bulletin (weather + AI summary + quiz) and quiz attempts.")
@RequiredArgsConstructor
public class BulletinController {

  private final BulletinService bulletinService;

  @GetMapping("/bulletin")
  @Operation(
      summary = "Get today's bulletin",
      description =
          "By locality_id or hashtag. data is null when the locality has no bulletin yet (it gets"
              + " one from the next 8 AM run). The quiz answer stays hidden until attempted.")
  public ResponseEntity<NewAPIResponse<BulletinData>> getBulletin(
      @RequestParam(name = "locality_id", required = false) Long localityId,
      @RequestParam(required = false) String hashtag,
      @AuthenticationPrincipal Long viewerUserId) {
    return ResponseEntity.ok(
        NewAPIResponse.<BulletinData>builder()
            .data(bulletinService.getTodayBulletin(localityId, hashtag, viewerUserId).orElse(null))
            .build());
  }

  @PostMapping("/quiz/{quizId}/attempt")
  @Operation(
      summary = "Submit a quiz attempt",
      description =
          "One attempt per user per quiz (409 on retry). selected_option_index null = the 15-second"
              + " timer expired; recorded as a missed, incorrect attempt.")
  public ResponseEntity<NewAPIResponse<QuizAttemptResultData>> submitAttempt(
      @PathVariable Long quizId,
      @Valid @RequestBody QuizAttemptRequest request,
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(
        NewAPIResponse.<QuizAttemptResultData>builder()
            .data(bulletinService.submitAttempt(userId, quizId, request.getSelectedOptionIndex()))
            .build());
  }
}
