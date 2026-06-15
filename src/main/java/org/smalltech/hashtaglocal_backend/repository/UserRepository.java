package org.smalltech.hashtaglocal_backend.repository;

import java.util.List;
import java.util.Optional;
import org.smalltech.hashtaglocal_backend.entity.Locality;
import org.smalltech.hashtaglocal_backend.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByUsername(String username);

  /**
   * The distinct localities that saved users belong to ({@code users.location_id} → {@code
   * locations.locality_id}). The bulletin jobs run over exactly this set, so a locality is picked
   * up automatically the day after its first user appears.
   */
  @Query(
      "SELECT DISTINCT loc.locality FROM UserEntity u JOIN u.location loc "
          + "WHERE loc.locality IS NOT NULL")
  List<Locality> findDistinctUserLocalities();
}
