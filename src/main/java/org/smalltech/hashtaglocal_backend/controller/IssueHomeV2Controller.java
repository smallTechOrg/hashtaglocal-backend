package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.request.LocationRequest;
import org.smalltech.hashtaglocal_backend.model.response.IssueListResponseData;
import org.smalltech.hashtaglocal_backend.service.IssueHomeService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/issues")
@Tag(name = "Issues Home V2", description = "issue home API with geospatial features")
@Transactional(readOnly = true)
public class IssueHomeV2Controller {

  private final IssueHomeService issueHomeAssembler;

  public IssueHomeV2Controller(IssueHomeService issueHomeAssembler) {
    this.issueHomeAssembler = issueHomeAssembler;
  }

  @GetMapping
  @Operation(
      summary = "Get issue Home",
      description =
          "Returns a List of issues with user, location, locality and viewer context. Optionally filter by locality hashtag.")
  public NewAPIResponse<IssueListResponseData> getIssuesNearby(
      @Valid LocationRequest locationRequest) {

    return issueHomeAssembler.getNearby(locationRequest.getLat(), locationRequest.getLng());
  }
}
