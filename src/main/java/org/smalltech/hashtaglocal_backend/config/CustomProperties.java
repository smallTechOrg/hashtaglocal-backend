package org.smalltech.hashtaglocal_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app")
@Data
public class CustomProperties {

	private Geo geo = new Geo();
	private Storage storage = new Storage();

	@Data
	public static class Geo {
		private double verifyRadiusMeters;
	}

	@Data
	public static class Storage {
		private String bucketName;

	}
}
