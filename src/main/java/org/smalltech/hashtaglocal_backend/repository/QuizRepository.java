package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuizRepository extends JpaRepository<QuizEntity, Long> {}
