package org.smalltech.hashtaglocal_backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LocationServiceTest {

	private final LocationService locationService = new LocationService(null, null);
	private static final ObjectMapper objectMapper = new ObjectMapper();

	static Stream<Map<String, Object>> metaDataProvider() throws Exception {
		InputStream inputStream = LocationServiceTest.class.getClassLoader().getResourceAsStream("meta-data.json");
		List<Map<String, Object>> entries = objectMapper.readValue(inputStream, new TypeReference<>() {
		});
		return entries.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("metaDataProvider")
	void getNameFromMetaData(Map<String, Object> metaData) {
		String expected = (String) metaData.get("expected");
		String description = (String) metaData.get("description");

		String result = locationService.getNameFromMetaData(metaData);

		assertEquals(expected, result, description);
	}
}
