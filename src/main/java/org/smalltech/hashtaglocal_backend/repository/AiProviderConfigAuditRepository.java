package org.smalltech.hashtaglocal_backend.repository;

import org.smalltech.hashtaglocal_backend.entity.AiProviderConfigAuditEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiProviderConfigAuditRepository
    extends JpaRepository<AiProviderConfigAuditEntity, Long> {}
