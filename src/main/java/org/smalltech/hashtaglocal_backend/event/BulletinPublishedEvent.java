package org.smalltech.hashtaglocal_backend.event;

/**
 * Published after a locality's BULLETIN feed post has been created (and committed). The bulletin
 * notification listener sends the "Daily Bulletin" push in response.
 */
public record BulletinPublishedEvent(Long bulletinId, Long localityId) {}
