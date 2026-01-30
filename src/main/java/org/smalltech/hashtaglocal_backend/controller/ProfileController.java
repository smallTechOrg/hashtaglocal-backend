package org.smalltech.hashtaglocal_backend.controller;

import java.util.Optional;

import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.service.GetProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/account/profile")
public class ProfileController {

	private final GetProfileService profileService;

	public ProfileController(GetProfileService profileService) {
		this.profileService = profileService;
	}


	/**
	 * Get authenticated user's own profile
	 *
	 * @param authorization The Authorization header with Bearer token
	 * @return User's own profile information
	 */
	@GetMapping()
	public ResponseEntity<APIResponse> getMyProfile(
			@RequestHeader(value = "Authorization", required = false) String authorization) {

		String accessToken = extractBearerToken(authorization);

		if (accessToken == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(APIResponse.builder().data(null).build());
		}

		Optional<UserProfileModel> profile = profileService.getMyProfile(accessToken);

		if (profile.isEmpty()) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(APIResponse.builder().data(null).build());
		}

		ResponseData responseData = ResponseData.builder().user(profile.get()).build();

		APIResponse response = APIResponse.builder().data(responseData).build();

		return ResponseEntity.ok(response);
	}

	/**
	 * Extract Bearer token from Authorization header
	 *
	 * @param authHeader The Authorization header value
	 * @return The token string or null if not present
	 */
	private String extractBearerToken(String authHeader) {
		if (authHeader != null && authHeader.startsWith("Bearer ")) {
			return authHeader.substring("Bearer ".length());
		}
		return null;
	}
}
