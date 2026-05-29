package org.smalltech.hashtaglocal_backend.service;

/** Scraping of link previews (Open Graph / oEmbed) for LINK posts. See FEED_DESIGN.md §6. */
public interface LinkScrapeService {

  /**
   * Scrape the link on the given post's content row and populate the preview fields, flipping
   * {@code scrapeStatus} to {@code OK} or {@code FAILED}. Uses the {@code link_cache} for dedupe.
   * Runs in its own transaction; call from an already-async / post-commit context.
   */
  void scrape(Long feedPostId);
}
