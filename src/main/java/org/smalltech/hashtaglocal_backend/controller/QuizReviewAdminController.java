package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.QuizRejectReason;
import org.smalltech.hashtaglocal_backend.model.request.ApproveQuizzesRequest;
import org.smalltech.hashtaglocal_backend.model.request.RejectQuizRequest;
import org.smalltech.hashtaglocal_backend.model.response.AdminQuizData;
import org.smalltech.hashtaglocal_backend.model.response.QueuedQuizCountData;
import org.smalltech.hashtaglocal_backend.service.QuizReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ops review of AI-generated quizzes. All routes are under {@code /admin/quiz}, ADMIN only. */
@RestController
@RequestMapping("/admin/quiz")
@Tag(
    name = "Admin — Quiz Review",
    description = "Generate AI quizzes, then approve or reject them before the 8 AM job uses them.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class QuizReviewAdminController {

  private final QuizReviewService quizReviewService;

  @PostMapping("/generate")
  @Operation(
      summary = "Generate quizzes with Claude",
      description =
          "Creates one PENDING quiz per saved-user locality, or only for the given hashtag.")
  public ResponseEntity<NewAPIResponse<List<AdminQuizData>>> generate(
      @RequestParam(required = false) String hashtag) {
    return ok(quizReviewService.generate(hashtag));
  }

  @PostMapping("/approve")
  @Operation(
      summary = "Approve pending quizzes",
      description = "Moves the given pending quizzes to QUEUED. Other ids are ignored.")
  public ResponseEntity<NewAPIResponse<List<AdminQuizData>>> approve(
      @Valid @RequestBody ApproveQuizzesRequest request,
      @AuthenticationPrincipal Long adminUserId) {
    return ok(quizReviewService.approve(request.getQuizIds(), adminUserId));
  }

  @PostMapping("/{quizId}/reject")
  @Operation(
      summary = "Reject a pending quiz",
      description =
          "Marks it REJECTED with a reason and an optional note (required when the reason is"
              + " OTHER), then generates a replacement for the same hashtag.")
  public ResponseEntity<NewAPIResponse<AdminQuizData>> reject(
      @PathVariable Long quizId,
      @Valid @RequestBody RejectQuizRequest request,
      @AuthenticationPrincipal Long adminUserId) {
    return ResponseEntity.ok(
        NewAPIResponse.<AdminQuizData>builder()
            .data(
                quizReviewService.reject(
                    quizId, request.getReason(), request.getNote(), adminUserId))
            .build());
  }

  @GetMapping("/pending")
  @Operation(summary = "List quizzes waiting for review")
  public ResponseEntity<NewAPIResponse<List<AdminQuizData>>> pending() {
    return ok(quizReviewService.pending());
  }

  @GetMapping("/queued-counts")
  @Operation(
      summary = "Queued quizzes per hashtag",
      description = "One row per saved-user locality, including those with 0 queued.")
  public ResponseEntity<NewAPIResponse<List<QueuedQuizCountData>>> queuedCounts() {
    return ok(quizReviewService.queuedCounts());
  }

  @GetMapping("/rejected")
  @Operation(
      summary = "List rejected quizzes with their reasons",
      description =
          "Most recently reviewed first. Optionally filtered by hashtag and/or rejection reason.")
  public ResponseEntity<NewAPIResponse<List<AdminQuizData>>> rejected(
      @RequestParam(required = false) String hashtag,
      @RequestParam(required = false) QuizRejectReason reason) {
    return ok(quizReviewService.rejected(hashtag, reason));
  }

  private static <T> ResponseEntity<NewAPIResponse<T>> ok(T data) {
    return ResponseEntity.ok(NewAPIResponse.<T>builder().data(data).build());
  }
}
