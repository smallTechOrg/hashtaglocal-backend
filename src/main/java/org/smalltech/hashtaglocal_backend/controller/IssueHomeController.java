package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.service.IssueHomeService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/issues")
@Tag(name = "Issues Home", description = "issue home API")
@Transactional(readOnly = true)
public class IssueHomeController {

	private final IssueHomeService issueHomeAssembler;

	public IssueHomeController(IssueHomeService issueHomeAssembler) {
		this.issueHomeAssembler = issueHomeAssembler;
	}

	@GetMapping
	@Operation(summary = "Get issue Home", description = "Returns a List of issues with user, location, locality and viewer context. Optionally filter by locality hashtag.")
	@ApiResponse(responseCode = "200", description = "Successful issue response", content = @Content(mediaType = "application/json", schema = @Schema(implementation = APIResponse.class)))
	public APIResponse getIssues(@RequestParam(value = "locality", required = false) String localityHashtag) {
		return issueHomeAssembler.getHome(localityHashtag);
	}

}
