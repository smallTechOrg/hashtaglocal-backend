# Feed / Broadcast Channel — Backend Tech Design

> Working name: **Feed** (UI calls it "chat"). A per-hashtag public broadcast
> timeline that carries text, link previews, issues, events, bulletins, quizzes,
> and polls. Backend models it as an append-only **feed**, not a realtime chat —
> the chat *look* is a frontend concern.

---

## 1. Goals & non-goals

**Goals**
- One public, read-anywhere timeline **per hashtag** (`localities.hashtag`), sitting
  alongside map / issues / events as a first-class feature.
- Multi-modal posts: plain text, link cards (scraped), shared issues, shared
  events, admin bulletins, polls, quizzes.
- Rich link handling: scrape Open Graph / oEmbed server-side so the client can
  render a native card, with a webview fallback URL.
- Reuse existing primitives: `Locality` (hashtag), `Location` (PostGIS),
  `MediaEntity` (GCS signed URLs), `UserEntity`, `NewAPIResponse<T>`,
  `@AuthenticationPrincipal Long viewerUserId`.

**Non-goals (v1)**
- No WebSocket / SSE / push realtime delivery. Client polls / paginates.
- No threaded replies or DMs. (Reactions + flat comments are a later phase.)
- No per-user feed personalization beyond hashtag membership / location.

**Confirmed product decisions** (drive the rest of this doc):
1. **Posting** — only logged-in users **with location enabled**; the post is
   geo-attributed to the locality their location falls in. Every user post is
   **auto-moderated by AI** before it goes public (§8).
2. **Auto-posting** — new **issues** auto-create an `ISSUE_REF` post in their
   locality. **Events are not** auto-posted in v1 (they're externally scraped);
   when event creation moves in-house, they will be (`EVENT_REF`, future).
3. **Reads are fully public** — no login required. `viewerUserId` is nullable;
   viewer context (voted/answered) is only populated when logged in.
4. **No realtime** — client polls / pull-to-refresh.

---

## 2. Design approach: a two-table spine, no table-per-type

The timeline is **one `feed_posts` table** (the spine, what we paginate over)
plus **one `feed_post_content` table** that holds the *content* of every post,
whatever its kind. `feed_post_content` carries:
- **FK columns to entities that already have their own tables** —
  `issue_id`, `event_id`, `media_id` (primary/cover media). No data is copied;
  we read the real entity at render time.
- **A small fixed set of generic structured columns** that cover the common
  shape of any rich content — `title`, `text`, `url`, `image_media_id`,
  `embed_html`, `scrape_status` — so link cards, bulletins, polls and quizzes
  reuse the same columns instead of each getting a table.
- **A `data` JSONB column** for the type-specific tail (poll options, quiz
  questions/options, OG extras). New content kinds add fields here — **no new
  table, no migration** (important: this project is `ddl-auto: update` with no
  migration tool, so avoiding schema churn is a real win).

```
        ┌──────────────────────────┐        ┌──────────────────────────────┐
        │        feed_posts         │ 1───1 │      feed_post_content        │
        │ id, locality_id (hashtag) │        │ feed_post_id PK/FK            │
        │ author_user_id            │        │ issue_id   FK (nullable)     │
        │ kind (enum)               │        │ event_id   FK (nullable)     │
        │ status, pinned            │        │ media_id   FK (nullable)     │
        │ location_id (nullable)    │        │ image_media_id FK (nullable) │
        │ published_at              │        │ title, text, url             │
        │ created_at / updated_at   │        │ embed_html, scrape_status    │
        └──────────────────────────┘        │ data  jsonb  (type tail)     │
                  │                          └──────────────────────────────┘
                  │ only the two things the DB must compute over get their own tables:
        ┌─────────┴───────────┬──────────────────────┬───────────────────────┐
        │  feed_votes         │  feed_quiz_answers    │  feed_moderation      │
        │ (poll, COUNT/unique)│ (per-user, unique)    │ (AI verdict + audit)  │
        └─────────────────────┴──────────────────────┴───────────────────────┘
```

**Why this split and not table-per-type:**
- Pagination / "latest N for #hashtag" is a single indexed query on `feed_posts`.
- Adding a content type = a new `kind` + a `data` shape. No entity, repo, or
  schema change — the dynamic property we want.
- We still keep **relational tables only where the DB must aggregate or enforce
  uniqueness**: poll **votes** and quiz **answers** (COUNT per option, "did this
  user vote", unique constraints) — these can't live in a per-post JSONB without
  rewriting the row on every vote and racing concurrent writers. `feed_moderation`
  stays relational too because the admin queue queries it directly.
- A multi-image gallery uses a `feed_post_media (feed_post_id, media_id, position)`
  join (ordered); single cover image is just `feed_post_content.media_id`.

---

## 3. Post kinds (`FeedPostKind` enum)

All content lives in the single `feed_post_content` row; `kind` tells the client
(and mapper) which columns/`data` keys are meaningful.

| kind        | uses on `feed_post_content`                                  | who creates                |
|-------------|--------------------------------------------------------------|----------------------------|
| `TEXT`      | `text`                                                       | any user                   |
| `MEDIA`     | `media_id` (or `feed_post_media` gallery) + optional `text`  | any user                   |
| `LINK`      | `url`, `title`, `text`, `image_media_id`, `embed_html`, `scrape_status` | any user        |
| `ISSUE_REF` | `issue_id` FK                                                | **system, auto on new issue** + user share |
| `EVENT_REF` | `event_id` FK                                                | user share only (deferred — §1.2) |
| `BULLETIN`  | `title`, `text` + optional `data.poll` / `data.quiz`         | ADMIN / system (cron)      |
| `POLL`      | `text` (question) + `data.options[]`; votes → `feed_votes`   | user / ADMIN               |
| `QUIZ`      | `title` + `data.questions[]`; answers → `feed_quiz_answers`  | ADMIN / system             |

> A bulletin is a composite: its `data` can embed a poll/quiz definition. Same
> table, same row — `kind=BULLETIN` just means "render the rich layout."

Enums follow the existing convention: `@Enumerated(EnumType.STRING)`, stored as
VARCHAR, validated in Java (`IssueTypeModel`, `EventPortalModel` precedent).

---

## 4. Entities (JPA, matching house style)

All entities: Lombok `@Data/@Builder/@NoArgs/@AllArgs`, `@PrePersist/@PreUpdate`
timestamps like `EventEntity`, `ddl-auto: update` creates the tables (no Flyway
in this project today — schema is Hibernate-managed).

### 4.1 `FeedPostEntity` → `feed_posts` (the spine)
```
id                 Long PK
locality           ManyToOne → Locality      (the #hashtag channel; indexed)
author             ManyToOne → UserEntity    (nullable for system posts)
kind               FeedPostKind (enum, varchar)
status             FeedPostStatus (see §8 — AI moderation lifecycle)
pinned             boolean default false
location           ManyToOne → Location (nullable — geo-tag a post)
content            OneToOne → FeedPostContentEntity (cascade, mappedBy)
moderation         OneToOne → FeedModerationEntity (nullable; AI verdict + audit)
publishedAt        LocalDateTime (nullable; future = scheduled bulletin)
createdAt / updatedAt   (@PrePersist/@PreUpdate like EventEntity)
```
Indexes: `(locality_id, status, created_at DESC, id DESC)` for the keyset timeline
query; `(locality_id, pinned)` for pinned posts; `(status, created_at)` for the
moderation sweeper.

### 4.2 `FeedPostContentEntity` → `feed_post_content` (the universal payload)
One row per post; holds *all* content regardless of kind. This is the table that
replaces the per-type tables.
```
feedPostId     Long PK + FK → feed_posts (shared PK, @MapsId)
-- references to entities that already have their own tables (no copy):
issue          ManyToOne → IssueEntity  (nullable)
event          ManyToOne → EventEntity  (nullable)
media          ManyToOne → MediaEntity  (nullable; single/cover media)
imageMedia     ManyToOne → MediaEntity  (nullable; e.g. re-hosted OG image)
-- generic structured columns reused across kinds:
title          varchar(500)  (nullable)
text           text(4000)    (nullable)  -- post body / poll question / etc.
url            varchar(5000) (nullable)  -- shared link (LINK)
canonicalUrl   varchar(5000) (nullable)  -- normalized, for dedupe/cache lookup
embedHtml      text          (nullable)  -- oEmbed (LINK)
embedType      LinkEmbedType (nullable)  -- LINK | VIDEO | RICH
scrapeStatus   LinkScrapeStatus (nullable) -- PENDING | OK | FAILED (LINK only)
-- the type-specific tail:
data           jsonb (Map<String,Object>) -- poll options, quiz Qs, OG extras
```
- **LINK**: `url`/`canonicalUrl` set immediately, `scrapeStatus=PENDING`; the
  async scraper (§6) fills `title/text/embedHtml/imageMedia` and flips to `OK`.
  An optional `link_cache(canonical_url UNIQUE → scraped fields)` table lets us
  scrape a shared URL once and copy results — a *cache*, not a per-post table.
- **POLL**: `text` = question; `data.options = [{id, label}]`,
  `data.multi_choice`, `data.closes_at`. Votes are **not** in `data` (see 4.3).
- **QUIZ**: `data.questions = [{id, prompt, media_id?, options:[{id,label,correct}]}]`.
  The `correct` flag is **stripped in the unanswered DTO** so answers aren't
  leaked; correctness is computed server-side on submit.
- Multi-image gallery: `feed_post_media (feed_post_id, media_id, position)` join;
  media URLs are freshly signed GCS URLs at read time (reuse `GcsMediaService`).

### 4.3 `FeedVoteEntity` → `feed_votes` (relational — must COUNT + dedupe)
```
id, feed_post_id FK, option_id (varchar — matches data.options[].id),
user_id FK, createdAt
UNIQUE(feed_post_id, user_id)            when poll is single-choice
UNIQUE(feed_post_id, option_id, user_id) always
```
Aggregate counts come from `GROUP BY option_id` (or a denormalized counter in
`data` updated transactionally if render cost demands it). Source of truth = rows.

### 4.4 `FeedQuizAnswerEntity` → `feed_quiz_answers` (relational — per-user, unique)
```
id, feed_post_id FK, question_id (varchar), option_id (varchar),
user_id FK, correct boolean, createdAt
UNIQUE(feed_post_id, question_id, user_id)
```
Powers "did this user answer / their score"; correctness stored at submit time.

---

## 5. API surface

Envelope: `NewAPIResponse<T>`. JSON is snake_case (Jackson global config).
Auth: `@AuthenticationPrincipal Long viewerUserId` (nullable ⇒ anonymous read OK).
New controller `FeedController` under `/api/v1/feed`.

> **Reads are public** (decision 3) — the security config must permit
> `GET /api/v1/feed/**` without authentication, like other public read paths.
> **Writes require auth + device location** (decision 1).

### Read
```
GET  /api/v1/feed?hashtag=tnagar&cursor=<id>&limit=30
        → FeedListResponseData { posts: [FeedPostData], next_cursor }
        Keyset pagination on (created_at, id) DESC. Pinned posts returned in a
        separate `pinned` array on the first page.
GET  /api/v1/feed/{postId}        → single FeedPostData (for deep links)
```
`FeedPostData` is a discriminated DTO assembled from `feed_posts` +
`feed_post_content`: always has `id, kind, author, created_at, text,
viewer_context{voted, answered}`, plus the content fields meaningful for `kind`
(`url/title/image` for LINK, `issue` for ISSUE_REF, `options+counts` for POLL,
etc.). Media URLs are freshly signed GCS URLs (reuse `GcsMediaService`).

### Write  (auth required)
```
POST /api/v1/feed
   body: { lat?, lng?, hashtag?, kind, text?, link_url?, media_ids?,
           poll?{question,options[],multi_choice,closes_at},
           issue_id?, event_id? }

   Locality resolution depends on the caller's role:
   • USER  → MUST send (lat,lng). Locality is RESOLVED SERVER-SIDE via the PostGIS
             containment query (Locality.geoBoundary) — NOT taken from the client.
             Missing coords, or coords outside any locality → 422. This enforces
             "logged-in + location on".
   • ADMIN → posts to the hashtag they have open: sends `hashtag` directly,
             NO location required, any hashtag allowed. (See §5.1.)

   → USER posts are created status=PENDING_AI and queued for AI moderation (§8);
     not visible in the public timeline until the verdict lands. ADMIN posts are
     created PUBLISHED directly (trusted authors skip AI).
   → For LINK, sets scrape_status=PENDING on the content row and enqueues scrape.
   → Returns the post immediately (author sees their own pending post).
POST /api/v1/feed/{postId}/poll/vote     { option_ids[] }
POST /api/v1/feed/{postId}/quiz/answer   { answers:[{question_id, option_id}] }
                                         → returns per-question correctness + score
POST /api/v1/feed/{postId}/react         { type }    (later phase)
POST /api/v1/feed/{postId}/report        flag for moderation
```

### Admin / system  (UserRole.ADMIN, like `IssueAdminController`)
```
GET   /api/v1/admin/feed/moderation?verdict=BLOCKED|FLAGGED|ALL&cursor=&limit=
        → review queue: posts the AI blocked/flagged, with the AI reason &
          category, newest first. This is the data behind the admin moderation page.
POST  /api/v1/admin/feed/{postId}/approve   { note? }
        → admin overrides AI: AI-BLOCKED post becomes PUBLISHED.
POST  /api/v1/admin/feed/{postId}/hide      { reason }
        → admin overrides AI: AI-APPROVED (or live) post becomes ADMIN_HIDDEN.
PATCH /api/v1/admin/feed/{postId}           { pinned }   pin/unpin
POST  /api/v1/admin/feed/bulletin           create BULLETIN (Phase 2)
```
Every admin override is recorded on `FeedModerationEntity` (who/when/why) so the
AI verdict and the human decision are both auditable.

### 5.1 Posting authority by role (decision)

| | how locality is chosen | location required | which hashtags | AI moderation |
|---|---|---|---|---|
| **USER**  | server resolves from `(lat,lng)` via PostGIS containment | **yes** | only the one their location is in | **yes** (PENDING_AI) |
| **ADMIN** | takes the `hashtag` they have open, as sent | **no** | **any** hashtag | **no** (PUBLISHED directly) |

The server keys off the authenticated principal's `UserRole`: for `ADMIN` it
trusts the supplied `hashtag` and skips both the location check and AI gate; for
`USER` it ignores any client-sent hashtag and derives it from coordinates. This
is enforced in `FeedService.create(...)`, not the controller, so every code path
(future bulk/import endpoints) inherits it.

---

## 6. Link scraping pipeline

Reuses the existing external scrape service pattern (`ScrapeApiClient`,
`PortalIssueScrapeClient`, `events.scrape.url`) and `RestClient`/timeout config
already in `application.yaml`.

1. On `POST /feed` with a `link_url`: normalize → `canonicalUrl` on the content
   row. If a `link_cache` row for that `canonicalUrl` already has scraped fields,
   copy them in and set `scrape_status=OK` immediately. Otherwise set
   `scrape_status=PENDING` and return the post right away.
2. **Async scrape** (`@Async` + a `@Scheduled` sweeper for retries — same shape
   as `EventGeocodingService.run()` / the events cron):
   - Fetch URL, parse Open Graph / Twitter Card / oEmbed (jsoup — add dep).
   - Re-host the OG image to GCS via `GcsMediaService` (CDN URLs expire — this is
     the exact rationale already documented on `EventEntity.media`).
   - Set title/description/embed, `scrapeStatus=OK`, `scrapedAt`.
   - On failure → `FAILED`; client falls back to raw URL + webview.
3. Client renders the card when `OK`; while `PENDING` it shows the bare link;
   tapping always allows opening the URL in an in-app webview.

> Security: scraping fetches arbitrary user-supplied URLs ⇒ SSRF risk. Enforce
> an allowlist of schemes (http/https only), block private/link-local IP ranges,
> cap response size and redirects, and set the short connect/read timeouts that
> already exist in config. This must be in the implementation, not optional.

---

## 7. Bulletins (daily / weekly)

- A `@Scheduled` cron job (mirror `events.scrape.cron` config style:
  `feed.bulletin.cron`, disable with `-`) generates BULLETIN posts per active
  hashtag — e.g. "This week in #tnagar": top issues resolved, upcoming events,
  plus an attached quiz/poll.
- Content can be assembled from existing services (`IssueStoryService` already
  produces issue stories — reuse it) and `EventService`.
- Scheduling: a bulletin row with `publishedAt` in the future is created with
  `status=PENDING`; the read query filters `publishedAt <= now`. A sweeper flips
  to `PUBLISHED` (or the query just uses `publishedAt`), so we get scheduled
  posts for free.

---

## 8. Moderation — AI-first, admin-override (core of v1)

Every **regular-user** post is auto-moderated by AI before it becomes public.
**Trusted authors skip the AI gate and publish directly**: admins, the bulletin
cron, and auto issue-refs. (Admins are moderators — gating their own posts is
pointless, and they can hide anything after the fact.) The flow is a state
machine so the AI verdict and any human override are both first-class and
auditable.

### `FeedPostStatus` lifecycle
```
                 user POST
                    │
                    ▼
              PENDING_AI ───(AI: allow)────────▶ PUBLISHED ──(admin hide)─▶ ADMIN_HIDDEN
                    │                                  ▲
                    ├──(AI: block)──▶ AI_BLOCKED ──────┘ (admin approve / override)
                    │                                  
                    └──(AI: uncertain)──▶ FLAGGED ──(admin approve)─▶ PUBLISHED
                                              └──────(admin hide)────▶ ADMIN_HIDDEN
              (AI service error / timeout) ──▶ FLAGGED  (fail-safe: never auto-publish)
```
- **Public timeline shows `PUBLISHED` only.** `PENDING_AI`, `FLAGGED`,
  `AI_BLOCKED`, `ADMIN_HIDDEN` are invisible to everyone except the author (who
  sees their own pending/blocked post with its status) and admins.
- Fail-safe default: if the AI call errors or times out, the post goes to
  `FLAGGED` (human review), never silently public.

### `FeedModerationEntity` → `feed_moderation` (1:1 with post)
```
id, feed_post_id FK,
aiVerdict (ALLOW | BLOCK | UNCERTAIN), aiCategory (e.g. SPAM, HATE, NSFW,
  HARASSMENT, OFF_TOPIC), aiConfidence (double), aiReason (text, 1000),
aiModel (varchar — which model/version), evaluatedAt,
adminAction (NONE | APPROVED | HIDDEN), adminUser FK (nullable),
adminNote (text), adminActedAt
```
This is the read model for the admin moderation page and the audit trail.

### Moderation runner
- Async, mirroring the link-scrape pipeline: post is created `PENDING_AI`, a job
  picks it up (`@Async` + `@Scheduled` retry sweeper for stuck rows), calls the
  classifier, writes `FeedModerationEntity`, transitions status.
- **Classifier is behind a `FeedModerationClient` interface** so the AI provider
  is swappable. v1 impl calls the **Gemini API over plain REST** using Spring's
  `RestClient` (same HTTP approach already used by the scrape clients — **no
  vendor SDK dependency**). It sends post text + link metadata + a category
  rubric and requests a **structured JSON response** (Gemini
  `responseMimeType: application/json` + a response schema) so the verdict parses
  deterministically. Use a cheap model (`gemini-2.0-flash`-class). A keyword/rule
  pre-filter can short-circuit obvious cases before spending an API call.
- Media moderation in v1 is text/link only; image moderation is a later add
  (the interface leaves room for it).

### Other safety
- `POST /feed/{id}/report` lets any logged-in user flag a *published* post; a
  report transitions it back to `FLAGGED` for admin review (or just records a
  flag count — admin queue surfaces it). 
- Rate-limit post creation per user (simple per-user/min counter in v1).
- SSRF protections on link scraping (§6).
- Soft-delete only — rows are never hard-deleted (audit/karma consistency).

---

## 9. Phasing

**Phase 1 (MVP):** `feed_posts` spine, `TEXT` + `MEDIA` + `LINK` (with async
scrape), **auto `ISSUE_REF`** on new issues, user-shared `EVENT_REF`, public
keyset-paginated read, **AI auto-moderation pipeline + admin review/override
page**, location-resolved posting, admin pin/hide. This delivers the "chat that
shows links, issues, events — safely moderated" experience.

**Phase 2:** `POLL` + `QUIZ` (definition in `feed_post_content.data`;
`feed_votes` + `feed_quiz_answers` tables for vote/answer endpoints);
`BULLETIN` kind + scheduled cron generation.

**Phase 3:** reactions, flat comments, per-user notification of new posts in
followed hashtags (and only here consider SSE/push if product needs realtime).

---

## 10. Files to add (Phase 1)

```
entity/        FeedPostEntity, FeedPostContentEntity, FeedModerationEntity,
               LinkCache (+ FeedPostMedia join)   [+ Phase 2: FeedVoteEntity, FeedQuizAnswerEntity]
model/         FeedPostKind, FeedPostStatus, LinkEmbedType, LinkScrapeStatus,
               AiVerdict, AiCategory, AdminModerationAction
repository/    FeedPostRepository (keyset + moderation-queue queries),
               FeedPostContentRepository, LinkCacheRepository, FeedModerationRepository
service/       FeedService (create; role-based locality resolution),
               FeedQueryService, LinkScrapeService (+Async),
               FeedModerationService (+Async), FeedModerationClient (interface)
               + GeminiFeedModerationClient (RestClient impl, no SDK),
               IssueCreatedEvent listener → emits auto ISSUE_REF post
controller/    FeedController, FeedAdminController
model/request/ CreateFeedPostRequest (lat,lng?,hashtag?,kind,...), FeedQueryRequest (hashtag,cursor,limit)
model/response/FeedPostData, FeedListResponseData, ModerationQueueData
mapper/        FeedPostMapper (post+content → DTO, signs media URLs)
config/        async executor; RestClient for scraping + Gemini (no SDK)
build.gradle:  add jsoup (OG/oEmbed parsing). No AI SDK — Gemini via RestClient.
application.yaml: feed.moderation.{enabled,gemini-api-key,model,endpoint},
                  feed.scrape.*, rate limits
```

---

## 11. Decisions locked / remaining smaller questions

**Locked (from product):**
1. **User** posting = logged-in **+ location on**; locality resolved server-side
   from (lat,lng); every user post **AI-moderated** before going public, with an
   admin page to override (approve AI-blocked, hide AI-approved).
2. **Admin** posting = posts to the **hashtag they have open**, **no location
   required, any hashtag allowed**, and **skips the AI gate** (publishes directly,
   can be hidden after the fact). See §5.1.
3. New **issues auto-post** (`ISSUE_REF`); **events do not** in v1 (deferred until
   event creation is in-house).
4. Reads are **fully public**, no login.
5. **No realtime** in v1.
6. Data model = **two-table spine** (`feed_posts` + `feed_post_content`); only
   votes, quiz answers, and moderation get their own tables. New content kind =
   new `kind` + `data` shape, no migration.

**Still to confirm (don't block Phase 1 start):**
- AI moderation provider/model — **Gemini via REST** (`gemini-2.0-flash`-class,
  structured-JSON output, no SDK) behind a swappable `FeedModerationClient`.
  Reads `GEMINI_API_KEY` from env (wired into `application.yaml` like
  `GOOGLE_MAPS_API_KEY`). Confirm the model choice.
- The category taxonomy (`SPAM / HATE / NSFW / HARASSMENT / OFF_TOPIC / …`) — final
  list and which categories hard-block vs. flag-for-review.
- Should an author be able to **edit & resubmit** an AI-blocked post, or is a block
  terminal (must repost)? (Affects whether posts are mutable.)
- Image moderation timing — v1 text/link only; when do images need it?
```
