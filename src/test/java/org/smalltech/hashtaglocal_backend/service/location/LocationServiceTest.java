package org.smalltech.hashtaglocal_backend.service.location;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class LocationNameExtractorTest {

	private static final ObjectMapper objectMapper = new ObjectMapper();

	static Stream<Map<String, Object>> metaDataProvider() throws Exception {
		InputStream inputStream = LocationNameExtractorTest.class.getClassLoader()
				.getResourceAsStream("meta-data.json");

		List<Map<String, Object>> entries = objectMapper.readValue(inputStream,
				new TypeReference<List<Map<String, Object>>>() {
				});

		return entries.stream();
	}

	@ParameterizedTest(name = "{0}")
	@MethodSource("metaDataProvider")
	void extract_shouldReturnExpectedName(Map<String, Object> metaData) {
		String expected = (String) metaData.get("expected");
		String description = (String) metaData.get("description");

		String result = LocationNameExtractor.extract(metaData);

		assertEquals(expected, result, description);
	}
}
