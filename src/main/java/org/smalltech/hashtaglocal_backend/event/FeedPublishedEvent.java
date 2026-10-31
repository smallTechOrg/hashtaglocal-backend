package org.smalltech.hashtaglocal_backend.event;

import org.smalltech.hashtaglocal_backend.entity.BulletinEntity;

/**
 * Published after a locality's feed post has been created (and committed). Today the post is a
 * BULLETIN, so the event carries the bulletin; the bulletin notification listener sends the "Daily
 * Bulletin" push in response.
 *
 * <p>The listener runs on another thread after commit, so it only reads {@code bulletin.getId()}
 * and reloads the bulletin (with its locality) itself rather than trusting this detached object.
 */
public record FeedPublishedEvent(BulletinEntity bulletin) {}
