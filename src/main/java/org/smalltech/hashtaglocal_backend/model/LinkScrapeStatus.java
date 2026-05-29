package org.smalltech.hashtaglocal_backend.model;

/** Status of the async link-preview scrape for a {@code LINK} post. See FEED_DESIGN.md §6. */
public enum LinkScrapeStatus {
  /** Queued, not yet scraped. Client shows the bare URL meanwhile. */
  PENDING,
  /** Scraped successfully; preview fields are populated. */
  OK,
  /** Scrape failed; client falls back to the raw URL + webview. */
  FAILED
}
