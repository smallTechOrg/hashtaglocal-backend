package org.smalltech.hashtaglocal_backend.service;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.UserProfileModel;
import org.smalltech.hashtaglocal_backend.repository.UserAuthSessionRepository;
import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class GetProfileService {

	private final UserAuthSessionRepository userAuthSessionRepository;

	public GetProfileService(UserRepository userRepository, UserAuthSessionRepository userAuthSessionRepository) {
		this.userAuthSessionRepository = userAuthSessionRepository;
	}

	/**
	 * Get user profile for the authenticated user (me), based on the access token.
	 *
	 * @param accessToken
	 *            The bearer token from the Authorization header
	 * @return Optional containing the user profile model if found and token is
	 *         valid
	 */
	public Optional<UserProfileModel> getMyProfile(String accessToken) {
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
		return Optional.of(mapToProfileModel(session.getUser()));
	}

	/**
	 * Map UserEntity to UserProfileModel
	 */
	private UserProfileModel mapToProfileModel(UserEntity user) {
		return UserProfileModel.builder().username(user.getUsername()).picture(user.getProfilePicture())
				.hashtag("world").build();
	}
}
