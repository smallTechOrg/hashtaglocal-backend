package org.smalltech.hashtaglocal_backend.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.smalltech.hashtaglocal_backend.entity.LinkCache;
import org.smalltech.hashtaglocal_backend.model.LinkEmbedType;
import org.smalltech.hashtaglocal_backend.repository.LinkCacheRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the shared link-preview cache in its OWN transaction, so a concurrent duplicate insert
 * (unique {@code canonical_url}) fails in isolation without rolling back the caller's scrape
 * result. See FEED_DESIGN.md §6.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LinkCacheWriter {

  private final LinkCacheRepository linkCacheRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveIfAbsent(
      String canonicalUrl,
      String title,
      String description,
      String siteName,
      String faviconUrl,
      LinkEmbedType embedType) {
    if (canonicalUrl == null || linkCacheRepository.findByCanonicalUrl(canonicalUrl).isPresent()) {
      return;
    }
    try {
      // Text metadata only — no image_media FK. The re-hosted image belongs to the caller's
      // (still-open) transaction, which this separate transaction can't reference yet.
      linkCacheRepository.save(
          LinkCache.builder()
              .canonicalUrl(canonicalUrl)
              .title(title)
              .description(description)
              .siteName(siteName)
              .faviconUrl(faviconUrl)
              .embedType(embedType != null ? embedType : LinkEmbedType.LINK)
              .build());
    } catch (DataIntegrityViolationException dup) {
      // Another scrape inserted the same canonical URL first — fine.
      log.debug("link_cache already populated for {}", canonicalUrl);
    }
  }
}
