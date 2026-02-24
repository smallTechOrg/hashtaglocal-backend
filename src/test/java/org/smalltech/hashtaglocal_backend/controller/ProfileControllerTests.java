package org.smalltech.hashtaglocal_backend.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.smalltech.hashtaglocal_backend.model.NewAPIResponse;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.model.response.UserProfileResponseData;
import org.smalltech.hashtaglocal_backend.service.GetProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for ProfileController.
 */
@DisplayName("ProfileController Tests")
class ProfileControllerTests {

	@Mock
	private GetProfileService profileService;

	@InjectMocks
	private ProfileController profileController;

	@BeforeEach
	void setup() {
		MockitoAnnotations.openMocks(this);
	}

	@Nested
	@DisplayName("Get My Profile")
	class GetMyProfile {

		@Test
		@DisplayName("Should return user profile with default hashtag when no lat/lng provided")
		void testGetMyProfile_NoCoordinates_Success() {
			// Arrange
			String token = "valid-token";
			UserProfileModel userProfile = UserProfileModel.builder().username("testuser")
					.picture("http://example.com/pic.jpg").hashtag("#local").build();

			when(profileService.getMyProfile(token, null, null)).thenReturn(Optional.of(userProfile));

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, null, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
			assertNotNull(response.getBody().getData());
			assertEquals("testuser", response.getBody().getData().getUser().getUsername());
			assertEquals("#local", response.getBody().getData().getUser().getHashtag());

			verify(profileService, times(1)).getMyProfile(token, null, null);
		}

		@Test
		@DisplayName("Should return user profile with resolved hashtag when lat/lng provided")
		void testGetMyProfile_WithCoordinates_Success() {
			// Arrange
			String token = "valid-token";
			Double lat = 12.9716;
			Double lng = 77.5946;
			UserProfileModel userProfile = UserProfileModel.builder().username("testuser")
					.picture("http://example.com/pic.jpg").hashtag("#bangalore").build();

			when(profileService.getMyProfile(token, lat, lng)).thenReturn(Optional.of(userProfile));

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, lat, lng);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.OK, response.getStatusCode());
			assertNotNull(response.getBody());
			assertNotNull(response.getBody().getData());
			assertEquals("testuser", response.getBody().getData().getUser().getUsername());
			assertEquals("#bangalore", response.getBody().getData().getUser().getHashtag());

			verify(profileService, times(1)).getMyProfile(token, lat, lng);
		}

		@Test
		@DisplayName("Should return 401 when bearer token is invalid")
		void testGetMyProfile_InvalidBearerToken() {
			// Arrange
			String invalidHeader = "InvalidHeader";

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile(invalidHeader, null, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
			verify(profileService, never()).getMyProfile(anyString(), anyDouble(), anyDouble());
		}

		@Test
		@DisplayName("Should return 401 when auth header is null")
		void testGetMyProfile_NullAuthHeader() {
			// Arrange
			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController.getMyProfile(null,
					null, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
			verify(profileService, never()).getMyProfile(anyString(), anyDouble(), anyDouble());
		}

		@Test
		@DisplayName("Should return 401 when token is not found in session")
		void testGetMyProfile_TokenNotFound() {
			// Arrange
			String token = "invalid-token";
			when(profileService.getMyProfile(token, null, null)).thenReturn(Optional.empty());

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, null, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
			verify(profileService, times(1)).getMyProfile(token, null, null);
		}

		@Test
		@DisplayName("Should extract bearer token correctly")
		void testGetMyProfile_BearerTokenExtraction() {
			// Arrange
			String token = "test-token-12345";
			UserProfileModel userProfile = UserProfileModel.builder().username("testuser")
					.picture("http://example.com/pic.jpg").hashtag("#local").build();

			when(profileService.getMyProfile(token, null, null)).thenReturn(Optional.of(userProfile));

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, null, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(profileService, times(1)).getMyProfile(token, null, null);
		}

		@Test
		@DisplayName("Should handle only latitude provided")
		void testGetMyProfile_OnlyLatitude() {
			// Arrange
			String token = "valid-token";
			Double lat = 12.9716;
			UserProfileModel userProfile = UserProfileModel.builder().username("testuser")
					.picture("http://example.com/pic.jpg").hashtag("#local").build();

			when(profileService.getMyProfile(token, lat, null)).thenReturn(Optional.of(userProfile));

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, lat, null);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(profileService, times(1)).getMyProfile(token, lat, null);
		}

		@Test
		@DisplayName("Should handle only longitude provided")
		void testGetMyProfile_OnlyLongitude() {
			// Arrange
			String token = "valid-token";
			Double lng = 77.5946;
			UserProfileModel userProfile = UserProfileModel.builder().username("testuser")
					.picture("http://example.com/pic.jpg").hashtag("#local").build();

			when(profileService.getMyProfile(token, null, lng)).thenReturn(Optional.of(userProfile));

			// Act
			ResponseEntity<NewAPIResponse<UserProfileResponseData>> response = profileController
					.getMyProfile("Bearer " + token, null, lng);

			// Assert
			assertNotNull(response);
			assertEquals(HttpStatus.OK, response.getStatusCode());
			verify(profileService, times(1)).getMyProfile(token, null, lng);
		}
	}
}
