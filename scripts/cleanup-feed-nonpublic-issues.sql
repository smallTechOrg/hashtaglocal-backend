-- One-shot cleanup: hide ISSUE_REF feed posts whose linked issue is NOT publicly visible.
-- The feed must only ever show issues that are OPEN (approved) or RESOLVED. The original prod
-- backfill predated the status filter and posted issues regardless of status (ONHOLD/REJECTED/
-- PENDING), so this hides those. Mirrors FeedPostRepository.hideIssueRefPosts at the data layer.
--
-- Run the SELECT first to see what will change, then the UPDATE.

-- Preview: how many ISSUE_REF posts reference a non-OPEN/RESOLVED issue and are still visible.
SELECT i.status AS issue_status, COUNT(*) AS posts_to_hide
FROM feed_posts p
JOIN feed_post_content c ON c.feed_post_id = p.id
JOIN issues i ON i.id = c.issue_id
WHERE p.kind = 'ISSUE_REF'
  AND p.status <> 'ADMIN_HIDDEN'
  AND i.status NOT IN ('OPEN', 'RESOLVED')
GROUP BY i.status
ORDER BY posts_to_hide DESC;

-- Apply: hide them so they drop out of the public timeline (which shows PUBLISHED only).
UPDATE feed_posts p
SET status = 'ADMIN_HIDDEN'
FROM feed_post_content c, issues i
WHERE c.feed_post_id = p.id
  AND i.id = c.issue_id
  AND p.kind = 'ISSUE_REF'
  AND p.status <> 'ADMIN_HIDDEN'
  AND i.status NOT IN ('OPEN', 'RESOLVED');
