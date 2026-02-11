package org.smalltech.hashtaglocal_backend.service;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class GetProfileService {

	private final UserAuthSessionRepository userAuthSessionRepository;
	private final LocalityRepository localityRepository;
	private static final String DEFAULT_HASHTAG = "#local";

	public GetProfileService(UserRepository userRepository, UserAuthSessionRepository userAuthSessionRepository,
			LocalityRepository localityRepository) {
		this.userAuthSessionRepository = userAuthSessionRepository;
		this.localityRepository = localityRepository;
	}

	/**
	 * Get user profile for the authenticated user (me), based on the access token.
	 *
	 * @param accessToken
	 *            The bearer token from the Authorization header
	 * @param latitude
	 *            Optional latitude coordinate
	 * @param longitude
	 *            Optional longitude coordinate
	 * @return Optional containing the user profile model if found and token is
	 *         valid
	 */
	public Optional<UserProfileModel> getMyProfile(String accessToken, Double latitude, Double longitude) {
		// Find the auth session by access token
		Optional<UserAuthSessionEntity> authSession = userAuthSessionRepository.findByAccessToken(accessToken);

		if (authSession.isEmpty()) {
			return Optional.empty();
		}

		UserAuthSessionEntity session = authSession.get();

		// Verify session is still active
		if (!session.getIsActive()) {
			return Optional.empty();
		}

		// Check if token has expired
		if (session.getAccessTokenExpiryTs() != null
				&& session.getAccessTokenExpiryTs() < System.currentTimeMillis() / 1000) {
			return Optional.empty();
		}

		// Get the user associated with this session
		return Optional.of(mapToProfileModel(session.getUser(), latitude, longitude));
	}

	/**
	 * Map UserEntity to UserProfileModel with optional location-based hashtag
	 * resolution
	 */
	private UserProfileModel mapToProfileModel(UserEntity user, Double latitude, Double longitude) {
		String hashtag = DEFAULT_HASHTAG;

		// If lat/lng provided, try to resolve locality
		if (latitude != null && longitude != null) {
			var locality = localityRepository.findContainingLocality(latitude, longitude)
					.or(() -> localityRepository.findNearestLocality(latitude, longitude));

			if (locality.isPresent() && locality.get().getHashtag() != null) {
				hashtag = locality.get().getHashtag();
			}
		}

		return UserProfileModel.builder().username(user.getUsername()).picture(user.getProfilePicture())
				.hashtag(hashtag).build();
	}
}
