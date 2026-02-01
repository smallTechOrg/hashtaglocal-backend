package org.smalltech.hashtaglocal_backend.repository;

import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.UserAuthSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserAuthSessionRepository extends JpaRepository<UserAuthSessionEntity, Long> {
	Optional<UserAuthSessionEntity> findByAccessToken(String accessToken);
	Optional<UserAuthSessionEntity> findByRefreshToken(String refreshToken);
}
