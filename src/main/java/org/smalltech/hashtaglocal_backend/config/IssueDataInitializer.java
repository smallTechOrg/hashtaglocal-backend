package org.smalltech.hashtaglocal_backend.config;

import lombok.RequiredArgsConstructor;
import org.smalltech.hashtaglocal_backend.repository.LocalityRepository;
import org.smalltech.hashtaglocal_backend.repository.LocationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
@Profile("!test")
public class IssueDataInitializer implements CommandLineRunner {

	private final LocalityRepository localityRepository;
	private final LocationRepository locationRepository;

	@Override
	@Transactional
	public void run(String... args) {
	}
}
