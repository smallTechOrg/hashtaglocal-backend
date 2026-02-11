package org.smalltech.hashtaglocal_backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing structured location metadata with complete Google Maps data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationMetadataDTO {
	private String city;
	private String name;
	private String region;
	private String street;
	private String country;
	private String district;
	private String timezone;

	@JsonProperty("sub_region")
	private String subRegion;

	@JsonProperty("postal_code")
	private String postalCode;

	@JsonProperty("street_number")
	private String streetNumber;

	@JsonProperty("iso_country_code")
	private String isoCountryCode;

	@JsonProperty("formatted_address")
	private String formattedAddress;

	// Additional fields for extra details
	private String neighborhood;

	@JsonProperty("point_of_interest")
	private String pointOfInterest;

	@JsonProperty("premise_name")
	private String premiseName;

	@JsonProperty("establishment_type")
	private String establishmentType;

	// Store complete Google Maps response data for reference
	@JsonProperty("google_maps_data")
	private Map<String, Object> googleMapsData;

	// Store raw address components for reference
	@JsonProperty("address_components")
	private Map<String, String> addressComponents;
}
