package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintRequestDTO;
import org.smalltech.hashtaglocal_backend.dto.ReportComplaintResponseDTO;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.service.PortalComplaintReportingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Complaint", description = "Government portal complaint reporting APIs")
@RequiredArgsConstructor
public class ComplaintController {

  private final PortalComplaintReportingService portalComplaintReportingService;

  @PostMapping("/portal/{type}")
  @SecurityRequirement(name = "bearerAuth")
  @Operation(
      summary = "Portal complaint action",
      description =
          "Accepts complaint details, validates request payload, forwards it to the external government portal scrape API, updates the requested issue row, persists portal tracking data locally, and returns tracking_id on success.")
  public ResponseEntity<NewAPIResponse<ReportComplaintResponseDTO>> reportComplaint(
      @PathVariable String type,
      @RequestParam("issue_id") Long issueId,
      @AuthenticationPrincipal Long adminUserId,
      @Valid @RequestBody ReportComplaintRequestDTO request) {
    ReportComplaintResponseDTO response =
        portalComplaintReportingService.reportComplaint(type, issueId, adminUserId, request);

    return ResponseEntity.ok(
        NewAPIResponse.<ReportComplaintResponseDTO>builder().data(response).build());
  }
}
