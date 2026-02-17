package org.smalltech.hashtaglocal_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

public class CustomProperties {

    @Configuration
    @ConfigurationProperties(prefix = "app")
    @Data
    public static class App {
        private Geo geo = new Geo();

        @Data
        public static class Geo {
            private double verifyRadiusMeters;
        }
    }

    @Configuration
    @ConfigurationProperties(prefix = "google")
    @Data
    public static class Google {
        private Storage storage = new Storage();

        @Data
        public static class Storage {
            private String bucketName;
        }
    }
}
