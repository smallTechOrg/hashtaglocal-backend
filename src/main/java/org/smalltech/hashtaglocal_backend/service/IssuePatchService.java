package org.smalltech.hashtaglocal_backend.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.entity.IssueEntity;
import org.smalltech.hashtaglocal_backend.model.request.IssuePatchRequest;
import org.smalltech.hashtaglocal_backend.repository.IssueRepository;
import org.smalltech.hashtaglocal_backend.util.EnumParsers;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class IssuePatchService {

	private final IssueRepository issueRepository;
	private final GoogleMapsGeocodingService googleMapsGeocodingService;

	public IssueEntity patchIssue(Long issueId, IssuePatchRequest request) {
		if (request == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
		}

		IssueEntity issueEntity = issueRepository.findById(issueId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Issue not found"));

		boolean updated = false;

		if (request.getStatus() != null) {
			issueEntity.setStatus(EnumParsers.parseStatus(request.getStatus()));
			updated = true;
		}

		if (request.getType() != null) {
			issueEntity.setType(EnumParsers.parseType(request.getType()));
			updated = true;
		}

		if (request.getDescription() != null) {
			issueEntity.setDescription(request.getDescription());
			updated = true;
		}

		if (request.getLat() != null && request.getLng() != null) {
			updateLocationCoordinates(issueEntity, request.getLat(), request.getLng());
			updated = true;
		}

		if (updated) {
			issueEntity.setUpdatedAt(LocalDateTime.now());
			issueRepository.save(issueEntity);
		}

		return issueEntity;
	}

	private void updateLocationCoordinates(IssueEntity issueEntity, Double lat, Double lng) {
		var locEntity = issueEntity.getLocation();
		if (locEntity == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Location data not found for issue");
		}

		// Update point
		locEntity.setPoint(org.smalltech.hashtaglocal_backend.util.LocationUtil.createPoint(lat, lng));

		// Fetch latest location metadata
		var metadata = googleMapsGeocodingService.reverseGeocode(lat, lng);
		if (metadata != null) {
			var metadataMap = googleMapsGeocodingService.metadataToMap(metadata);
			locEntity.setMetaData(metadataMap);

			if (metadata.getName() != null && !metadata.getName().isEmpty()) {
				locEntity.setName(metadata.getName());
			} else if (metadata.getCity() != null) {
				locEntity.setName(metadata.getCity());
			}
		}
	}
}
