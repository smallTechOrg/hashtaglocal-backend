package org.smalltech.hashtaglocal_backend.model;

/** How a scraped link should be rendered. See FEED_DESIGN.md §4.2 / §6. */
public enum LinkEmbedType {
  /** Plain link card (Open Graph title/description/image). */
  LINK,
  /** Video embed (e.g. oEmbed player). */
  VIDEO,
  /** Rich oEmbed HTML embed. */
  RICH
}
