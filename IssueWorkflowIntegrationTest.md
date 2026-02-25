Test for all the Issue Changes.

Single Integration Test (orchestrate entire workflow)

1. Happy Case

Issue is Reported.
Single Media (this is a constraint)
Issue action PENDING.
Issue ONHOLD
No one should be able to see this issue, except same user who should be able to see it.
Then admin approves it. action APPROVED.
Issue transitions to OPEN.
Everyone able to see the issue.

Same user Verify it with single media.
Issue action PENDING.
Issue OPEN.
No one should be able to see this media/verification/count except the same user, who should be able to see it.
Admin approves it: action APPROVED.
Issue stays OPEN.
Everyone to see media/verification/count etc.

SAme user Resolve it with single media.
Issue action PENDING.
Issue PENDING.
Everyone can see the status as PENDING. 
But no one can see the media.
In all the status filters and counts, this issue shows up same as just like OPEN issue.
Admin approves it: action APPROVED.
Issue transtions to RESOLVED.
Everyone sees it as resolved, in counts etc.

2. REJECTED Cases 

When admin rejects REPORT action issue transitions to REJECTED.

Wehn admin rejects VERIFY actio issues stays open but the media, verification, count doesn't up.

When admin rejects RESOLVE action issues transitions back to OPEN, media doesn't show up anywhere, nor count.

3. Other + Failure cases.

When issue is ONHOLD.
Same user can still VERIFY issue.
Admin can selectively APPROVE the issue but the VERIFY.
IN that case issue stays open but VERIFY not listed.



Add other cases as you see fit.
