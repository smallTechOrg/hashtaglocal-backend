package org.smalltech.hashtaglocal_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class HashtagLocalBackendJavaApplication {
  public static void main(String[] args) {
    SpringApplication.run(HashtagLocalBackendJavaApplication.class, args);
  }
}
