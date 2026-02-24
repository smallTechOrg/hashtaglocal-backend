package org.smalltech.hashtaglocal_backend.service.location;

import java.util.Map;

public class LocationNameExtractor {

	private LocationNameExtractor() {
	}

	public static String extract(Map<String, Object> metaData) {
		if (metaData == null)
			return null;

		Object formatted = metaData.get("formatted_address");
		if (!(formatted instanceof String formattedAddress) || formattedAddress.isEmpty()) {
			return null;
		}

		String region = metaData.get("region") instanceof String s ? s : null;
		String postalCode = metaData.get("postal_code") instanceof String s ? s : null;

		String[] parts = formattedAddress.split(",\\s*");
		StringBuilder result = new StringBuilder();

		for (String part : parts) {
			if (region != null && part.contains(region))
				continue;
			if (postalCode != null && part.contains(postalCode))
				continue;

			if (result.length() > 0)
				result.append(" - ");
			result.append(part.trim());
		}

		return result.length() > 0 ? result.toString() : null;
	}
}
