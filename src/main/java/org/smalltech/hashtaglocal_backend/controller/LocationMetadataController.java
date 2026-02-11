package org.smalltech.hashtaglocal_backend.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.job.LocationMetadataUpdateJob;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for managing location metadata update jobs.
 */
@RestController
@RequestMapping("/api/v1/admin/location-metadata")
@RequiredArgsConstructor
@Tag(name = "Location Metadata", description = "Endpoints for updating location metadata using Google Maps API")
public class LocationMetadataController {

	private final LocationMetadataUpdateJob locationMetadataUpdateJob;

	@PostMapping("/update")
	@Operation(summary = "Update location metadata", description = "Triggers a job to update location metadata and names using Google Maps Geocoding API. Use 'partial=true' to update only locations with missing metadata.")
	public ResponseEntity<LocationMetadataUpdateJob.LocationUpdateJobResult> updateLocationMetadata(
			@RequestParam(defaultValue = "false") boolean partial) {

		LocationMetadataUpdateJob.LocationUpdateJobResult result;

		if (partial) {
			result = locationMetadataUpdateJob.runPartialUpdate();
		} else {
			result = locationMetadataUpdateJob.runFullUpdate();
		}

		return ResponseEntity.ok(result);
	}
}
