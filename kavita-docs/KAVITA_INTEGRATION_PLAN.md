# Mimiral × Kavita Deep Integration Plan

> Based on Kavita API v0.9.0.6 — 471 endpoints across 30+ controllers
> Generated: 2026-06-07

## Priority Ranking (by user impact × feasibility)

| Rank | Feature | Impact | Effort | Dependencies |
|------|---------|--------|--------|--------------|
| 1 | Auth (JWT + API Key) | ★★★★★ | M | None (prerequisite) |
| 2 | Reading Progress Sync | ★★★★★ | M | Auth |
| 3 | Mark Read/Unread | ★★★★☆ | S | Auth |
| 4 | Continue Reading + Next/Prev Chapter | ★★★★☆ | S | Auth, Progress |
| 5 | Bookmark Sync | ★★★★☆ | M | Auth |
| 6 | Want To Read | ★★★☆☆ | S | Auth |
| 7 | Collections | ★★★☆☆ | M | Auth |
| 8 | Reading Lists | ★★★☆☆ | M | Auth |
| 9 | Annotations | ★★★☆☆ | L | Auth |
| 10 | OPDS Enhancements | ★★☆☆☆ | S | Auth (API Key) |
| 11 | Scrobbling | ★★☆☆☆ | M | Auth, Kavita+ |
| 12 | Device Support (Send-to) | ★★☆☆☆ | M | Auth |
| 13 | Stats | ★★☆☆☆ | M | Auth |
| 14 | Koreader/Panels Sync (alternative) | ★☆☆☆☆ | M | Auth (API Key) |

---

## 1. AUTH — JWT Login Flow + API Key

**Priority: CRITICAL (prerequisite for all other features)**

### Current State
Mimiral likely uses an API key (OPDS) for basic access. Deep integration requires JWT-based authentication for the full REST API.

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Account/login` | POST | Login with username+password OR apiKey → returns `UserDto` with JWT token |
| `/api/Account/refresh-token` | POST | Refresh expiring JWT token |
| `/api/Account` | GET | Get current user (no tokens returned) |
| `/api/Account/refresh-account` | GET | Get up-to-date user account |
| `/api/Account/opds-url` | GET | Get OPDS URL for this user |
| `/api/Plugin/authenticate` | GET | Authenticate via API key (x-api-key header) → returns user+JWT |
| `/api/Account/auth-keys` | GET | List user's API keys |
| `/api/Account/create-auth-key` | POST | Create new API key |
| `/api/Account/auth-key` | DELETE | Delete API key |

### Implementation Approach

1. **Dual-auth strategy**: Support both JWT login (username/password) and API key authentication.
   - JWT: User enters credentials → `POST /api/Account/login` with `LoginDto { username, password }` → store JWT from response header/cookie → use `Authorization: Bearer <token>` for all subsequent API calls.
   - API Key: User enters API key → `GET /api/Plugin/authenticate` with `x-api-key` header → get JWT back. Use as fallback or for headless setups.

2. **Token lifecycle**:
   - Store JWT + refresh token securely in Android Keystore.
   - Intercept 401 responses → call `POST /api/Account/refresh-token` → retry.
   - If refresh fails, prompt re-login.

3. **API Key derivation**: After JWT login, call `GET /api/Account/opds-url` to get the OPDS URL (contains API key). Or list existing keys via `GET /api/Account/auth-keys`. Create one if needed.

4. **LoginDto schema**: `{ username: string?, password: string?, apiKey: string? }` — supports all three auth methods in one call.

### Data Model
```kotlin
data class KavitaAuthState(
    val serverUrl: String,
    val jwtToken: String?,
    val refreshToken: String?,
    val apiKey: String?,       // for OPDS / Plugin auth
    val opdsUrl: String?,
    val userId: Int,
    val username: String,
    val tokenExpiry: Instant,
    val roles: List<String>
)
```

---

## 2. READING PROGRESS SYNC

**Priority: P0 — Core reading experience across devices**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Reader/progress` | POST | Save current page progress (ProgressDto) |
| `/api/Reader/get-progress` | GET | Get progress for a chapter (?chapterId=X) |
| `/api/Reader/continue-point` | GET | Get the chapter to resume for a series (?seriesId=X) |
| `/api/Reader/has-progress` | GET | Check if user has progress on a series (?seriesId=X) |

### ProgressDto Schema
```json
{
  "volumeId": int,      // required
  "chapterId": int,     // required
  "pageNum": int,       // required
  "seriesId": int,      // required
  "libraryId": int,     // required
  "bookScrollId": string?,  // for epub scroll position
  "lastModifiedUtc": datetime // for conflict resolution
}
```

### Implementation Approach

1. **On open chapter**: `GET /api/Reader/get-progress?chapterId=X` → get server page number. Compare with local page number → use whichever is further (or server wins on tie).

2. **On page turn**: Debounce and batch-send `POST /api/Reader/progress` with `ProgressDto` every N seconds or every N pages (e.g., every 3 pages or 10 seconds). Include `lastModifiedUtc` for conflict detection.

3. **On close reader**: Immediately POST final progress. This is critical — don't rely on debounced sends.

4. **Continue Reading**: Use `GET /api/Reader/continue-point?seriesId=X` to show "Continue: Ch.X Pg.Y" on series detail and home screen.

5. **Conflict resolution**: Use `lastModifiedUtc` field. If server progress is newer than local, server wins. On first sync after offline reading, upload local progress if `lastModifiedUtc` is more recent.

6. **Offline queue**: When offline, queue progress POSTs in a Room database. On reconnect, flush the queue in order.

7. **Epub scroll position**: Pass `bookScrollId` for epub chapters to maintain scroll position across devices.

### Data Model
```kotlin
data class ReadingProgress(
    val chapterId: Int,
    val volumeId: Int,
    val seriesId: Int,
    val libraryId: Int,
    val pageNum: Int,
    val bookScrollId: String? = null,
    val lastModifiedUtc: Instant
)
```

---

## 3. MARK READ / UNREAD

**Priority: P1 — Essential library management**

### Endpoints

| Endpoint | Method | Scope |
|----------|--------|-------|
| `/api/Reader/mark-chapter-read` | POST | Single chapter |
| `/api/Reader/mark-read` | POST | Entire series |
| `/api/Reader/mark-unread` | POST | Entire series |
| `/api/Reader/mark-volume-read` | POST | All chapters in a volume |
| `/api/Reader/mark-volume-unread` | POST | All chapters in a volume |
| `/api/Reader/mark-multiple-read` | POST | Multiple volumes (same series) |
| `/api/Reader/mark-multiple-unread` | POST | Multiple volumes (same series) |
| `/api/Reader/mark-multiple-series-read` | POST | Multiple series |
| `/api/Reader/mark-multiple-series-unread` | POST | Multiple series |
| `/api/Tachiyomi/mark-chapter-until-as-read` | POST | Mark chapters up to number N as read |
| `/api/Tachiyomi/latest-chapter` | GET | Get latest read chapter for a series |

### Implementation Approach

1. **Chapter context menu**: "Mark as Read" / "Mark as Unread" → `POST mark-chapter-read` with `{ chapterId, seriesId, volumeId }`.

2. **Volume context menu**: "Mark Volume Read" / "Mark Volume Unread" → `POST mark-volume-read/unread`.

3. **Series context menu**: "Mark All Read" / "Mark All Unread" → `POST mark-read/mark-unread` with `seriesId`.

4. **Bulk operations**: Multi-select in library → "Mark Selected Read/Unread" → `POST mark-multiple-series-read/unread`.

5. **"Mark until"**: After finishing chapter N, optionally mark all prior chapters as read via `POST /api/Tachiyomi/mark-chapter-until-as-read` (useful for catching up).

6. **UI feedback**: After marking, refresh the progress indicators (page count read / total pages) locally without re-fetching the whole series.

---

## 4. CONTINUE READING + NEXT/PREV CHAPTER NAVIGATION

**Priority: P1 — Seamless reading flow**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Reader/continue-point` | GET | Get resume chapter for a series |
| `/api/Reader/next-chapter` | GET | Next logical chapter (?seriesId=X &volumeId=Y &chapterId=Z) |
| `/api/Reader/prev-chapter` | GET | Previous logical chapter |
| `/api/Reader/time-left` | GET | Estimated time to finish series |
| `/api/Reader/time-left-for-chapter` | GET | Estimated time to finish chapter |
| `/api/Series/on-deck` | GET | Series with progress (On Deck feed) |

### Implementation Approach

1. **"Continue" button on series**: `GET /api/Reader/continue-point?seriesId=X` → returns `ChapterDto` with the chapter to resume. Deep link directly into reader at that chapter + page.

2. **End-of-chapter navigation**: When user finishes a chapter, offer "Next Chapter →" using `GET /api/Reader/next-chapter`. Also "← Prev Chapter" at start.

3. **Reading list navigation**: When reading within a Reading List, use `GET /api/ReadingList/next-chapter` and `GET /api/ReadingList/prev-chapter` instead (respects list order).

4. **On Deck / Continue Reading shelf**: `GET /api/Series/on-deck` → populate home screen with in-progress series.

5. **Time estimates**: Show "≈ X hours left" on series detail using `GET /api/Reader/time-left`.

---

## 5. BOOKMARK SYNC

**Priority: P1 — Users expect bookmarks to survive device switches**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Reader/bookmark` | POST | Bookmark a page (chapterId, page) |
| `/api/Reader/unbookmark` | POST | Remove bookmark (chapterId, page) |
| `/api/Reader/chapter-bookmarks` | GET | List bookmarks for a chapter |
| `/api/Reader/volume-bookmarks` | GET | List bookmarks for a volume |
| `/api/Reader/series-bookmarks` | GET | List bookmarks for a series |
| `/api/Reader/all-bookmarks` | GET | List all user bookmarks |
| `/api/Reader/remove-bookmarks` | POST | Remove bookmarks for a series |
| `/api/Reader/bulk-remove-bookmarks` | POST | Bulk remove bookmarks |
| `/api/Reader/bookmark-image` | GET | Get the actual image for a bookmarked page |
| `/api/Reader/bookmark-info` | GET | Get dimension info for bookmark pages |
| `/api/Image/bookmark` | GET | Get bookmark image for display |
| `/api/Download/bookmarks` | GET | Download all bookmarks as zip |

### Implementation Approach

1. **In-reader bookmark**: Tap bookmark icon → `POST /api/Reader/bookmark` with `{ chapterId, page }`. Toggle UI state.

2. **Remove bookmark**: Tap again → `POST /api/Reader/unbookmark`.

3. **Bookmark viewer**: Dedicated screen/tab showing `GET /api/Reader/all-bookmarks`. Group by series → volume → chapter. Show thumbnail via `GET /api/Image/bookmark`.

4. **Series bookmark view**: `GET /api/Reader/series-bookmarks?seriesId=X` on series detail page.

5. **Sync strategy**: On app start, fetch `all-bookmarks` and merge with local. Server is source of truth. Local changes are POSTed immediately.

6. **Download bookmarks**: "Export Bookmarks" → `GET /api/Download/bookmarks?seriesId=X` → save zip of bookmarked page images.

---

## 6. WANT TO READ

**Priority: P2 — Basic library curation**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/want-to-read/v2` | GET | Get Want To Read list (filtered, paginated) |
| `/api/want-to-read/add-series` | POST | Add series to list (body: list of seriesIds) |
| `/api/want-to-read/remove-series` | POST | Remove series from list (body: list of seriesIds) |
| `/api/Opds/{apiKey}/want-to-read` | GET | OPDS feed for Want To Read |

### Implementation Approach

1. **Toggle on series detail**: "Want to Read" / "In Want to Read" toggle button → POST add/remove.

2. ** Want to Read tab**: Dedicated tab showing `GET /api/want-to-read/v2` with full filtering and pagination support.

3. **OPDS integration**: Expose via `GET /api/Opds/{apiKey}/want-to-read` for browsing the list through the existing OPDS flow.

4. **Auto-cleanup**: Kavita has `POST /api/Server/cleanup-want-to-read` which removes completed+fully-read series. Could trigger or just let server handle it on schedule.

---

## 7. COLLECTIONS

**Priority: P2 — Library organization**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Collection` | GET | Get all collections for user |
| `/api/Collection/single` | GET | Get single collection by ID |
| `/api/Collection/all-series` | GET | Get all collections containing a series |
| `/api/Collection/name-exists` | GET | Check if collection name exists |
| `/api/Collection/update` | POST | Update collection (title, summary, promoted) |
| `/api/Collection/update-series` | POST | Add series to collection (creates if id=0) |
| `/api/Collection/update-for-series` | POST | Update collection for a series (remove series) |
| `/api/Collection/promote-multiple` | POST | Promote/unpromote collections |
| `/api/Collection` | DELETE | Delete a collection |
| `/api/Collection/delete-multiple` | POST | Delete multiple collections |
| `/api/Series/series-by-collection` | GET | Get series grouped by collection (paginated) |

### OPDS Endpoints
| `/api/Opds/{apiKey}/collections` | GET | Browse collections via OPDS |
| `/api/Opds/{apiKey}/collections/{collectionId}` | GET | Browse series in collection via OPDS |

### Implementation Approach

1. **Collections browse**: `GET /api/Collection` → show list with covers. Tap into one → `GET /api/Series/series-by-collection?collectionId=X` with pagination.

2. **Create collection**: "New Collection" button → `POST /api/Collection/update-series` with `tagId=0` (creates new) + list of series IDs.

3. **Add/remove series**: On series detail, show "Add to Collection" picker → call `POST update-series`. Remove via `POST update-for-series`.

4. **Edit collection**: `POST /api/Collection/update` with `{ id, title, summary, promoted }`.

5. **Delete**: Swipe-to-delete or context menu → `DELETE /api/Collection`.

6. **OPDS route**: Add collections section to OPDS browser via `/api/Opds/{apiKey}/collections`.

---

## 8. READING LISTS

**Priority: P2 — Curated reading order**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/ReadingList` | GET | Get a single reading list by ID |
| `/api/ReadingList/lists` | GET | Get user's reading lists (paginated) |
| `/api/ReadingList/all` | GET | Get all reading lists (paginated) |
| `/api/ReadingList/lists-for-series` | GET | Lists containing a series |
| `/api/ReadingList/lists-for-chapter` | GET | Lists containing a chapter |
| `/api/ReadingList/items` | GET | Get all items in a list (with progress) |
| `/api/ReadingList/create` | POST | Create new reading list |
| `/api/ReadingList/update` | POST | Update list (title, summary) |
| `/api/ReadingList/update-position` | POST | Reorder item position |
| `/api/ReadingList/delete-item` | POST | Remove item from list |
| `/api/ReadingList/remove-read` | POST | Remove all fully-read items |
| `/api/ReadingList/update-by-series` | POST | Add all chapters from a series |
| `/api/ReadingList/update-by-multiple` | POST | Add chapters from volumes/chapters |
| `/api/ReadingList/update-by-multiple-series` | POST | Add chapters from multiple series |
| `/api/ReadingList/next-chapter` | GET | Next chapter in list order |
| `/api/ReadingList/prev-chapter` | GET | Prev chapter in list order |
| `/api/ReadingList/name-exists` | GET | Check name uniqueness |
| `/api/ReadingList/promote-multiple` | POST | Promote/unpromote lists |
| `/api/ReadingList` | DELETE | Delete a reading list |
| `/api/ReadingList/delete-multiple` | POST | Delete multiple lists |
| `/api/ReadingList/export-as-cbl` | GET | Export as CBL file |
| `/api/ReadingList/info` | GET | Random info about list |

### OPDS Endpoints
| `/api/Opds/{apiKey}/reading-list` | GET | Browse reading lists via OPDS |
| `/api/Opds/{apiKey}/reading-list/{id}` | GET | Browse items in list via OPDS |

### Implementation Approach

1. **Browse lists**: `GET /api/ReadingList/lists` → show with cover images and progress.

2. **List detail**: `GET /api/ReadingList/items?readingListId=X` → show ordered chapter list with read/unread status and progress % per item.

3. **Read in order**: Tap "Start Reading" → open first unread item. At end of chapter, use `GET /api/ReadingList/next-chapter` for next-in-list navigation (NOT series next-chapter).

4. **Create list**: Dialog → `POST /api/ReadingList/create` with `{ title, summary }`.

5. **Add items**: From series/volume/chapter context menus → "Add to Reading List" → `POST update-by-series` or `POST update-by-multiple`.

6. **Reorder**: Drag-and-drop → `POST update-position` for each moved item.

7. **Remove read items**: "Clean Up List" → `POST /api/ReadingList/remove-read`.

8. **OPDS route**: Add reading lists section to OPDS browser.

---

## 9. ANNOTATIONS

**Priority: P3 — Power-user feature, complex UI**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Annotation/all` | GET | All annotations for a chapter (via query) |
| `/api/Annotation/all-filtered` | GET | Filtered/browsable annotations |
| `/api/Annotation/all-for-series` | GET | All annotations for a series |
| `/api/Annotation/{annotationId}` | GET | Get single annotation |
| `/api/Annotation/create` | POST | Create annotation (AnnotationDto) |
| `/api/Annotation/update` | POST | Update (spoiler, highlight, comment) |
| `/api/Annotation/like` | POST | Like annotation(s) |
| `/api/Annotation/unlike` | POST | Unlike annotation(s) |
| `/api/Annotation` | DELETE | Delete annotation |
| `/api/Annotation/bulk-delete` | POST | Bulk delete |
| `/api/Annotation/export` | GET | Export user's annotations |
| `/api/Annotation/export-filter` | GET | Export filtered annotations |

### AnnotationDto Schema (key fields)
```json
{
  "id": int,
  "xPath": string,           // DOM path to highlighted element
  "endingXPath": string?,    // For multi-element selections
  "selectedText": string?,   // The highlighted text
  "comment": string?,        // User's comment (markdown)
  "commentHtml": string?,
  "commentPlainText": string?,
  "containsSpoiler": boolean,
  "pageNumber": int,
  "selectedSlotIndex": int,  // Highlight color slot
  "highlightCount": int,     // Number of highlights
  "likes": int[],            // User IDs who liked
  "chapterId": int,
  "volumeId": int,
  "seriesId": int,
  "libraryId": int,
  "ownerUserId": int,
  "createdUtc": datetime,
  "lastModifiedUtc": datetime
}
```

### Implementation Approach

1. **In-reader highlight**: User selects text → create `AnnotationDto` with `xPath`, `selectedText`, `pageNumber` → `POST /api/Annotation/create`. Store returned ID.

2. **Annotation popup**: Tap existing highlight → show comment, spoiler toggle, like count. Edit → `POST /api/Annotation/update`.

3. **Chapter annotations list**: Side panel in reader showing `GET /api/Annotation/all?chapterId=X`. Tap to jump to that page+highlight.

4. **Series annotations**: Series detail tab → `GET /api/Annotation/all-for-series?seriesId=X`.

5. **Social**: Like/unlike others' annotations (if visible) → `POST /api/Annotation/like`.

6. **Export**: "Export Annotations" → `GET /api/Annotation/export`.

7. **Implementation note**: `xPath` is DOM-based, so this is primarily useful for epub/HTML content. For image-based manga, annotations may be limited to page-level comments. Coordinate the `xPath` format with Kavita's web reader for compatibility.

---

## 10. SCROBBLING (External Service Sync)

**Priority: P3 — Requires Kavita+ subscription, niche audience**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Scrobbling/scrobble-settings` | GET | Get scrobble provider settings |
| `/api/Scrobbling/update-scrobble-settings` | POST | Update provider settings |
| `/api/Scrobbling/update-user-scrobble-provider` | POST | Update auth for a provider |
| `/api/Scrobbling/scrobble-events` | GET | View scrobble event history |
| `/api/Scrobbling/holds` | GET | Get scrobble holds |
| `/api/Scrobbling/has-hold` | GET | Check hold on series |
| `/api/Scrobbling/add-hold` | POST | Add scrobble hold |
| `/api/Scrobbling/remove-hold` | POST | Remove scrobble hold |
| `/api/Scrobbling/token-expired` | GET | Check if provider token expired |
| `/api/Scrobbling/expired-tokens` | GET | All expired tokens |
| `/api/Scrobbling/scrobble-errors` | GET | View errors |
| `/api/Scrobbling/clear-errors` | POST | Clear errors |
| `/api/Scrobbling/library-allows-scrobbling` | GET | Check library scrobble permission |
| `/api/Scrobbling/generate-scrobble-events` | POST | Generate events from history |
| `/api/Scrobbling/retry-scrobble` | POST | Retry failed events |

### Implementation Approach

1. **Settings view**: `GET /api/Scrobbling/scrobble-settings` → show connected providers (AniList, MAL, Hardcover, etc.) with status.

2. **Token management**: If `GET /api/Scrobbling/token-expired?providerId=X` → prompt re-auth via `POST update-user-scrobble-provider`.

3. **Holds management**: On series detail, show "Scrobble: Active/Hold" toggle → `POST add-hold/remove-hold`. Holds prevent scrobbling for a series (e.g., rereads you don't want tracked).

4. **Error monitoring**: `GET /api/Scrobbling/scrobble-errors` → show in admin/settings. `POST retry-scrobble` to retry.

5. **Note**: Scrobbling is automatic server-side when Kavita detects reading events. Mimiral just needs to manage settings, holds, and token auth. No need to POST scrobble events directly.

---

## 11. DEVICE SUPPORT (Send-to-Device)

**Priority: P3 — Email-based, requires server email config**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Device/create` | POST | Register email device (CreateEmailDeviceDto) |
| `/api/Device/update` | POST | Update device |
| `/api/Device` | DELETE | Delete device |
| `/api/Device/send-to` | POST | Send chapters to device |
| `/api/Device/send-series-to` | POST | Send entire series to device |
| `/api/Device/client/devices` | GET | Get user's devices |
| `/api/Device/client/all-devices` | GET | Get all devices (admin) |
| `/api/Device/client/device` | DELETE | Remove client device |
| `/api/Device/client/update-name` | POST | Update device friendly name |

### Implementation Approach

1. **Device registration**: On first setup or in settings, register this device as an email device → `POST /api/Device/create` with `{ name: "Mimiral (Pixel 8)", emailAddress: "user@kindle.com", deviceType }`.

2. **Send-to flow**: On chapter/volume/series context menu → "Send to Device" → picker showing `GET /api/Device/client/devices` → `POST /api/Device/send-to` with `{ chapterIds, deviceId }`.

3. **Series send**: `POST /api/Device/send-series-to` for whole series.

4. **Device management**: Settings screen to add/remove/rename devices.

---

## 12. STATS (Reading History & Analytics)

**Priority: P3 — Nice to have, not core reading**

### Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Stats/reading-activity` | GET | Reading events broken by day/format |
| `/api/Stats/reading-pace` | GET | Reading pace over time |
| `/api/Stats/genre-breakdown` | GET | Top genres by reading volume |
| `/api/Stats/tag-breakdown` | GET | Top tags |
| `/api/Stats/pages-per-year` | GET | Pages read per year |
| `/api/Stats/words-per-year` | GET | Words read per year |
| `/api/Stats/reading-history` | GET | User's reading session history |
| `/api/Stats/reading-history/series/{seriesId}` | GET | History for a specific series |
| `/api/Stats/user-stats` | GET | Aggregate user statistics |
| `/api/Stats/total-reads` | GET | Total reads in timeframe |
| `/api/Stats/reads-by-month` | GET | Reads by month |
| `/api/Stats/avg-time-by-hour` | GET | Average reading time by hour |
| `/api/Stats/favorite-authors` | GET | Favorite authors |
| `/api/Stats/day-breakdown` | GET | Per-day reading breakdown |
| `/api/Stats/page-spread` | GET | Page spread distribution |
| `/api/Stats/word-spread` | GET | Word spread distribution |
| `/api/Stats/popular-genres` | GET | Server-wide popular genres |
| `/api/Stats/popular-series` | GET | Server-wide popular series |
| `/api/Stats/most-active-users` | GET | Most active readers |

### Implementation Approach

1. **Personal stats screen**: New "Stats" tab or section showing:
   - Reading activity chart (`reading-activity`)
   - Genre pie chart (`genre-breakdown`)
   - Pages/year bar chart (`pages-per-year`)
   - Pace trend (`reading-pace`)
   - Favorite authors list (`favorite-authors`)

2. **Series-specific stats**: On series detail, `GET /api/Stats/reading-history/series/{seriesId}` → show reading sessions timeline.

3. **Dashboard widgets**: On Deck + recently-read + reading-pace mini-chart.

4. **Privacy**: Stats are per-user. Admin users can see `most-active-users` and server-wide stats.

---

## 13. OPDS ENHANCEMENTS

**Priority: P2 — Already working, easy wins for free**

### New OPDS Routes (already exist, just need to integrate into Mimiral's OPDS browser)

| Endpoint | Purpose |
|----------|---------|
| `/api/Opds/{apiKey}/collections` | Browse collections as OPDS feeds |
| `/api/Opds/{apiKey}/collections/{collectionId}` | Series within a collection |
| `/api/Opds/{apiKey}/reading-list` | Browse reading lists as OPDS feeds |
| `/api/Opds/{apiKey}/reading-list/{readingListId}` | Items within a reading list |
| `/api/Opds/{apiKey}/want-to-read` | Want to Read list as OPDS feed |
| `/api/Opds/{apiKey}/smart-filters` | User's smart filters |
| `/api/Opds/{apiKey}/smart-filters/{filterId}` | Smart filter results as OPDS feed |
| `/api/Opds/{apiKey}/on-deck` | On Deck as OPDS feed |
| `/api/Opds/{apiKey}/recently-added` | Recently added |
| `/api/Opds/{apiKey}/recently-updated` | Recently updated |

### Implementation Approach

1. **OPDS browser enhancement**: Add sidebar/nav items for Collections, Reading Lists, Want to Read, Smart Filters, On Deck, Recently Added/Updated — all browsed through the existing OPDS client.

2. **Smart filters**: `GET /api/Opds/{apiKey}/smart-filters` → list user's saved filters. Each one is a feed → `GET /api/Opds/{apiKey}/smart-filters/{filterId}`.

3. **No extra code path**: Since Mimiral already has an OPDS client, these are just additional OPDS feed URLs to expose in the navigation. Minimal effort.

---

## 14. KOREADER / PANELS SYNC (Alternative Progress Sync)

**Priority: P4 — For users coming from Koreader or Panels**

### Koreader Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Koreader/{apiKey}/users/auth` | GET | Authenticate Koreader user |
| `/api/Koreader/{apiKey}/syncs/progress` | PUT | Sync progress (KoreaderBookDto) |
| `/api/Koreader/{apiKey}/syncs/progress/{ebookHash}` | GET | Get progress by ebook hash |

### Panels Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/api/Panels/save-progress` | POST | Save progress (ProgressDto, uses apiKey query param) |
| `/api/Panels/get-progress` | GET | Get progress (chapterId + apiKey query params) |

### KoreaderBookDto Schema
```json
{
  "document": string,     // Hash/ID of the book
  "device_id": string,    // Device identifier
  "device": string,       // Device name
  "percentage": float,    // 0.0 - 1.0
  "progress": string,     // Opaque progress string (XPointer)
  "timestamp": int64      // Unix timestamp
}
```

### Implementation Approach

1. **Koreader compat mode**: In settings, offer "Koreader Sync Mode". When enabled, use PUT `/api/Koreader/{apiKey}/syncs/progress` with `KoreaderBookDto` format. The `percentage` field (0-1) maps to page/totalPages. The `document` is an MD5 hash of the file.

2. **Panels compat mode**: `/api/Panels/save-progress` and `get-progress` use the same `ProgressDto` as the Reader endpoints but authenticate via `apiKey` query parameter instead of JWT. Useful for API-key-only setups.

3. **Recommendation**: Default to Reader/progress (JWT). Offer Panels/progress as fallback when only API key is available. Koreader sync is niche — only enable if user explicitly requests it.

---

## 15. ADDITIONAL FEATURES (Future Consideration)

### Rating / Review
| `/api/Rating/series` | POST | Rate a series (1-5 stars) |
| `/api/Rating/chapter` | POST | Rate a chapter |
| `/api/Rating/overall-series` | GET | Community rating for series |
| `/api/Review/series` | POST | Write series review |
| `/api/Review/chapter` | POST | Write chapter review |
| `/api/Review/all` | GET | All user reviews |

### Personal Table of Contents (PTOC)
| `/api/Reader/ptoc` | GET | User's personal TOC for a chapter |
| `/api/Reader/create-ptoc` | POST | Create PTOC entry |
| DELETE `/api/Reader/ptoc` | DELETE | Delete PTOC |

### Reading Profiles (per-series reading settings)
| `/api/reading-profile/all` | GET | All reading profiles |
| `/api/reading-profile/create` | POST | Create reading profile |
| `/api/reading-profile/{libraryId}/{seriesId}` | GET | Effective profile for series |

### Reread Prompts
| `/api/Reader/prompt-reread/series` | GET | Should prompt reread for series? |
| `/api/Reader/prompt-reread/volume` | GET | Should prompt reread for volume? |
| `/api/Reader/prompt-reread/chapter` | GET | Should prompt reread for chapter? |

### Smart Filters
| `/api/Filter` | GET | All smart filters |
| `/api/Filter/update/series` | POST | Save series filter |
| `/api/Filter/encode/series` | POST | Encode a filter |

---

## Implementation Phases

### Phase 1: Foundation (Weeks 1-2)
- ✅ JWT auth flow (login, token refresh, API key derivation)
- ✅ Reading progress sync (save/get/continue-point)
- ✅ Mark read/unread (chapter, volume, series)
- ✅ Next/prev chapter navigation
- ✅ On Deck feed

### Phase 2: Core Library Management (Weeks 3-4)
- ✅ Bookmark sync (CRUD + image viewing)
- ✅ Want to Read (add/remove/browse)
- ✅ Collections (browse, create, add/remove series)
- ✅ Reading Lists (browse, create, add items, next-chapter navigation)
- ✅ OPDS enhancements (collections/lists/want-to-read feeds)

### Phase 3: Power User Features (Weeks 5-7)
- ✅ Annotations (create, view, edit, delete in reader)
- ✅ Rating & Review
- ✅ Personal TOC
- ✅ Reading Profiles (fetch effective profile per series)
- ✅ Reread prompts

### Phase 4: Ecosystem (Weeks 8-9)
- ✅ Stats dashboard
- ✅ Scrobbling management (holds, tokens)
- ✅ Device support (send-to)
- ✅ Koreader/Panels sync mode

---

## Architectural Notes

1. **API client design**: Create a unified `KavitaApiService` Retrofit interface. JWT interceptor auto-attaches `Authorization: Bearer <token>`. API key interceptor attaches `x-api-key` header for Plugin/OPDS endpoints.

2. **Offline support**: All write operations (progress, bookmarks, mark-read) must be queued in a Room `PendingOperation` table and flushed on reconnect. Include `lastModifiedUtc` for conflict resolution.

3. **Error handling**: Kavita returns standard HTTP status codes. 401 → refresh token. 403 → permission denied. 400 → bad request (show error). Rate limiting is not documented but should be respected.

4. **Pagination**: Most list endpoints use a standardized pagination format. Implement a generic `KavitaPagedSource` for Paging 3 integration.

5. **WebSocket/SignalR**: Kavita uses SignalR for real-time events (scrobble updates, scan progress, etc.). Consider adding a SignalR client for live updates in a future phase.

6. **Server version check**: `GET /api/Plugin/version` returns the Kavita version. Check on connect and disable features not available on older servers.

7. **Kavita+ features**: Scrobbling, external metadata, and some stats features require a Kavita+ subscription. Check `GET /api/License/valid-license` and gracefully hide/degrade features when no license.
