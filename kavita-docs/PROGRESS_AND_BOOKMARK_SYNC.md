# Mimiral × Kavita: Progress & Bookmark Sync — Detailed Implementation Plan

> Version: 1.0  
> Date: 2026-06-07  
> Based on: Kavita API v0.9.0.6, Mimiral Room DB, Retrofit  
> Parent doc: `KAVITA_INTEGRATION_PLAN.md`

---

## Table of Contents

1. [Sync Protocol Recommendation](#1-sync-protocol-recommendation)
2. [Auth Flow — JWT from API Key](#2-auth-flow--jwt-from-api-key)
3. [Progress Sync Flow](#3-progress-sync-flow)
4. [Bookmark Sync Flow](#4-bookmark-sync-flow)
5. [Mark Read/Unread Sync](#5-mark-readunread-sync)
6. [Room Data Models](#6-room-data-models)
7. [Retrofit Service Interfaces](#7-retrofit-service-interfaces)
8. [Offline Queue & Sync Worker](#8-offline-queue--sync-worker)
9. [Conflict Resolution Strategy](#9-conflict-resolution-strategy)
10. [Implementation Checklist](#10-implementation-checklist)

---

## 1. Sync Protocol Recommendation

### Options Considered

| Protocol | Endpoints | Auth | Data Format | Pros | Cons |
|----------|-----------|------|-------------|------|------|
| **Reader/progress** (native) | `POST /api/Reader/progress`, `GET /api/Reader/get-progress` | JWT | `ProgressDto` (chapterId, pageNum, bookScrollId, lastModifiedUtc) | Full Kavita native format; supports `bookScrollId` for epub scroll; `lastModifiedUtc` for conflict resolution; integrates with continue-point/next-chapter; supports all media types | Requires JWT |
| **Panels/progress** | `POST /api/Panels/save-progress`, `GET /api/Panels/get-progress` | API key (query param) | Same `ProgressDto` | No JWT needed — works with API key alone | Undocumented fields; may lack `lastModifiedUtc`; secondary/support endpoint; fewer companion endpoints (no continue-point etc.) |
| **Koreader/progress** | `PUT /api/Koreader/{apiKey}/syncs/progress`, `GET /api/Koreader/{apiKey}/syncs/progress/{ebookHash}` | API key (path) | `KoreaderBookDto` (document hash, percentage 0–1, XPointer, device_id) | Works from e-ink devices; API key only; percentage-based | Requires MD5 hash of file as document ID; mapping to chapterId is fragile; no `bookScrollId`; no companion endpoints; XPointer is opaque; niche use case |

### ✅ Recommendation: **Reader/progress (native) as primary, Panels/progress as fallback**

**Rationale:**

1. **Reader/progress is the canonical API** — it is what Kavita's own web reader uses. It has full `ProgressDto` fidelity including `lastModifiedUtc` for conflict resolution and `bookScrollId` for epub scroll position.
2. **It integrates with the entire Reader ecosystem** — `continue-point`, `next-chapter`, `prev-chapter`, `has-progress`, and all bookmark/mark-read endpoints are part of the same controller and share the same JWT auth.
3. **Panels/progress is useful as a degraded fallback** — if JWT acquisition fails (e.g. server doesn't support `Plugin/authenticate`), we can still sync basic progress via API key. However, it likely doesn't carry `lastModifiedUtc`, so conflict resolution is weaker.
4. **Koreader/progress should NOT be used as primary** — the ebook-hash-based addressing is fragile (requires matching file hashes), the percentage model loses page precision, and there are no companion endpoints for bookmarks or mark-read.

**Implementation strategy:**
```kotlin
enum class ProgressSyncProtocol {
    NATIVE_READER,   // Default — JWT + Reader/progress
    PANELS_FALLBACK, // API-key only — Panels/save-progress
}
// Selected at runtime: NATIVE_READER if JWT available, else PANELS_FALLBACK
```

---

## 2. Auth Flow — JWT from API Key

### Problem

Mimiral currently authenticates via **API key** (for OPDS feeds). The native Reader endpoints require **JWT Bearer auth**. We need a way to obtain a JWT when the user has only configured an API key.

### Solution: Two-Path JWT Acquisition

```
┌─────────────────────────────────────────────────────────────┐
│                  User Has API Key Only                       │
│                                                              │
│  apiKey ──→ GET /api/Plugin/authenticate (x-api-key header) │
│              ↓                                               │
│         Returns: UserDto + JWT (in response header/body)     │
│              ↓                                               │
│         Store JWT in EncryptedSharedPreferences              │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│            User Has Username + Password                      │
│                                                              │
│  credentials ──→ POST /api/Account/login                    │
│                    { username, password }                    │
│              ↓                                               │
│         Returns: UserDto + JWT + RefreshToken                │
│              ↓                                               │
│         Store JWT + RefreshToken in EncryptedSharedPreferences│
└─────────────────────────────────────────────────────────────┘
```

### Step-by-step Flow

1. **On server setup**, user provides either:
   - **API key** (pasted from Kavita OPDS URL), OR
   - **Username + password** (new login flow)

2. **If API key provided:**
   - Call `GET /api/Plugin/authenticate` with `x-api-key: <apiKey>` header
   - Response includes a JWT token (in the `Authorization` response header or as a field in the response body, depending on Kavita version)
   - Parse and store the JWT
   - **Limitation:** Plugin/authenticate may not return a refresh token. In this case, the JWT will expire (default: ~24h in Kavita). When it expires, re-call `Plugin/authenticate` with the same API key to get a fresh JWT.

3. **If username + password provided:**
   - Call `POST /api/Account/login` with `LoginDto { username, password }`
   - Response returns JWT + refresh token
   - Store both

4. **JWT Refresh Strategy:**
   ```kotlin
   class KavitaAuthInterceptor : Interceptor {
       override fun intercept(chain: Chain): Response {
           val request = chain.request().newBuilder()
               .addHeader("Authorization", "Bearer ${authState.jwtToken}")
               .build()
           val response = chain.proceed(request)
           
           if (response.code == 401) {
               // Attempt refresh
               val newJwt = refreshTokenOrReauth()
               if (newJwt != null) {
                   val retryRequest = request.newBuilder()
                       .header("Authorization", "Bearer $newJwt")
                       .build()
                   return chain.proceed(retryRequest)
               }
               // Refresh failed — trigger re-login UI
               authStateListener.onAuthExpired()
           }
           return response
       }
       
       private fun refreshTokenOrReauth(): String? {
           return if (authState.refreshToken != null) {
               // Try JWT refresh
               accountApi.refreshToken(authState.refreshToken)
           } else if (authState.apiKey != null) {
               // Re-authenticate with API key
               pluginApi.authenticate(authState.apiKey)
           } else null
       }
   }
   ```

5. **Secure Storage:**
   - JWT, refresh token, and API key stored in `EncryptedSharedPreferences` (AndroidX Security)
   - Never persist in plain Room tables
   - `tokenExpiry` tracked to proactively refresh before expiry (e.g., refresh when <5 min remaining)

### Data Model for Auth State

```kotlin
// Stored in EncryptedSharedPreferences, NOT in Room
data class KavitaAuthState(
    val serverUrl: String,
    val jwtToken: String?,
    val refreshToken: String?,
    val apiKey: String?,
    val userId: Int,
    val username: String,
    val tokenExpiryEpochMillis: Long,  // For proactive refresh
    val authMethod: AuthMethod          // USERNAME_PASSWORD or API_KEY
)

enum class AuthMethod {
    USERNAME_PASSWORD,  // Has refresh token
    API_KEY,            // Must re-call Plugin/authenticate on expiry
}
```

---

## 3. Progress Sync Flow

### 3.1 When to Save Progress (Upload to Server)

| Trigger | Behavior | Priority |
|---------|----------|----------|
| **Page turn** (every N pages) | Debounced batch send — every 3 pages or 10 seconds, whichever comes first | Low (debounced) |
| **Reader pause/background** | Immediate send of current progress | High |
| **Reader close/destroy** | Immediate send of final progress | Critical — must succeed or queue |
| **Chapter complete** | Immediate send (pageNum = totalPages) + trigger `mark-chapter-read` | Critical |
| **App lifecycle: onStop** | Immediate send if reader is open | High |

### Debounce Strategy

```kotlin
class ProgressSyncManager(
    private val syncWorker: SyncWorker,
    private val pendingOpsDao: PendingOperationDao,
    private val isOnline: () -> Boolean
) {
    private val debounceJob = Job()
    private var pagesSinceLastSync = 0
    private var lastSyncInstant = Clock.System.now()
    
    companion object {
        const val DEBOUNCE_PAGE_THRESHOLD = 3
        val DEBOUNCE_TIME_THRESHOLD = 10.seconds
    }
    
    fun onPageChanged(chapterId: Int, pageNum: Int, totalPages: Int, progress: ProgressDto) {
        pagesSinceLastSync++
        val timeSinceLastSync = Clock.System.now() - lastSyncInstant
        
        if (pagesSinceLastSync >= DEBOUNCE_PAGE_THRESHOLD || 
            timeSinceLastSync >= DEBOUNCE_TIME_THRESHOLD) {
            sendProgress(progress)
            pagesSinceLastSync = 0
            lastSyncInstant = Clock.System.now()
        } else {
            // Ensure a delayed send is scheduled
            scheduleDebouncedSend(progress)
        }
    }
    
    fun onReaderClose(progress: ProgressDto) {
        cancelDebouncedSend()
        sendProgress(progress) // Immediate
    }
    
    private fun sendProgress(progress: ProgressDto) {
        if (isOnline()) {
            syncWorker.enqueueProgressUpload(progress)
        } else {
            pendingOpsDao.insert(PendingOperation.fromProgress(progress))
        }
    }
}
```

### 3.2 When to Fetch Progress (Download from Server)

| Trigger | Behavior |
|---------|----------|
| **Opening a chapter in reader** | Fetch server progress → compare with local → resolve conflict → show resolved page |
| **App start / server reconnect** | Fetch progress for all "recently read" chapters (last 7 days) |
| **Periodic sync** | WorkManager periodic sync every 15 minutes (configurable) for active reads |
| **Pull-to-refresh on library** | Fetch continue-points for visible series |

### 3.3 Periodic Sync vs On-Change Sync

**Use both, with different responsibilities:**

| Sync Type | Mechanism | Scope | Frequency |
|-----------|-----------|-------|-----------|
| **On-change (push)** | Immediate + debounced | Current chapter being read | Every 3 pages / 10 sec |
| **On-change (pull)** | On chapter open | Single chapter | Each open |
| **Periodic (pull)** | WorkManager `PeriodicWorkRequest` | Recently read chapters + continue-points | Every 15 min (min allowed by WorkManager) |
| **Periodic (push)** | Flush offline queue | Pending operations | On connectivity restore + periodic |
| **Full sync** | WorkManager `OneTimeWorkRequest` | All local progress with `lastModifiedUtc` newer than server | On app start, once per session |

```kotlin
// WorkManager setup
val periodicSyncWork = PeriodicWorkRequestBuilder<ProgressSyncWorker>(
    repeatInterval = 15, 
    repeatIntervalTimeUnit = TimeUnit.MINUTES
)
    .setConstraints(Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build())
    .build()

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(
        "kavita_progress_sync",
        ExistingPeriodicWorkPolicy.KEEP,
        periodicSyncWork
    )
```

### 3.4 Full Sync Flow (On App Start / Reconnect)

```
App Start
  │
  ├─ 1. Auth: Ensure JWT is valid (refresh if needed)
  │
  ├─ 2. Flush Pending Operations queue
  │     ├─ For each pending op (ordered by createdAt):
  │     │   ├─ If PROGRESS_UPLOAD: POST /api/Reader/progress
  │     │   ├─ If BOOKMARK_ADD: POST /api/Reader/bookmark
  │     │   ├─ If BOOKMARK_REMOVE: POST /api/Reader/unbookmark
  │     │   └─ If MARK_READ: POST /api/Reader/mark-chapter-read
  │     │   
  │     └─ On success: delete from queue
  │        On 409/412 conflict: resolve, then delete
  │
  ├─ 3. Pull server progress for recently-read chapters
  │     ├─ For each local ReadingProgress where lastModifiedUtc > lastSyncUtc:
  │     │   GET /api/Reader/get-progress?chapterId=X
  │     │   → compare lastModifiedUtc
  │     │   → apply conflict resolution (see §9)
  │     │
  │     └─ Update local DB + UI
  │
  └─ 4. Update continue-points for visible series
        GET /api/Reader/continue-point?seriesId=X
        → cache in local DB
```

### 3.5 Offline Queueing

All mutating operations that fail due to network issues are persisted in a Room `pending_operations` table and flushed in order when connectivity is restored.

See [§8 Offline Queue & Sync Worker](#8-offline-queue--sync-worker) for full details.

---

## 4. Bookmark Sync Flow

### 4.1 Model

Bookmarks in Kavita are per-user, per-chapter, per-page. They represent "saved pages" (image bookmarks). There is no `lastModifiedUtc` on bookmarks — the API doesn't provide timestamps for individual bookmarks.

### 4.2 Sync Strategy: Server-Authoritative with Local Cache

Since bookmarks lack timestamps for incremental sync, we use a **server-authoritative** model:

- **Server is source of truth** for the complete set of bookmarks for any chapter/series.
- **Local DB is a cache** that can be rebuilt from the server at any time.
- **Local mutations** (add/remove) are applied optimistically to local DB + sent to server immediately (or queued if offline).

### 4.3 On-Change Sync (Per-Chapter)

```
User toggles bookmark on page P of chapter C:

1. Optimistic update: Toggle bookmark in local DB immediately
2. If online:
   - If bookmarking:   POST /api/Reader/bookmark { chapterId: C, page: P }
   - If unbookmarking: POST /api/Reader/unbookmark { chapterId: C, page: P }
   - On success: Mark local bookmark as synced (syncState = SYNCED)
   - On failure: Queue in PendingOperation, keep local as PENDING_SYNC
3. If offline:
   - Queue in PendingOperation immediately
   - Mark local bookmark as PENDING_SYNC
```

### 4.4 Full Bookmark Sync (On App Start / Periodic)

```
Bookmark full sync (every 30 min or on app start):

1. GET /api/Reader/all-bookmarks  
   → Returns all bookmarks for the user (list of { chapterId, page, seriesId, volumeId })

2. Compare with local cache:
   - Server bookmarks NOT in local → INSERT into local DB (syncState = SYNCED)
   - Local bookmarks NOT on server AND syncState == SYNCED → DELETE from local 
     (server removed them elsewhere)
   - Local bookmarks NOT on server AND syncState == PENDING_SYNC → 
     KEEP (they were created offline and haven't been uploaded yet)

3. Flush PendingOperation queue:
   - For each pending bookmark add: POST /api/Reader/bookmark
   - For each pending bookmark remove: POST /api/Reader/unbookmark
   
4. After flush: re-fetch all-bookmarks to reconcile
```

### 4.5 Chapter-Scoped Bookmark Fetch

When opening a chapter in the reader:

```
1. GET /api/Reader/chapter-bookmarks?chapterId=C
   → Get fresh bookmark set for this chapter
2. Replace local cache for this chapter
3. Use to render bookmark indicators on page thumbnails / seekbar
```

### 4.6 Bookmark Image Retrieval

Bookmarks can display thumbnail images:

```
GET /api/Image/bookmark?chapterId=C&page=P
  → Returns the bookmarked page image (for display in bookmark gallery)
```

Cache these images in a disk LRU cache (e.g., 50MB limit) keyed by `{chapterId}_{page}`.

---

## 5. Mark Read/Unread Sync

### 5.1 Endpoints Used

| Action | Endpoint | Body |
|--------|----------|------|
| Mark chapter read | `POST /api/Reader/mark-chapter-read` | `{ chapterId, seriesId, volumeId }` |
| Mark series read | `POST /api/Reader/mark-read` | `{ seriesId }` |
| Mark series unread | `POST /api/Reader/mark-unread` | `{ seriesId }` |
| Mark volume read | `POST /api/Reader/mark-volume-read` | `{ volumeId, seriesId }` |
| Mark volume unread | `POST /api/Reader/mark-volume-unread` | `{ volumeId, seriesId }` |
| Mark multiple volumes read | `POST /api/Reader/mark-multiple-read` | `{ seriesId, volumeIds }` |
| Mark multiple volumes unread | `POST /api/Reader/mark-multiple-unread` | `{ seriesId, volumeIds }` |

### 5.2 Sync Strategy

Mark read/unread is **server-authoritative** — the read state lives on the server and Mimiral caches it.

```
User marks chapter as read:

1. POST /api/Reader/mark-chapter-read { chapterId, seriesId, volumeId }
2. On success:
   - Update local chapter read state (pagesRead = totalPages, isRead = true)
   - Update local series progress percentage
3. On failure:
   - Queue in PendingOperation
   - Optimistically update local state anyway (mark as PENDING_SYNC)

User marks series as read:

1. POST /api/Reader/mark-read { seriesId }
2. On success:
   - Bulk-update all chapters/volumes for this series in local DB as read
3. Refresh UI

Pull read state (when browsing library):

- Chapter/Volume read state is already embedded in ChapterDto/VolumeDto 
  from the OPDS or REST API (pagesRead, totalPages, isRead fields)
- No separate "fetch read state" endpoint needed — it comes with the metadata
```

### 5.3 Interaction with Progress Sync

When page progress reaches the last page of a chapter:

```
1. POST /api/Reader/progress { chapterId, pageNum: totalPages, ... }
2. POST /api/Reader/mark-chapter-read { chapterId, seriesId, volumeId }
3. Update local state
4. Check: GET /api/Reader/next-chapter → offer "Next Chapter →" in UI
```

---

## 6. Room Data Models

### 6.1 ReadingProgress Entity

```kotlin
@Entity(
    tableName = "reading_progress",
    indices = [
        Index(value = ["chapter_id"], unique = true),
        Index(value = ["series_id"]),
        Index(value = ["last_modified_utc"])
    ]
)
data class ReadingProgressEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,

    @ColumnInfo(name = "volume_id")
    val volumeId: Int,

    @ColumnInfo(name = "series_id")
    val seriesId: Int,

    @ColumnInfo(name = "library_id")
    val libraryId: Int,

    @ColumnInfo(name = "page_num")
    val pageNum: Int,

    @ColumnInfo(name = "total_pages")      // For local display; not in ProgressDto
    val totalPages: Int,

    @ColumnInfo(name = "book_scroll_id")   // Epub scroll position
    val bookScrollId: String? = null,

    @ColumnInfo(name = "last_modified_utc")
    val lastModifiedUtc: Long,             // Epoch millis (UTC)

    @ColumnInfo(name = "last_sync_utc")    // When last successfully synced to server
    val lastSyncUtc: Long? = null,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.SYNCED,

    @ColumnInfo(name = "server_id")        // Multi-server support
    val serverId: Long                     // FK to server config
)
```

### 6.2 Bookmark Entity

```kotlin
@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["chapter_id", "page"]),
        Index(value = ["series_id"]),
        Index(value = ["sync_state"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = ReadingProgressEntity::class,
            parentColumns = ["chapter_id"],
            childColumns = ["chapter_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,

    @ColumnInfo(name = "volume_id")
    val volumeId: Int,

    @ColumnInfo(name = "series_id")
    val seriesId: Int,

    @ColumnInfo(name = "page")
    val page: Int,

    @ColumnInfo(name = "sync_state")
    val syncState: SyncState = SyncState.SYNCED,

    @ColumnInfo(name = "created_at_utc")
    val createdAtUtc: Long,               // Epoch millis

    @ColumnInfo(name = "server_id")
    val serverId: Long
)
```

### 6.3 PendingOperation Entity (Offline Queue)

```kotlin
@Entity(
    tableName = "pending_operations",
    indices = [
        Index(value = ["created_at_utc"]),
        Index(value = ["operation_type", "target_id"])
    ]
)
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "operation_type")
    val operationType: PendingOpType,

    @ColumnInfo(name = "target_id")        // chapterId or composite key
    val targetId: String,                  // e.g. "chapter:123" or "bookmark:123:5"

    @ColumnInfo(name = "payload_json")     // Serialized request body
    val payloadJson: String,               // Moshi/Kotlinx JSON of the DTO

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "max_retries")
    val maxRetries: Int = 5,

    @ColumnInfo(name = "created_at_utc")
    val createdAtUtc: Long,

    @ColumnInfo(name = "server_id")
    val serverId: Long
)

enum class PendingOpType {
    PROGRESS_UPLOAD,
    BOOKMARK_ADD,
    BOOKMARK_REMOVE,
    MARK_CHAPTER_READ,
    MARK_SERIES_READ,
    MARK_SERIES_UNREAD,
    MARK_VOLUME_READ,
    MARK_VOLUME_UNREAD,
}
```

### 6.4 SyncState Enum

```kotlin
enum class SyncState {
    SYNCED,          // In sync with server
    PENDING_SYNC,    // Local change not yet pushed to server
    SYNC_FAILED,     // Push attempted but failed (will retry)
}
```

### 6.5 ContinuePoint Cache Entity

```kotlin
@Entity(
    tableName = "continue_points",
    indices = [Index(value = ["series_id"], unique = true)]
)
data class ContinuePointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "series_id")
    val seriesId: Int,

    @ColumnInfo(name = "chapter_id")
    val chapterId: Int,

    @ColumnInfo(name = "volume_id")
    val volumeId: Int,

    @ColumnInfo(name = "page_num")
    val pageNum: Int,

    @ColumnInfo(name = "fetched_at_utc")
    val fetchedAtUtc: Long,

    @ColumnInfo(name = "server_id")
    val serverId: Long
)
```

### 6.6 DAOs

```kotlin
@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE chapter_id = :chapterId AND server_id = :serverId LIMIT 1")
    suspend fun getByChapter(chapterId: Int, serverId: Long): ReadingProgressEntity?
    
    @Query("SELECT * FROM reading_progress WHERE series_id = :seriesId AND server_id = :serverId")
    suspend fun getBySeries(seriesId: Int, serverId: Long): List<ReadingProgressEntity>
    
    @Query("SELECT * FROM reading_progress WHERE last_modified_utc > :sinceEpoch AND server_id = :serverId ORDER BY last_modified_utc DESC")
    suspend fun getModifiedSince(sinceEpoch: Long, serverId: Long): List<ReadingProgressEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)
    
    @Query("DELETE FROM reading_progress WHERE chapter_id = :chapterId AND server_id = :serverId")
    suspend fun deleteByChapter(chapterId: Int, serverId: Long)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE chapter_id = :chapterId AND server_id = :serverId ORDER BY page ASC")
    suspend fun getByChapter(chapterId: Int, serverId: Long): List<BookmarkEntity>
    
    @Query("SELECT * FROM bookmarks WHERE series_id = :seriesId AND server_id = :serverId ORDER BY chapter_id, page")
    suspend fun getBySeries(seriesId: Int, serverId: Long): List<BookmarkEntity>
    
    @Query("SELECT * FROM bookmarks WHERE sync_state = :syncState AND server_id = :serverId")
    suspend fun getBySyncState(syncState: SyncState, serverId: Long): List<BookmarkEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)
    
    @Query("DELETE FROM bookmarks WHERE chapter_id = :chapterId AND page = :page AND server_id = :serverId")
    suspend fun delete(chapterId: Int, page: Int, serverId: Long)
    
    @Query("DELETE FROM bookmarks WHERE chapter_id = :chapterId AND server_id = :serverId AND sync_state = 'SYNCED' AND page NOT IN (:serverPages)")
    suspend fun pruneNotOnServer(chapterId: Int, serverPages: List<Int>, serverId: Long)
}

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations WHERE server_id = :serverId ORDER BY created_at_utc ASC")
    suspend fun getAllPending(serverId: Long): List<PendingOperationEntity>
    
    @Query("SELECT COUNT(*) FROM pending_operations WHERE server_id = :serverId")
    suspend fun pendingCount(serverId: Long): Int
    
    @Insert
    suspend fun insert(operation: PendingOperationEntity): Long
    
    @Delete
    suspend fun delete(operation: PendingOperationEntity)
    
    @Query("UPDATE pending_operations SET retry_count = retry_count + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)
}
```

---

## 7. Retrofit Service Interfaces

### 7.1 KavitaReaderService (JWT Auth — Native)

```kotlin
interface KavitaReaderService {

    // ─── Progress ──────────────────────────────────────────────

    @POST("api/Reader/progress")
    suspend fun saveProgress(
        @Body progress: ProgressDto
    ): Response<Unit>

    @GET("api/Reader/get-progress")
    suspend fun getProgress(
        @Query("chapterId") chapterId: Int
    ): Response<ProgressDto>

    @GET("api/Reader/continue-point")
    suspend fun getContinuePoint(
        @Query("seriesId") seriesId: Int
    ): Response<ChapterDto>

    @GET("api/Reader/has-progress")
    suspend fun hasProgress(
        @Query("seriesId") seriesId: Int
    ): Response<Boolean>

    // ─── Bookmarks ─────────────────────────────────────────────

    @POST("api/Reader/bookmark")
    suspend fun bookmark(
        @Body request: BookmarkRequestDto
    ): Response<Unit>

    @POST("api/Reader/unbookmark")
    suspend fun unbookmark(
        @Body request: BookmarkRequestDto
    ): Response<Unit>

    @GET("api/Reader/chapter-bookmarks")
    suspend fun getChapterBookmarks(
        @Query("chapterId") chapterId: Int
    ): Response<List<BookmarkDto>>

    @GET("api/Reader/volume-bookmarks")
    suspend fun getVolumeBookmarks(
        @Query("volumeId") volumeId: Int
    ): Response<List<BookmarkDto>>

    @GET("api/Reader/series-bookmarks")
    suspend fun getSeriesBookmarks(
        @Query("seriesId") seriesId: Int
    ): Response<List<BookmarkDto>>

    @POST("api/Reader/all-bookmarks")
    suspend fun getAllBookmarks(): Response<List<BookmarkDto>>

    // ─── Mark Read / Unread ────────────────────────────────────

    @POST("api/Reader/mark-chapter-read")
    suspend fun markChapterRead(
        @Body request: MarkChapterReadDto
    ): Response<Unit>

    @POST("api/Reader/mark-read")
    suspend fun markSeriesRead(
        @Body request: MarkSeriesDto
    ): Response<Unit>

    @POST("api/Reader/mark-unread")
    suspend fun markSeriesUnread(
        @Body request: MarkSeriesDto
    ): Response<Unit>

    @POST("api/Reader/mark-volume-read")
    suspend fun markVolumeRead(
        @Body request: MarkVolumeDto
    ): Response<Unit>

    @POST("api/Reader/mark-volume-unread")
    suspend fun markVolumeUnread(
        @Body request: MarkVolumeDto
    ): Response<Unit>

    // ─── Navigation ────────────────────────────────────────────

    @GET("api/Reader/next-chapter")
    suspend fun getNextChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("chapterId") chapterId: Int
    ): Response<Int>  // Returns chapterId

    @GET("api/Reader/prev-chapter")
    suspend fun getPrevChapter(
        @Query("seriesId") seriesId: Int,
        @Query("volumeId") volumeId: Int,
        @Query("chapterId") chapterId: Int
    ): Response<Int>

    // ─── Bookmark Images ───────────────────────────────────────

    @GET("api/Image/bookmark")
    @Streaming
    suspend fun getBookmarkImage(
        @Query("chapterId") chapterId: Int,
        @Query("page") page: Int
    ): ResponseBody
}
```

### 7.2 KavitaPanelsService (API Key Auth — Fallback)

```kotlin
interface KavitaPanelsService {

    @POST("api/Panels/save-progress")
    suspend fun saveProgress(
        @Query("apiKey") apiKey: String,
        @Body progress: ProgressDto
    ): Response<Unit>

    @GET("api/Panels/get-progress")
    suspend fun getProgress(
        @Query("chapterId") chapterId: Int,
        @Query("apiKey") apiKey: String
    ): Response<ProgressDto>
}
```

### 7.3 KavitaPluginService (API Key → JWT Auth)

```kotlin
interface KavitaPluginService {

    @GET("api/Plugin/authenticate")
    @Headers("x-api-key: placeholder") // Set dynamically via interceptor
    suspend fun authenticate(
        @Header("x-api-key") apiKey: String
    ): Response<UserDto>

    @GET("api/Plugin/version")
    suspend fun getVersion(): Response<String>
}
```

### 7.4 KavitaAccountService (JWT Login & Refresh)

```kotlin
interface KavitaAccountService {

    @POST("api/Account/login")
    suspend fun login(
        @Body loginDto: LoginDto
    ): Response<UserDto>  // JWT + refresh token in response headers

    @POST("api/Account/refresh-token")
    suspend fun refreshToken(
        @Body refreshToken: String
    ): Response<TokenResponseDto>

    @GET("api/Account/refresh-account")
    suspend fun refreshAccount(): Response<UserDto>
}
```

### 7.5 DTOs

```kotlin
@JsonClass(generateAdapter = true)
data class ProgressDto(
    @Json(name = "volumeId") val volumeId: Int,
    @Json(name = "chapterId") val chapterId: Int,
    @Json(name = "pageNum") val pageNum: Int,
    @Json(name = "seriesId") val seriesId: Int,
    @Json(name = "libraryId") val libraryId: Int,
    @Json(name = "bookScrollId") val bookScrollId: String? = null,
    @Json(name = "lastModifiedUtc") val lastModifiedUtc: String? = null  // ISO 8601
)

@JsonClass(generateAdapter = true)
data class BookmarkRequestDto(
    @Json(name = "chapterId") val chapterId: Int,
    @Json(name = "page") val page: Int
)

@JsonClass(generateAdapter = true)
data class BookmarkDto(
    @Json(name = "chapterId") val chapterId: Int,
    @Json(name = "page") val page: Int,
    @Json(name = "seriesId") val seriesId: Int? = null,
    @Json(name = "volumeId") val volumeId: Int? = null,
    @Json(name = "fileName") val fileName: String? = null
)

@JsonClass(generateAdapter = true)
data class MarkChapterReadDto(
    @Json(name = "chapterId") val chapterId: Int,
    @Json(name = "seriesId") val seriesId: Int,
    @Json(name = "volumeId") val volumeId: Int
)

@JsonClass(generateAdapter = true)
data class MarkSeriesDto(
    @Json(name = "seriesId") val seriesId: Int
)

@JsonClass(generateAdapter = true)
data class MarkVolumeDto(
    @Json(name = "volumeId") val volumeId: Int,
    @Json(name = "seriesId") val seriesId: Int
)

@JsonClass(generateAdapter = true)
data class LoginDto(
    @Json(name = "username") val username: String? = null,
    @Json(name = "password") val password: String? = null,
    @Json(name = "apiKey") val apiKey: String? = null
)

@JsonClass(generateAdapter = true)
data class TokenResponseDto(
    @Json(name = "token") val token: String,
    @Json(name = "refreshToken") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "id") val id: Int,
    @Json(name = "username") val username: String,
    @Json(name = "roles") val roles: List<String> = emptyList()
)
```

---

## 8. Offline Queue & Sync Worker

### 8.1 PendingOperation Lifecycle

```
User action (e.g., page turn)
  │
  ├─ Update local DB immediately (optimistic)
  │
  ├─ If online:
  │   ├─ Send to server
  │   ├─ On 2xx: Update syncState = SYNCED, lastSyncUtc = now
  │   └─ On failure: Insert into pending_operations
  │
  └─ If offline:
      └─ Insert into pending_operations immediately
```

### 8.2 Flush Queue on Reconnect

```kotlin
class PendingOperationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val serverId = inputData.getLong("server_id", -1)
        val pendingOps = pendingOpsDao.getAllPending(serverId)
        
        for (op in pendingOps) {
            if (op.retryCount >= op.maxRetries) {
                // Max retries exceeded — mark as permanently failed
                // Optionally notify user
                continue
            }
            
            try {
                when (op.operationType) {
                    PendingOpType.PROGRESS_UPLOAD -> {
                        val dto = moshi.adapter(ProgressDto::class.java)
                            .fromJson(op.payloadJson)!!
                        readerApi.saveProgress(dto)
                    }
                    PendingOpType.BOOKMARK_ADD -> {
                        val dto = moshi.adapter(BookmarkRequestDto::class.java)
                            .fromJson(op.payloadJson)!!
                        readerApi.bookmark(dto)
                    }
                    PendingOpType.BOOKMARK_REMOVE -> {
                        val dto = moshi.adapter(BookmarkRequestDto::class.java)
                            .fromJson(op.payloadJson)!!
                        readerApi.unbookmark(dto)
                    }
                    PendingOpType.MARK_CHAPTER_READ -> {
                        val dto = moshi.adapter(MarkChapterReadDto::class.java)
                            .fromJson(op.payloadJson)!!
                        readerApi.markChapterRead(dto)
                    }
                    // ... other types
                }
                // Success — remove from queue
                pendingOpsDao.delete(op)
            } catch (e: Exception) {
                // Increment retry count, keep in queue
                pendingOpsDao.incrementRetry(op.id)
            }
        }
        
        return Result.success()
    }
}
```

### 8.3 Connectivity Observer

```kotlin
class ConnectivityObserver(context: Context) : LifecycleObserver {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    
    val isOnline: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }
    
    // When transitioning offline→online, trigger flush:
    // pendingOperationWorker.enqueueOneTime()
}
```

### 8.4 Deduplication of Pending Operations

Before inserting a new pending op, check for an existing op with the same `operationType` + `targetId`:

```kotlin
suspend fun enqueueOrMerge(op: PendingOperationEntity) {
    val existing = pendingOpsDao.findByTypeAndTarget(op.operationType, op.targetId)
    if (existing != null) {
        when {
            // Newer progress supersedes older progress
            op.operationType == PendingOpType.PROGRESS_UPLOAD -> {
                pendingOpsDao.updatePayload(existing.id, op.payloadJson, op.createdAtUtc)
            }
            // Bookmark add + remove for same page cancel out
            op.operationType == PendingOpType.BOOKMARK_ADD && 
                existing.operationType == PendingOpType.BOOKMARK_REMOVE -> {
                pendingOpsDao.delete(existing) // Net neutral, remove both
                return
            }
            op.operationType == PendingOpType.BOOKMARK_REMOVE && 
                existing.operationType == PendingOpType.BOOKMARK_ADD -> {
                pendingOpsDao.delete(existing)
                return
            }
            else -> pendingOpsDao.insert(op)
        }
    } else {
        pendingOpsDao.insert(op)
    }
}
```

---

## 9. Conflict Resolution Strategy

### 9.1 Progress Conflicts

**Rule: `lastModifiedUtc` wins — newest timestamp takes precedence.**

```
On chapter open:
  local = localProgressDao.getByChapter(chapterId)
  server = readerApi.getProgress(chapterId)
  
  if (local == null) → use server
  if (server == null) → use local (and push to server)
  if (local != null && server != null):
    if (local.lastModifiedUtc > server.lastModifiedUtc) → 
      local wins, push local to server
    if (server.lastModifiedUtc > local.lastModifiedUtc) → 
      server wins, update local from server
    if (equal) → 
      use max(local.pageNum, server.pageNum)  // Furthest progress
      // (edge case: timestamps equal but pages differ due to clock skew)
```

### 9.2 Special Case: Offline Reading Advance

If the user reads 50 pages offline, their local `lastModifiedUtc` will be more recent than the server's. On reconnect:

1. Flush pending progress uploads first
2. The server accepts the upload (server-side `lastModifiedUtc` is updated)
3. No conflict — the offline progress is simply newer

### 9.3 Bookmark Conflicts

Bookmarks have no timestamps, so we use **set reconciliation**:

```
On full sync:
  serverBookmarks = readerApi.getAllBookmarks()
  localBookmarks = bookmarkDao.getAll()
  
  // Add server bookmarks we don't have locally
  for (sb in serverBookmarks):
    if (sb not in localBookmarks):
      bookmarkDao.upsert(sb.asEntity(syncState = SYNCED))
  
  // Remove local bookmarks that server doesn't have (but only if SYNCED)
  for (lb in localBookmarks where syncState == SYNCED):
    if (lb not in serverBookmarks):
      bookmarkDao.delete(lb)
  
  // Keep PENDING_SYNC bookmarks (they were created offline)
  // They'll be uploaded in the flush step
```

### 9.4 Mark Read Conflicts

Mark read/unread is idempotent — marking something read that's already read is a no-op. No conflict resolution needed. The server's state is authoritative and is refreshed when browsing the library.

---

## 10. Implementation Checklist

### Phase A: Auth Foundation
- [ ] Implement `KavitaAccountService` Retrofit interface
- [ ] Implement `KavitaPluginService` Retrofit interface
- [ ] Implement `KavitaAuthInterceptor` (JWT attachment + 401 refresh)
- [ ] Implement JWT secure storage in `EncryptedSharedPreferences`
- [ ] Implement `Plugin/authenticate` flow (API key → JWT)
- [ ] Implement `Account/login` flow (username/password → JWT + refresh)
- [ ] Implement `Account/refresh-token` flow
- [ ] Proactive token refresh (refresh when <5 min to expiry)
- [ ] UI: Login screen with both auth methods
- [ ] Server version check via `Plugin/version`

### Phase B: Progress Sync
- [ ] Create `ReadingProgressEntity` + DAO + migrations
- [ ] Create `PendingOperationEntity` + DAO
- [ ] Implement `KavitaReaderService` Retrofit interface
- [ ] Implement `ProgressSyncManager` (debounce, on-page, on-close)
- [ ] Implement `PendingOperationWorker` (flush queue)
- [ ] Implement `ProgressSyncWorker` (periodic pull)
- [ ] Implement conflict resolution (lastModifiedUtc comparison)
- [ ] Implement offline detection + auto-queue
- [ ] Integrate into reader ViewModel
- [ ] Implement `continue-point` fetching for library UI
- [ ] Implement `next-chapter` / `prev-chapter` navigation

### Phase C: Bookmark Sync
- [ ] Create `BookmarkEntity` + DAO + migrations
- [ ] Implement bookmark toggle in reader UI
- [ ] Implement optimistic local update + server push
- [ ] Implement `chapter-bookmarks` fetch on chapter open
- [ ] Implement `all-bookmarks` full sync (periodic + on app start)
- [ ] Implement set reconciliation logic
- [ ] Implement bookmark thumbnail fetching + disk cache
- [ ] Implement bookmark gallery UI (grouped by series/volume/chapter)
- [ ] Queue bookmark ops in `PendingOperationEntity` when offline
- [ ] Deduplication of pending bookmark add/remove pairs

### Phase D: Mark Read/Unread
- [ ] Implement `mark-chapter-read` (on chapter completion + manual)
- [ ] Implement `mark-series-read/unread` (series context menu)
- [ ] Implement `mark-volume-read/unread` (volume context menu)
- [ ] Queue mark-read ops in `PendingOperationEntity` when offline
- [ ] Integrate with progress sync (auto-mark on last page)
- [ ] UI: Read/unread indicators in library views

### Phase E: Fallback & Robustness
- [ ] Implement `KavitaPanelsService` (API-key fallback for progress)
- [ ] Auto-detect: use native Reader if JWT available, else Panels
- [ ] Network connectivity observer → trigger sync on reconnect
- [ ] Pending operation retry with exponential backoff
- [ ] Pending operation deduplication
- [ ] Error handling: 401 → re-auth, 403 → permission denied, 500 → retry
- [ ] Sync status indicator in UI (last synced, pending ops count)

---

## Appendix A: Sequence Diagrams

### A.1 Reading a Chapter (Progress Sync)

```
User                Mimiral             Kavita Server
  │                   │                      │
  │── open ch. 42 ───→│                      │
  │                   │── GET get-progress ──→│
  │                   │←── { page: 15 } ─────│
  │                   │                      │
  │                   │ ← compare with local │
  │                   │   local: page 12     │
  │                   │   server: page 15    │
  │                   │   server wins (15)   │
  │                   │                      │
  │←── show page 15 ──│                      │
  │                   │                      │
  │── turn to pg 16 ──→│ (debounce timer)    │
  │── turn to pg 17 ──→│                      │
  │── turn to pg 18 ──→│ (3 pages → flush)   │
  │                   │── POST progress ─────→│
  │                   │   { page: 18 }       │
  │                   │←── 200 ──────────────│
  │                   │                      │
  │── close reader ───→│                      │
  │                   │── POST progress ─────→│
  │                   │   { page: 20 }       │
  │                   │←── 200 ──────────────│
```

### A.2 Bookmark Toggle (Offline)

```
User                Mimiral             Kavita Server
  │                   │                      │
  │── toggle bm pg 5 ─→│                      │
  │                   │  (offline detected)   │
  │                   │  INSERT into bookmarks│
  │                   │    syncState=PENDING  │
  │                   │  INSERT into pending  │
  │                   │    operations queue   │
  │←── bookmark icon ──│                      │
  │                   │                      │
  │     ... later ... │  (connectivity restored) │
  │                   │                      │
  │                   │── POST bookmark ─────→│
  │                   │   { ch: 42, pg: 5 }  │
  │                   │←── 200 ──────────────│
  │                   │  UPDATE syncState=   │
  │                   │    SYNCED             │
  │                   │  DELETE from pending  │
```

### A.3 Auth Flow (API Key → JWT)

```
User                Mimiral             Kavita Server
  │                   │                      │
  │── enter API key ──→│                      │
  │                   │── GET Plugin/        │
  │                   │   authenticate ──────→│
  │                   │   Header: x-api-key  │
  │                   │                      │
  │                   │←── UserDto + JWT ────│
  │                   │                      │
  │                   │  Store JWT in        │
  │                   │  EncryptedSharedPrefs│
  │                   │  Set tokenExpiry     │
  │                   │                      │
  │←── "connected" ───│                      │
  │                   │                      │
  │  ... JWT expires ...                     │
  │                   │                      │
  │                   │── GET Reader/        │
  │                   │   get-progress ──────→│
  │                   │   Header: Bearer JWT │
  │                   │←── 401 ──────────────│
  │                   │                      │
  │                   │── GET Plugin/        │
  │                   │   authenticate ──────→│
  │                   │   (re-auth w/ API key)│
  │                   │←── new JWT ──────────│
  │                   │                      │
  │                   │── GET Reader/        │
  │                   │   get-progress ──────→│
  │                   │   Header: Bearer new │
  │                   │←── { page: 15 } ─────│
```

---

## Appendix B: Retrofit Setup

```kotlin
fun createKavitaRetrofit(
    baseUrl: String,
    authInterceptor: KavitaAuthInterceptor,
    moshi: Moshi
): Retrofit {
    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    
    return Retrofit.Builder()
        .baseUrl(baseUrl.ensureTrailingSlash())
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()
}

// Create service instances
val readerService: KavitaReaderService = retrofit.create()
val accountService: KavitaAccountService = retrofit.create()
val pluginService: KavitaPluginService = retrofit.create()
val panelsService: KavitaPanelsService = retrofit.create() // separate OkHttpClient w/ API key interceptor
```
