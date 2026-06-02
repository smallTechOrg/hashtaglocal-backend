package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.DeviceTokenEntity;
import org.smalltech.hashtaglocal_backend.model.Platform;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceTokenRepository extends JpaRepository<DeviceTokenEntity, Long> {

  Optional<DeviceTokenEntity> findByToken(String token);

  List<DeviceTokenEntity> findAllByUserId(Long userId);

  @Modifying
  @Query("delete from DeviceTokenEntity d where d.user.id = :userId and d.platform = :platform")
  int deleteByUserIdAndPlatform(@Param("userId") Long userId, @Param("platform") Platform platform);

  @Modifying
  @Query("delete from DeviceTokenEntity d where d.token = :token")
  int deleteByToken(@Param("token") String token);
}
