package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.CreateQuizRequest;
import org.smalltech.hashtaglocal_backend.model.request.UpdateBulletinSummaryRequest;
import org.smalltech.hashtaglocal_backend.model.request.UpdateQuizRequest;
import org.smalltech.hashtaglocal_backend.model.response.AdminBulletinData;
import org.smalltech.hashtaglocal_backend.model.response.AdminLocalityOptionData;
import org.smalltech.hashtaglocal_backend.model.response.AdminQuizData;
import org.smalltech.hashtaglocal_backend.model.response.AiPromptData;
import org.smalltech.hashtaglocal_backend.model.response.WeekCoverageData;
import org.smalltech.hashtaglocal_backend.service.BulletinGenerationService;
import org.smalltech.hashtaglocal_backend.service.GroqClient;
import org.smalltech.hashtaglocal_backend.service.QuizAdminService;
import org.smalltech.hashtaglocal_backend.service.QuizGenerationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops-portal management of the city bulletin: manual quiz CRUD (one quiz per locality per date),
 * editing AI summaries/explanations, and a manual trigger for the daily weather run. All routes are
 * under {@code /admin} and require the ADMIN role.
 */
@RestController
@RequestMapping("/admin")
@Tag(
    name = "Admin â€” City Bulletin",
    description = "Quiz CRUD, bulletin summary edits, and the manual weather-generation trigger.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BulletinAdminController {

  private final QuizAdminService quizAdminService;
  private final BulletinGenerationService bulletinGenerationService;
  private final QuizGenerationService quizGenerationService;

  // ---------------------------------------------------------------- quizzes

  @GetMapping("/quiz")
  @Operation(summary = "List quizzes", description = "Optionally filtered by locality and/or date.")
  public ResponseEntity<NewAPIResponse<List<AdminQuizData>>> listQuizzes(
      @RequestParam(name = "locality_id", required = false) Long localityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(
        NewAPIResponse.<List<AdminQuizData>>builder()
            .data(quizAdminService.listQuizzes(localityId, date))
            .build());
  }

  @PostMapping("/quiz")
  @Operation(
      summary = "Create a quiz",
      description =
          "Creates the quiz for a locality + date and links it on the bulletin row (created as a"
              + " shell if the weather job hasn't run yet). Groq generates the explanation unless"
              + " one is provided.")
  public ResponseEntity<NewAPIResponse<AdminQuizData>> createQuiz(
      @Valid @RequestBody CreateQuizRequest request) {
    return ResponseEntity.ok(
        NewAPIResponse.<AdminQuizData>builder().data(quizAdminService.createQuiz(request)).build());
  }

  @PutMapping("/quiz/{quizId}")
  @Operation(summary = "Edit a quiz", description = "Updates the provided fields.")
  public ResponseEntity<NewAPIResponse<AdminQuizData>> updateQuiz(
      @PathVariable Long quizId, @Valid @RequestBody UpdateQuizRequest request) {
    return ResponseEntity.ok(
        NewAPIResponse.<AdminQuizData>builder()
            .data(quizAdminService.updateQuiz(quizId, request))
            .build());
  }

  @DeleteMapping("/quiz/{quizId}")
  @Operation(
      summary = "Delete a quiz",
      description = "Unlinks it from its bulletin and deletes it. Rejected once attempts exist.")
  public ResponseEntity<Void> deleteQuiz(@PathVariable Long quizId) {
    quizAdminService.deleteQuiz(quizId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/quiz/coverage")
  @Operation(
      summary = "Quiz coverage grid for a date range",
      description =
          "Returns a locality × date matrix showing which slots have a quiz. Used by the ops portal to surface missing quizzes.")
  public ResponseEntity<NewAPIResponse<WeekCoverageData>> quizCoverage(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return ResponseEntity.ok(
        NewAPIResponse.<WeekCoverageData>builder()
            .data(quizAdminService.getWeekCoverage(from, to))
            .build());
  }

  @GetMapping("/quiz/localities")
  @Operation(
      summary = "List saved-user localities",
      description = "The localities the daily job covers â€” the dropdown options for quiz entry.")
  public ResponseEntity<NewAPIResponse<List<AdminLocalityOptionData>>> listLocalities() {
    return ResponseEntity.ok(
        NewAPIResponse.<List<AdminLocalityOptionData>>builder()
            .data(quizAdminService.listUserLocalities())
            .build());
  }

  // -------------------------------------------------------------- bulletins

  @GetMapping("/bulletin")
  @Operation(
      summary = "List bulletins",
      description = "Optionally filtered by locality and/or date.")
  public ResponseEntity<NewAPIResponse<List<AdminBulletinData>>> listBulletins(
      @RequestParam(name = "locality_id", required = false) Long localityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return ResponseEntity.ok(
        NewAPIResponse.<List<AdminBulletinData>>builder()
            .data(quizAdminService.listBulletins(localityId, date))
            .build());
  }

  @PutMapping("/bulletin/{bulletinId}/summary")
  @Operation(
      summary = "Edit a bulletin summary",
      description = "Rewrites the AI weather summary; the linked feed post text is synced too.")
  public ResponseEntity<NewAPIResponse<AdminBulletinData>> updateSummary(
      @PathVariable Long bulletinId, @Valid @RequestBody UpdateBulletinSummaryRequest request) {
    return ResponseEntity.ok(
        NewAPIResponse.<AdminBulletinData>builder()
            .data(quizAdminService.updateSummary(bulletinId, request.getSummary()))
            .build());
  }

  @PostMapping("/bulletin/generate")
  @Operation(
      summary = "Run the bulletin weather generation now",
      description =
          "Manually triggers the same run the 8 AM cron performs (idempotent per locality+day).")
  public ResponseEntity<NewAPIResponse<BulletinGenerationService.GenerationResult>> generate() {
    return ResponseEntity.ok(
        NewAPIResponse.<BulletinGenerationService.GenerationResult>builder()
            .data(bulletinGenerationService.generateForAllUserLocalities())
            .build());
  }

  @PostMapping("/quiz/generate")
  @Operation(
      summary = "Manually trigger quiz generation for the current week",
      description =
          "Generates Groq quizzes for all saved-user localities from today through this Sunday"
              + " (inclusive). Idempotent — skips any locality+date that already has a quiz, so"
              + " re-running only retries failed ones. The Sunday 7 AM cron uses a separate path"
              + " that generates the full next Mon–Sun week.")
  public ResponseEntity<NewAPIResponse<QuizGenerationService.GenerationResult>> generateQuizzes() {
    return ResponseEntity.ok(
        NewAPIResponse.<QuizGenerationService.GenerationResult>builder()
            .data(quizGenerationService.generateForCurrentWeek())
            .build());
  }

  @GetMapping("/ai-prompts")
  @Operation(
      summary = "Read-only view of the Groq prompt templates",
      description =
          "Returns the prompt templates currently used to generate weather summaries and"
              + " quiz explanations. Edit GroqClient.java to change them.")
  public ResponseEntity<NewAPIResponse<List<AiPromptData>>> aiPrompts() {
    List<AiPromptData> prompts =
        List.of(
            AiPromptData.builder()
                .key("WEATHER_SUMMARY")
                .description("Weather Summary — the one-line advice shown in the daily bulletin")
                .template(GroqClient.WEATHER_SUMMARY_TEMPLATE)
                .variables("localityName, weatherData")
                .build(),
            AiPromptData.builder()
                .key("QUIZ_GENERATION")
                .description(
                    "Quiz Generation — used by the weekly cron (and manual trigger) to generate"
                        + " quiz question + options + answer + explanation for each locality")
                .template(GroqClient.QUIZ_GENERATION_TEMPLATE)
                .variables("localityName")
                .build());
    return ResponseEntity.ok(NewAPIResponse.<List<AiPromptData>>builder().data(prompts).build());
  }
}
