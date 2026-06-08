#!/usr/bin/env python3
import subprocess


def kc(args):
    cmd = ["hermes", "kanban", "--board", "mimiral"] + args
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
    out = r.stdout.strip()
    # Extract task ID from "Created t_xxxxx  (ready, ...)"
    if "Created" in out:
        return out.split("Created ")[1].split(" ")[0]
    return "FAIL: " + out


# Phase 1
p1_auth = kc([
    "create",
    "Kavita Auth: JWT login + API key + token lifecycle",
    "--body",
    "Implement dual-auth for Kavita: (1) JWT via POST /api/Account/login (username+password), (2) API key via GET /api/Plugin/authenticate (x-api-key header returns JWT). Token lifecycle: store JWT+refresh in EncryptedSharedPreferences, intercept 401 and call POST /api/Account/refresh-token then retry, auto-derive OPDS URL via GET /api/Account/opds-url. Data model: KavitaAuthState(serverUrl, jwtToken, refreshToken, apiKey, opdsUrl, userId, username, tokenExpiry, roles). Create KavitaAuthInterceptor for Retrofit. Prerequisite for ALL other Kavita features.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "1",
])
print(f"Auth: {p1_auth}")

p1_progress = kc([
    "create",
    "Kavita Reading Progress Sync",
    "--body",
    "Bidirectional reading progress sync. On chapter open: GET /api/Reader/get-progress, compare with local, use furthest. On page turn: debounced POST /api/Reader/progress every 3 pages or 10s with ProgressDto{volumeId,chapterId,pageNum,seriesId,libraryId,bookScrollId,lastModifiedUtc}. On reader close: immediate POST. On app start: full sync. Continue Reading: GET /api/Reader/continue-point. Conflict resolution: lastModifiedUtc wins. Offline: queue in Room PendingOperation table, flush on reconnect. EPUB scroll via bookScrollId.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "1",
])
print(f"Progress: {p1_progress}")

p1_markread = kc([
    "create",
    "Kavita Mark Read/Unread",
    "--body",
    "Mark chapters/volumes/series as read or unread. Chapter context menu: POST /api/Reader/mark-chapter-read. Volume: POST mark-volume-read/unread. Series: POST mark-read/mark-unread. Bulk: POST mark-multiple-series-read/unread. Catch-up: POST /api/Tachiyomi/mark-chapter-until-as-read. After marking refresh progress indicators locally. Wire into existing UI context menus.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "2",
])
print(f"MarkRead: {p1_markread}")

p1_continue = kc([
    "create",
    "Kavita Continue Reading + Next/Prev Chapter + On Deck",
    "--body",
    "Seamless reading flow. Continue button on series detail: GET /api/Reader/continue-point then deep link to reader. End-of-chapter: Next Chapter via GET /api/Reader/next-chapter. Start-of-chapter: Prev Chapter via GET /api/Reader/prev-chapter. On Deck home shelf: GET /api/Series/on-deck. Time estimates: GET /api/Reader/time-left on series detail.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "2",
])
print(f"Continue: {p1_continue}")

# Phase 2
p2_bookmarks = kc([
    "create",
    "Kavita Bookmark Sync",
    "--body",
    "Bidirectional bookmark sync. In-reader toggle: POST /api/Reader/bookmark or /unbookmark. Bookmark viewer: GET /api/Reader/all-bookmarks grouped by series/volume/chapter with thumbnails via GET /api/Image/bookmark. Series bookmarks: GET /api/Reader/series-bookmarks. Sync on app start: fetch all, merge (server source of truth), POST local changes immediately. Export: GET /api/Download/bookmarks.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "2",
])
print(f"Bookmarks: {p2_bookmarks}")

p2_wtr = kc([
    "create",
    "Kavita Want To Read",
    "--body",
    "Want To Read list. Toggle on series detail: POST /api/want-to-read/add-series or /remove-series. Dedicated tab: GET /api/want-to-read/v2 with filtering+pagination. OPDS route: /api/Opds/{apiKey}/want-to-read. Auto-cleanup: optionally POST /api/Server/cleanup-want-to-read.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "3",
])
print(f"WTR: {p2_wtr}")

p2_collections = kc([
    "create",
    "Kavita Collections",
    "--body",
    "Collection management. Browse: GET /api/Collection with covers. Detail: GET /api/Series/series-by-collection paginated. Create: POST /api/Collection/update-series with tagId=0. Add/remove series: POST update-series / update-for-series. Edit: POST /api/Collection/update. Delete: DELETE /api/Collection. OPDS: /api/Opds/{apiKey}/collections. New CollectionsScreen + add-to-collection picker.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "3",
])
print(f"Collections: {p2_collections}")

p2_readinglists = kc([
    "create",
    "Kavita Reading Lists",
    "--body",
    "Reading list management with ordered reading. Browse: GET /api/ReadingList/lists. Detail: GET /api/ReadingList/items with read/unread+progress. Read in order: use GET /api/ReadingList/next-chapter at chapter end (NOT series next-chapter). Create: POST /api/ReadingList/create. Add items: POST update-by-series or update-by-multiple. Reorder: POST update-position. Clean: POST remove-read. OPDS: /api/Opds/{apiKey}/reading-list.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "3",
])
print(f"ReadingLists: {p2_readinglists}")

p2_opds = kc([
    "create",
    "Kavita OPDS Enhancements",
    "--body",
    "Expand OPDS browser with new feed categories. Add nav for: Collections, Reading Lists, Want To Read, Smart Filters, On Deck, Recently Added/Updated - all via /api/Opds/{apiKey}/ endpoints. Since OPDS client already exists, these are just additional feed URLs in Discover screen sidebar navigation. Mostly UI changes.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "3",
])
print(f"OPDS: {p2_opds}")

# Phase 3
p3_annotations = kc([
    "create",
    "Kavita Annotations (highlights+comments)",
    "--body",
    "Annotation system in reader. Text selection: POST /api/Annotation/create with AnnotationDto{xPath, selectedText, pageNumber, chapterId}. Tap highlight: popup with comment/spoiler/likes: POST /api/Annotation/update. Chapter panel: GET /api/Annotation/all?chapterId=X. Series: GET /api/Annotation/all-for-series. Like/unlike: POST like/unlike. Export: GET /api/Annotation/export. xPath is DOM-based (best for EPUB). New AnnotationOverlay + AnnotationPanel.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "4",
])
print(f"Annotations: {p3_annotations}")

p3_rating = kc([
    "create",
    "Kavita Rating + Review",
    "--body",
    "Rating and review. Rate series: POST /api/Rating/series (1-5 stars). Rate chapter: POST /api/Rating/chapter. Community rating: GET /api/Rating/overall-series. Write review: POST /api/Review/series or /chapter. All reviews: GET /api/Review/all. Star rating widget on series detail. Review text field. Show community rating alongside user rating.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "4",
])
print(f"Rating: {p3_rating}")

# Phase 4
p4_stats = kc([
    "create",
    "Kavita Stats Dashboard",
    "--body",
    "Reading statistics screen. GET /api/Stats/reading-activity for activity chart. genre-breakdown for pie chart. pages-per-year for bar chart. reading-pace for trend. favorite-authors for list. Series-specific: reading-history/series/{seriesId}. Dashboard widgets: On Deck + recently-read + pace mini-chart. New StatsScreen with Compose Canvas charts.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "5",
])
print(f"Stats: {p4_stats}")

p4_scrobble = kc([
    "create",
    "Kavita Scrobbling Management",
    "--body",
    "Scrobble settings/holds (Kavita+ only). Settings: GET /api/Scrobbling/scrobble-settings. Token management: if expired, re-auth via POST update-user-scrobble-provider. Holds toggle on series: POST add-hold/remove-hold. Errors: GET scrobble-errors, POST retry-scrobble. Check Kavita+ license first via GET /api/License/valid-license. Scrobbling is server-side automatic; Mimiral just manages settings/holds/tokens.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "5",
])
print(f"Scrobble: {p4_scrobble}")

p4_device = kc([
    "create",
    "Kavita Device Support (Send-to-Device)",
    "--body",
    "Send chapters to email devices (Kindle etc). Register: POST /api/Device/create. Send: context menu picker showing GET /api/Device/client/devices then POST /api/Device/send-to. Send series: POST /api/Device/send-series-to. Device management in settings. Requires server email config.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "5",
])
print(f"Device: {p4_device}")

p4_alt_sync = kc([
    "create",
    "Kavita Koreader/Panels Alternative Progress Sync",
    "--body",
    "Alternative progress sync modes. Koreader: PUT /api/Koreader/{apiKey}/syncs/progress with KoreaderBookDto{document(hash), device_id, percentage, progress, timestamp}. Panels: POST /api/Panels/save-progress (apiKey query param, no JWT). Settings toggle: Native (Reader/progress, default), Panels (API-key fallback), Koreader (KOReader interop). Default Native; offer Panels when only API key available.",
    "--assignee",
    "frontend-eng",
    "--priority",
    "6",
])
print(f"AltSync: {p4_alt_sync}")

# Now wire dependencies
print("\n--- Wiring dependencies ---")


def link(child, parent):
    r = kc(["link", child, parent])
    print(f"  {child} -> {parent}")


# Everything depends on Auth
for tid in [
    p1_progress,
    p1_markread,
    p1_continue,
    p2_bookmarks,
    p2_wtr,
    p2_collections,
    p2_readinglists,
    p2_opds,
    p3_annotations,
    p3_rating,
    p4_stats,
    p4_scrobble,
    p4_device,
    p4_alt_sync,
]:
    link(tid, p1_auth)

# Continue chapter depends on progress
link(p1_continue, p1_progress)

# Mark read depends on auth (already linked above)

# OPDS enhancements depend on collections + readinglists + WTR (they expose those feeds)
link(p2_opds, p2_collections)
link(p2_opds, p2_readinglists)
link(p2_opds, p2_wtr)

# Scrobble depends on Kavita+ license check (low priority, no extra link needed)

print("\nDone! All Kavita integration tasks created with dependencies.")
