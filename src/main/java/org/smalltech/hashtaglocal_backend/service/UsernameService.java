package org.smalltech.hashtaglocal_backend.service;

import org.smalltech.hashtaglocal_backend.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UsernameService {

  private final UserRepository userRepository;

  public UsernameService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public String normalizeUsername(String name) {
    if (name == null || name.isBlank()) {
      return "user";
    }
    return name.toLowerCase().replaceAll("\\s+", "").replaceAll("[^a-z0-9]", "");
  }

  public String generateUniqueUsername(String baseUsername) {
    String username = baseUsername;
    int attempt = 0;
    while (userRepository.findByUsername(username).isPresent()) {
      int random = 100 + (int) (Math.random() * 900);
      username = baseUsername + random;
      attempt++;
      if (attempt > 3) {
        username = baseUsername + (System.currentTimeMillis() % 100000);
        break;
      }
    }
    return username;
  }
}
