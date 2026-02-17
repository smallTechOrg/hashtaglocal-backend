package org.smalltech.hashtaglocal_backend.mapper;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.entity.MediaEntity;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.smalltech.hashtaglocal_backend.model.APIResponse;
import org.smalltech.hashtaglocal_backend.model.Issue;
import org.smalltech.hashtaglocal_backend.model.IssueActionModel;
import org.smalltech.hashtaglocal_backend.model.Locality;
import org.smalltech.hashtaglocal_backend.model.Location;
import org.smalltech.hashtaglocal_backend.model.Media;
import org.smalltech.hashtaglocal_backend.model.ResponseData;
import org.smalltech.hashtaglocal_backend.model.User;
import org.smalltech.hashtaglocal_backend.model.ViewerContext;
import org.smalltech.hashtaglocal_backend.repository.IssueActionRepository;
import org.smalltech.hashtaglocal_backend.repository.MediaRepository;
import org.smalltech.hashtaglocal_backend.service.GCSService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IssueResponseMapper {

	private final MediaRepository mediaRepository;
	private final IssueActionRepository issueActionRepository;
	private final GCSService gcsService;

	public APIResponse map(IssueEntity entity) {
		// Get user from entity - IssueDataInitializer ensures all issues have user ID 1
		UserEntity userEntity = entity.getUserEntity();
		if (userEntity == null) {
			userEntity = new UserEntity();
			userEntity.setUsername("admin");
			userEntity.setProfilePicture("https://example.com/default-profile.jpg");
		}

		User user = User.builder().username(userEntity.getUsername()).profilePhoto(userEntity.getProfilePicture())
				.build();

		// Map Locality from Location entity with robust null-safety
		org.smalltech.hashtaglocal_backend.entity.Location locEntity = entity.getLocation();
		String hashtag = "world";
		if (locEntity != null && locEntity.getLocality() != null && locEntity.getLocality().getHashtag() != null) {
			hashtag = locEntity.getLocality().getHashtag();
		}
		Locality locality = Locality.builder().hashtags(List.of(hashtag)).build();

		double lat = 0.0;
		double lng = 0.0;
		String name = "Unknown";
		if (locEntity != null) {
			if (locEntity.getPoint() != null) {
				lat = locEntity.getPoint().getY();
				lng = locEntity.getPoint().getX();
			}
			if (locEntity.getName() != null) {
				name = locEntity.getName();
			}
		}

		Location location = Location.builder().lat(lat).lng(lng).locality(locality).address(name).colloquialName(name)
				.build();

		// Fetch media items from database
		List<MediaEntity> mediaEntities = mediaRepository.findByIssue(entity);

		List<Media> mediaList = mediaEntities.stream().map(mediaEntity -> {
			String username = "admin";
			double mediaLocLat = 0.0;
			double mediaLocLng = 0.0;
			String mediaLocName = "Unknown";

			if (mediaEntity.getUser() != null && mediaEntity.getUser().getUsername() != null) {
				username = mediaEntity.getUser().getUsername();
			}

			if (mediaEntity.getLocation() != null) {
				if (mediaEntity.getLocation().getPoint() != null) {
					mediaLocLat = mediaEntity.getLocation().getPoint().getY();
					mediaLocLng = mediaEntity.getLocation().getPoint().getX();
				}
				if (mediaEntity.getLocation().getName() != null) {
					mediaLocName = mediaEntity.getLocation().getName();
				}
			}

			Location mediaLocation = Location.builder().lat(mediaLocLat).lng(mediaLocLng).locality(locality)
					.address(mediaLocName).colloquialName(mediaLocName).build();

			return Media.builder().location(mediaLocation).type(mediaEntity.getType().name().toLowerCase())
					.url(gcsService.generateSignedUrl(mediaEntity.getUrl())).description(mediaEntity.getDescription())
					.username(username).createdAt(mediaEntity.getCreatedAt()).build();
		}).toList();

		int verifyCount = issueActionRepository.countDistinctUserByIssueAndAction(entity, IssueActionModel.VERIFY);

		ViewerContext viewerContext = ViewerContext.builder().upvote(false).build();

		Issue issue = Issue.builder().id(entity.getId()).user(user).location(location)
				.type(entity.getType().name().toLowerCase()).description(entity.getDescription())
				.createdAt(entity.getCreatedAt()).mediaUrls(mediaList).voteCount(0).verifyCount(verifyCount)
				.status(entity.getStatus().name()).rank(1).viewerContext(viewerContext).build();

		ResponseData data = ResponseData.builder().issue(issue).build();

		return APIResponse.builder().data(data).build();
	}
}
