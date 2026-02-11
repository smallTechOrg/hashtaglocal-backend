package org.smalltech.hashtaglocal_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.geo")
@Getter
@Setter
public class CustomProperties {

	private double verifyRadiusMeters;
}
