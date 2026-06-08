# Kavita API Reference — Integration-Relevant Endpoints

Full spec: kavita-openapi.json (473 endpoints, v0.9.0.6)

## Integration Priority Endpoints for Mimiral

### Reader (39 endpoints)

- `POST /api/Reader/all-bookmarks` [body] — Returns a list of all bookmarked pages for a User
- `POST /api/Reader/bookmark` [body] — Bookmarks a page against a Chapter
- `GET /api/Reader/bookmark-image` — Returns an image for a given bookmark series. Side effect: This will cache the bookmark images for reading.
- `GET /api/Reader/bookmark-info` — Returns various information about all bookmark files for a Series. Side effect: This will cache the bookmark images for reading.
- `POST /api/Reader/bulk-remove-bookmarks` [body] — Removes all bookmarks for all chapters linked to a Series
- `GET /api/Reader/chapter-bookmarks` — Returns a list of bookmarked pages for a given Chapter
- `GET /api/Reader/chapter-info` — Returns various information about a Chapter. Side effect: This will cache the chapter images for reading.
- `GET /api/Reader/continue-point` — Continue point is the chapter which you should start reading again from. If there is no progress on a series, then the first chapter will be returned (non-special unless only specials).
Otherwise, loop through the chapters and volumes in order to find the next chapter which has progress.
- `POST /api/Reader/create-ptoc` [body] — Create a new personal table of content entry for a given chapter
- `GET /api/Reader/file-dimensions` — Returns the file dimensions for all pages in a chapter. If the underlying chapter is PDF, use extractPDF to unpack as images.
- `GET /api/Reader/first-progress-date` — 
- `GET /api/Reader/get-progress` — Returns Progress (page number) for a chapter for the logged in user
- `GET /api/Reader/has-progress` — Returns if the user has reading progress on the Series
- `GET /api/Reader/image` — Returns an image for a given chapter. Will perform bounding checks
- `POST /api/Reader/mark-chapter-read` [body] — Mark a single chapter as read
- `POST /api/Reader/mark-multiple-read` [body] — Marks all chapters within a list of volumes as Read. All volumes must belong to the same Series.
- `POST /api/Reader/mark-multiple-series-read` [body] — Marks all chapters within a list of series as Read.
- `POST /api/Reader/mark-multiple-series-unread` [body] — Marks all chapters within a list of series as Unread.
- `POST /api/Reader/mark-multiple-unread` [body] — Marks all chapters within a list of volumes as Unread. All volumes must belong to the same Series.
- `POST /api/Reader/mark-read` [body] — Marks a Series as read. All volumes and chapters will be marked as read during this process.
- `POST /api/Reader/mark-unread` [body] — Marks a Series as Unread. All volumes and chapters will be marked as unread during this process.
- `POST /api/Reader/mark-volume-read` [body] — Marks all chapters within a volume as Read
- `POST /api/Reader/mark-volume-unread` [body] — Marks all chapters within a volume as unread
- `GET /api/Reader/next-chapter` — Returns the next logical chapter from the series.
- `GET /api/Reader/pdf` — Returns the PDF for the chapterId.
- `GET /api/Reader/prev-chapter` — Returns the previous logical chapter from the series.
- `POST /api/Reader/progress` [body] — Save page against Chapter for authenticated user
- `GET /api/Reader/prompt-reread/chapter` — Check if we should prompt the user for rereads for the given chapter
- `GET /api/Reader/prompt-reread/series` — Check if we should prompt the user for rereads for the given series
- `GET /api/Reader/prompt-reread/volume` — Check if we should prompt the user for rereads for the given volume
- `GET /api/Reader/ptoc` — Returns the user's personal table of contents for the given chapter
- `DELETE /api/Reader/ptoc` — Deletes the user's personal table of content for the given chapter
- `POST /api/Reader/remove-bookmarks` [body] — Removes all bookmarks for all chapters linked to a Series
- `GET /api/Reader/series-bookmarks` — Returns all bookmarked pages for a given series
- `GET /api/Reader/thumbnail` — Returns a thumbnail for the given page number
- `GET /api/Reader/time-left` — For the current user, returns an estimate on how long it would take to finish reading the series.
- `GET /api/Reader/time-left-for-chapter` — For the current user, returns an estimate on how long it would take to finish reading the chapter.
- `POST /api/Reader/unbookmark` [body] — Removes a bookmarked page for a Chapter
- `GET /api/Reader/volume-bookmarks` — Returns all bookmarked pages for a given volume

### Collection (12 endpoints)

- `GET /api/Collection` — Returns all Collection tags for a given User
- `DELETE /api/Collection` — Removes the collection tag from the user
- `GET /api/Collection/all-series` — Returns all collections that contain the Series for the user with the option to allow for promoted collections (non-user owned)
- `POST /api/Collection/delete-multiple` [body] — Delete multiple collections in one go
- `POST /api/Collection/import-stack` [body] — Imports a MAL Stack into Kavita
- `GET /api/Collection/mal-stacks` — For the authenticated user, if they have an active Kavita+ subscription and a MAL username on record,
fetch their Mal interest stacks (including restacks)
- `GET /api/Collection/name-exists` — Checks if a collection exists with the name
- `POST /api/Collection/promote-multiple` [body] — Promote/UnPromote multiple collections in one go. Will only update the authenticated user's collections and will only work if the user has promotion role
- `GET /api/Collection/single` — Returns a single Collection tag by Id for a given user
- `POST /api/Collection/update` [body] — Updates an existing tag with a new title, promotion status, and summary.
<remarks>UI does not contain controls to update title</remarks>
- `POST /api/Collection/update-for-series` [body] — Adds multiple series to a collection. If tag id is 0, this will create a new tag.
- `POST /api/Collection/update-series` [body] — For a given tag, update the summary if summary has changed and remove a set of series from the tag.

### ReadingList (27 endpoints)

- `GET /api/ReadingList` — Fetches a single Reading List
- `DELETE /api/ReadingList` — Deletes a reading list
- `POST /api/ReadingList/all` [body] — Returns reading lists (paginated) for a given user.
- `GET /api/ReadingList/all-people` — Returns all people in given roles for a reading list
- `POST /api/ReadingList/create` [body] — Creates a new List with a unique title. Returns the new ReadingList back
- `POST /api/ReadingList/delete-item` [body] — Deletes a list item from the list. Item orders will update as a result.
- `POST /api/ReadingList/delete-multiple` [body] — Delete multiple reading lists in one go
- `POST /api/ReadingList/export-as-cbl` — Export a Reading List to CBL format
- `GET /api/ReadingList/info` — Returns random information about a Reading List
- `GET /api/ReadingList/items` — Fetches all reading list items for a given list including rich metadata around series, volume, chapters, and progress
- `POST /api/ReadingList/lists` — Returns reading lists (paginated) for a given user.
- `GET /api/ReadingList/lists-for-chapter` — Returns all Reading Lists the user has access to that has the given chapter within it.
- `GET /api/ReadingList/lists-for-series` — Returns all Reading Lists the user has access to that the given series within it.
- `GET /api/ReadingList/name-exists` — Checks if a reading list exists with the name
- `GET /api/ReadingList/next-chapter` — Returns the next chapter within the reading list
- `GET /api/ReadingList/people` — Returns a list of a given role associated with the reading list
- `GET /api/ReadingList/prev-chapter` — Returns the prev chapter within the reading list
- `POST /api/ReadingList/promote-multiple` [body] — Promote/UnPromote multiple reading lists in one go. Will only update the authenticated user's reading lists and will only work if the user has promotion role
- `POST /api/ReadingList/regenerate-cover` — Regenerates the cover image for a reading list, you must own the given reading list to do this
- `POST /api/ReadingList/remove-read` — Removes all entries that are fully read from the reading list
- `POST /api/ReadingList/update` [body] — Update the properties (title, summary) of a reading list
- `POST /api/ReadingList/update-by-chapter` [body] — 
- `POST /api/ReadingList/update-by-multiple` [body] — Adds all chapters from a list of volumes and chapters to a reading list
- `POST /api/ReadingList/update-by-multiple-series` [body] — Adds all chapters from a list of series to a reading list
- `POST /api/ReadingList/update-by-series` [body] — Adds all chapters from a Series to a reading list
- `POST /api/ReadingList/update-by-volume` [body] — 
- `POST /api/ReadingList/update-position` [body] — Updates an items position

### Library (20 endpoints)

- `GET /api/Library` — Return a specific library
- `POST /api/Library/copy-settings-from` [body] — Copy the library settings (adv tab + optional type) to a set of other libraries.
- `POST /api/Library/create` [body] — Creates a new Library. Upon library creation, adds new library to all Admin accounts.
- `DELETE /api/Library/delete` — Deletes the library and all series within it.
- `DELETE /api/Library/delete-multiple` [body] — Deletes multiple libraries and all series within it.
- `POST /api/Library/grant-access` [body] — Grants a user account access to a Library
- `POST /api/Library/has-files-at-root` [body] — For each root, checks if there are any supported files at root to warn the user during library creation about an invalid setup
- `GET /api/Library/jump-bar` — For a given library, generate the jump bar information
- `GET /api/Library/libraries` — Return all libraries in the Server
- `GET /api/Library/list` — Returns a list of directories for a given path. If path is empty, returns root drives.
- `GET /api/Library/name-exists` — Checks if the library name exists or not
- `POST /api/Library/refresh-metadata` — 
- `POST /api/Library/refresh-metadata-multiple` [body] — 
- `POST /api/Library/scan` — Scans a given library for file changes.
- `POST /api/Library/scan-all` — Scans a given library for file changes. If another scan task is in progress, will reschedule the invocation for 3 hours in future.
- `POST /api/Library/scan-folder` [body] — Given a valid path, will invoke either a Scan Series or Scan Library. If the folder does not exist within Kavita, the request will be ignored
- `POST /api/Library/scan-multiple` [body] — Enqueues a bunch of library scans
- `GET /api/Library/type` — Returns the type of the underlying library
- `POST /api/Library/update` [body] — Updates an existing Library with new name, folders, and/or type.
- `GET /api/Library/user-libraries` — Gets libraries for the given user that you also have access to

### Series (32 endpoints)

- `GET /api/Series/age-rating` — Get the age rating for the Kavita.Models.Entities.Enums.AgeRating enum value
- `GET /api/Series/all-related` — Returns all related series against the passed series Id
- `POST /api/Series/all-v2` [body] — Returns all series for the library
- `POST /api/Series/analyze` [body] — Run a file analysis on the series.
- `GET /api/Series/chapter` — Returns a single Chapter with progress information
- `GET /api/Series/currently-reading` — Get series a user is currently reading, requires the user to share their profile
- `POST /api/Series/delete-multiple` [body] — Deletes multiple series from Kavita at once
- `POST /api/Series/dont-match` — When true, will not perform a match and will prevent Kavita from attempting to match/scrobble against this series
- `GET /api/Series/external-series-detail` — 
- `POST /api/Series/match` [body] — Sends a request to Kavita+ API for all potential matches, sorted by relevance
- `GET /api/Series/metadata` — Returns metadata for a given series
- `POST /api/Series/metadata` [body] — Update series metadata
- `GET /api/Series/next-expected` — Based on the delta times between when chapters are added, for series that are not Completed/Cancelled/Hiatus, forecast the next
date when it will be available.
- `POST /api/Series/on-deck` — Fetches series that are on deck aka have progress on them.
- `POST /api/Series/recently-added-v2` [body] — Gets all recently added series
- `POST /api/Series/recently-updated-series` — Returns series that were recently updated, like adding or removing a chapter
- `POST /api/Series/refresh-metadata` [body] — Runs a Cover Image Generation task
- `GET /api/Series/related` — Fetches the related series for a given series
- `POST /api/Series/remove-from-on-deck` — Removes a series from displaying on deck until the next read event on that series
- `POST /api/Series/scan` [body] — Scan a series and force each file to be updated. This should be invoked via the User, hence why we force.
- `GET /api/Series/series-by-collection` — Returns all Series grouped by the passed Collection Id with Pagination.
- `POST /api/Series/series-by-ids` [body] — Fetches Series for a set of Ids. This will check User for permission access and filter out any Ids that don't exist or
the user does not have access to.
- `GET /api/Series/series-detail` — Get a special DTO for Series Detail page.
- `GET /api/Series/series-with-annotations` — Returns all Series that a user has access to
- `POST /api/Series/update` [body] — Updates the Series
- `POST /api/Series/update-match` [body] — This will perform the fix match
- `POST /api/Series/update-related` [body] — Update the relations attached to the Series. Does not generate associated Sequel/Prequel pairs on target series.
- `POST /api/Series/v2` [body] — Gets series with the applied Filter
- `GET /api/Series/volume` — Returns a single Volume with progress information and Chapters
- `GET /api/Series/volumes` — Returns All volumes for a series with progress information and Chapters
- `GET /api/Series/{seriesId}` — Fetches a Series for a given Id
- `DELETE /api/Series/{seriesId}` — Deletes a series from Kavita

### Account (27 endpoints)

- `GET /api/Account` — Returns the current user, as it would from login
- `DELETE /api/Account/auth-key` — Delete the Auth Key
- `GET /api/Account/auth-keys` — Returns all Auth Keys with the account
- `POST /api/Account/clear-oidc-link` — Remove the OIDC link for the authenticated user. This action will also remove the authentication cookie.
The caller should take note and redirect to login if no other authentication is currently present (I.e. JWT)
- `POST /api/Account/confirm-email` [body] — Last step in authentication flow, confirms the email token for email
- `POST /api/Account/confirm-email-update` [body] — Final step in email update change. Given a confirmation token and the email, this will finish the email change.
- `POST /api/Account/confirm-migration-email` [body] — 
- `POST /api/Account/confirm-password-reset` [body] — 
- `POST /api/Account/create-auth-key` [body] — Creates a new Auth Key for a user.
- `GET /api/Account/email-confirmed` — 
- `POST /api/Account/forgot-password` — Will send user a link to update their password to their email or prompt them if not accessible
- `POST /api/Account/invite` [body] — Invites a user to the server. Will generate a setup link for continuing setup. If email is not setup, a link will be presented to user to continue setup.
- `GET /api/Account/invite-url` — Requests the Invite Url for the AppUserId. Will return error if user is already validated.
- `GET /api/Account/is-email-valid` — Is the user's current email valid or not
- `POST /api/Account/login` [body] — Perform a login. Will send JWT Token of the logged in user back.
- `GET /api/Account/oidc-authenticated` — Returns true if OIDC authentication cookies are present and the Kavita.Server.Extensions.IdentityServiceExtensions.OpenIdConnect
scheme has been registered
- `GET /api/Account/opds-url` — Returns the OPDS url for this user
- `GET /api/Account/refresh-account` — Returns an up-to-date user account
- `POST /api/Account/refresh-token` [body] — Refreshes the user's JWT token
- `POST /api/Account/register` [body] — Register the first user (admin) on the server. Will not do anything if an admin is already confirmed
- `POST /api/Account/resend-confirmation-email` — Resend an invite to a user already invited
- `POST /api/Account/reset-password` [body] — Update a user's password
- `POST /api/Account/rotate-auth-key` [body] — Rotate the Auth Key
- `POST /api/Account/update` [body] — Update the user account. This can only affect Username, Email (will require confirming), Roles, and Library access.
- `POST /api/Account/update/age-restriction` [body] — Change the Age Rating restriction for the user
- `POST /api/Account/update/email` [body] — Initiates the flow to update a user's email address.
            
If email is not setup, then the email address is not changed in this API. A confirmation link is sent/dumped which will
validate the email. It must be confirmed for the email to update.
- `POST /api/Account/update/username` [body] — Initiates the flow to update a user's username.

### Annotation (12 endpoints)

- `DELETE /api/Annotation` — Delete the annotation for the user
- `GET /api/Annotation/all` — Returns the annotations for the given chapter
- `POST /api/Annotation/all-filtered` [body] — Returns a list of annotations for browsing
- `GET /api/Annotation/all-for-series` — Returns all annotations by Series
- `POST /api/Annotation/bulk-delete` [body] — Removes annotations in bulk. Requires every annotation to be owned by the authenticated user
- `POST /api/Annotation/create` [body] — Create a new Annotation for the user against a Chapter
- `POST /api/Annotation/export` [body] — Exports Annotations for the User
- `POST /api/Annotation/export-filter` [body] — Exports annotations for the given users
- `POST /api/Annotation/like` [body] — Adds a like for the currently authenticated user if not already from the annotations with given ids
- `POST /api/Annotation/unlike` [body] — Removes likes for the currently authenticated user if present from the annotations with given ids
- `POST /api/Annotation/update` [body] — Update the modifiable fields (Spoiler, highlight slot, and comment) for an annotation
- `GET /api/Annotation/{annotationId}` — Returns the Annotation by Id. User must have access to annotation.

### Scrobbling (17 endpoints)

- `POST /api/Scrobbling/add-hold` — Adds a hold against the Series for user's scrobbling
- `POST /api/Scrobbling/bulk-remove-events` [body] — Delete the given scrobble events if they belong to that user
- `POST /api/Scrobbling/clear-errors` — Clears the scrobbling errors table
- `GET /api/Scrobbling/expired-tokens` — Returns all expired tokens for the current user
- `POST /api/Scrobbling/generate-scrobble-events` — Generate scrobble events from history. Should only be ran once per user.
- `POST /api/Scrobbling/generate-scrobble-events-all` — Generate scrobble events from history for all valid providers.
- `GET /api/Scrobbling/has-hold` — If there is an active hold on the series
- `GET /api/Scrobbling/holds` — Returns all scrobble holds for the current user
- `GET /api/Scrobbling/library-allows-scrobbling` — Does the library the series is in allow scrobbling?
- `DELETE /api/Scrobbling/remove-hold` — Remove a hold against the Series for user's scrobbling
- `POST /api/Scrobbling/retry-scrobble` [body] — Attempts to retry Scrobble Events for the current authenticated user (or admin-allowed).
- `GET /api/Scrobbling/scrobble-errors` — Returns all scrobbling errors for the instance
- `POST /api/Scrobbling/scrobble-events` [body] — Returns the scrobbling history for the user
- `GET /api/Scrobbling/scrobble-settings` — Returns all scrobble providers for a user. This list is guaranteed to contain an entry for each currently
valid scrobble provider. If the user has none setup, returns the empty default values.
- `GET /api/Scrobbling/token-expired` — Checks if the current Scrobbling token for the given Provider has expired for the current user
- `POST /api/Scrobbling/update-scrobble-settings` [body] — Updates the scrobble settings for a given provider. Libraries are filtered on supported types
- `POST /api/Scrobbling/update-user-scrobble-provider` [body] — Update authentication details for the given provider

### Stats (34 endpoints)

- `GET /api/Stats/avg-time-by-hour` — Returns the avg time read by hour in the given filter
- `GET /api/Stats/day-breakdown` — 
- `GET /api/Stats/device/client-type` — Returns client type breakdown for the current month
- `GET /api/Stats/device/device-type` — Desktop vs Mobile spread over this month
- `GET /api/Stats/favorite-authors` — 
- `GET /api/Stats/files-added-over-time` — 
- `GET /api/Stats/genre-breakdown` — Returns the top 10 genres that the user likes reading
- `GET /api/Stats/most-active-users` — Top 5 most active readers for the given timeframe
- `GET /api/Stats/page-spread` — 
- `GET /api/Stats/pages-per-year` — Returns a count of pages read per year for a given userId.
- `GET /api/Stats/popular-decades` — 
- `GET /api/Stats/popular-genres` — 
- `GET /api/Stats/popular-libraries` — 
- `GET /api/Stats/popular-people` — 
- `GET /api/Stats/popular-reading-list` — Gets the top 5 most popular reading lists. Counts a reading list as active if a user has read at least some
- `GET /api/Stats/popular-series` — 
- `GET /api/Stats/popular-tags` — 
- `GET /api/Stats/reading-activity` — 
- `GET /api/Stats/reading-counts` — Returns reading history events for a give or all users, broken up by day, and format
- `GET /api/Stats/reading-history` — Return a user's reading session history
- `GET /api/Stats/reading-history/series/{seriesId}` — Return the authenticated users reading session history for a given series
- `GET /api/Stats/reading-pace` — 
- `GET /api/Stats/reads-by-month` — 
- `GET /api/Stats/server/count/manga-format` — 
- `GET /api/Stats/server/count/publication-status` — 
- `GET /api/Stats/server/file-breakdown` — A breakdown of different files, their size, and format
- `GET /api/Stats/server/file-extension` — Generates a csv of all file paths for a given extension
- `GET /api/Stats/server/stats` — 
- `GET /api/Stats/tag-breakdown` — Returns top 10 tags that user likes reading
- `GET /api/Stats/total-reads` — Returns the total amount reads in the given filter
- `GET /api/Stats/user-read` — 
- `GET /api/Stats/user-stats` — 
- `GET /api/Stats/word-spread` — 
- `GET /api/Stats/words-per-year` — Returns a count of words read per year for a given userId.

### Search (4 endpoints)

- `GET /api/Search/chapters-by-series` — Returns all chapters for a given series with localized titles. Used for CBL chapter-level matching.
- `GET /api/Search/search` — Searches against different entities in the system against a query string
- `GET /api/Search/series-for-chapter` — Returns the series for the Chapter id. If the user does not have access (shouldn't happen by the UI),
then null is returned
- `GET /api/Search/series-for-mangafile` — Returns the series for the MangaFile id. If the user does not have access (shouldn't happen by the UI),
then null is returned

### Koreader (3 endpoints)

- `PUT /api/Koreader/{apiKey}/syncs/progress` [body] — Syncs book progress with Kavita. Will attempt to save the underlying reader position if possible.
- `GET /api/Koreader/{apiKey}/syncs/progress/{ebookHash}` — Gets book progress from Kavita, if not found will return a 400
- `GET /api/Koreader/{apiKey}/users/auth` — 

### Tachiyomi (2 endpoints)

- `GET /api/Tachiyomi/latest-chapter` — Given the series Id, this should return the latest chapter that has been fully read.
- `POST /api/Tachiyomi/mark-chapter-until-as-read` — Marks every chapter that is sorted below the passed number as Read. This will not mark any specials as read.

### Panels (2 endpoints)

- `GET /api/Panels/get-progress` — Gets the Progress of a given chapter
- `POST /api/Panels/save-progress` [body] — Saves the progress of a given chapter. This will generate a reading session with the estimated time from the
last progress till the current

### Device (10 endpoints)

- `DELETE /api/Device` — Deletes the device from the user
- `GET /api/Device` — 
- `GET /api/Device/client/all-devices` — Get All user client devices
- `DELETE /api/Device/client/device` — Removes the client device from DB
- `GET /api/Device/client/devices` — Get my client devices
- `POST /api/Device/client/update-name` [body] — Update the friendly name of the Device
- `POST /api/Device/create` [body] — Creates a new Device
- `POST /api/Device/send-series-to` [body] — Attempts to send a whole series to a device.
- `POST /api/Device/send-to` [body] — Sends a collection of chapters to the user's device
- `POST /api/Device/update` [body] — Updates an existing Device

### WantToRead (4 endpoints)

- `GET /api/want-to-read` — 
- `POST /api/want-to-read/add-series` [body] — Given a list of Series Ids, add them to the current logged in user's Want To Read list
- `POST /api/want-to-read/remove-series` [body] — Given a list of Series Ids, remove them from the current logged in user's Want To Read list
- `POST /api/want-to-read/v2` [body] — Return all Series that are in the current logged in user's Want to Read list, filtered

### Rating (4 endpoints)

- `POST /api/Rating/chapter` [body] — Update the users' rating of the given chapter
- `GET /api/Rating/overall-chapter` — Overall rating from all Kavita users for a given Chapter
- `GET /api/Rating/overall-series` — Overall rating from all Kavita users for a given Series
- `POST /api/Rating/series` [body] — Update the users' rating of the given series

### Review (5 endpoints)

- `GET /api/Review/all` — Returns all reviews for the user. If you are authenticated as the user, will always return data, regardless of ShareReviews setting
- `POST /api/Review/chapter` [body] — Update the user's review for a given chapter
- `DELETE /api/Review/chapter` — Deletes the user's review for the given chapter
- `POST /api/Review/series` [body] — Updates the user's review for a given series
- `DELETE /api/Review/series` — Deletes the user's review for the given series

### Opds (22 endpoints)

- `POST /api/Opds/{apiKey}` — Returns the Catalogue for Kavita's OPDS Service
- `GET /api/Opds/{apiKey}` — Returns the Catalogue for Kavita's OPDS Service
- `GET /api/Opds/{apiKey}/collections` — Get all Collections - Supports Pagination
- `GET /api/Opds/{apiKey}/collections/{collectionId}` — Get Series for a given Collection - Supports Pagination
- `GET /api/Opds/{apiKey}/favicon` — 
- `GET /api/Opds/{apiKey}/image` — This returns a streamed image following OPDS-PS v1.2
- `GET /api/Opds/{apiKey}/libraries` — Get the User's Libraries - No Pagination Support
- `GET /api/Opds/{apiKey}/libraries/{libraryId}` — Returns Series from the Library - Supports Pagination
- `GET /api/Opds/{apiKey}/on-deck` — Get the On Deck (Dashboard) - Supports Pagination
- `GET /api/Opds/{apiKey}/reading-list` — Get a User's Reading Lists - Supports Pagination
- `GET /api/Opds/{apiKey}/reading-list/{readingListId}` — Returns individual items (chapters) from Reading List by ID - Supports Pagination
- `GET /api/Opds/{apiKey}/recently-added` — Returns Recently Added (Dashboard Feed) - Supports Pagination
- `GET /api/Opds/{apiKey}/recently-updated` — Get the Recently Updated Series (Dashboard) - Pagination available, total pages will not be filled due to underlying implementation
- `GET /api/Opds/{apiKey}/search` — 
- `GET /api/Opds/{apiKey}/series` — OPDS Search endpoint
- `GET /api/Opds/{apiKey}/series/{seriesId}` — Returns the items within a Series (Series Detail)
- `GET /api/Opds/{apiKey}/series/{seriesId}/volume/{volumeId}` — Returns items for a given Volume
- `GET /api/Opds/{apiKey}/series/{seriesId}/volume/{volumeId}/chapter/{chapterId}` — Gets items for a given Chapter
- `GET /api/Opds/{apiKey}/series/{seriesId}/volume/{volumeId}/chapter/{chapterId}/download/{filename}` — Downloads a file (user must have download permission)
- `GET /api/Opds/{apiKey}/smart-filters` — Get the User's Smart Filters (Dashboard Context) - Supports Pagination
- `GET /api/Opds/{apiKey}/smart-filters/{filterId}` — Get the User's Smart Filter - Supports Pagination
- `GET /api/Opds/{apiKey}/want-to-read` — Get the User's Want to Read list - Supports Pagination

### ReadingProfile (16 endpoints)

- `POST /api/reading-profile` [body] — Updates the given reading profile, must belong to the current user
- `DELETE /api/reading-profile` — Deletes the given profile, requires the profile to belong to the logged-in user
- `GET /api/reading-profile/all` — Gets all non-implicit reading profiles for a user
- `POST /api/reading-profile/bulk` [body] — Assigns the reading profile to all passes series, and deletes their implicit profiles
- `POST /api/reading-profile/create` [body] — Creates a new reading profile for the current user
- `GET /api/reading-profile/library` — Returns all the Reading rofiles bound to the library
- `POST /api/reading-profile/library/{libraryId}` [body] — Sets the reading profile for a given library, removes the old one
- `DELETE /api/reading-profile/library/{libraryId}` — Clears the reading profile for the given library for the currently logged-in user
- `POST /api/reading-profile/promote` — Promotes the implicit profile to a user profile. Removes the series from other profiles
- `GET /api/reading-profile/series` — Returns all Reading Profiles bound to a series
- `POST /api/reading-profile/series` [body] — Update the implicit reading profile for a series, creates one if none exists
- `POST /api/reading-profile/series/{seriesId}` [body] — Sets the reading profile for a given series, removes the old one
- `DELETE /api/reading-profile/series/{seriesId}` — Clears the reading profile for the given series for the currently logged-in user
- `POST /api/reading-profile/set-devices` [body] — Set the assigned devices for a reading profile
- `POST /api/reading-profile/update-parent` [body] — Updates the non-implicit reading profile for the given series, and removes implicit profiles
- `GET /api/reading-profile/{libraryId}/{seriesId}` — Returns the ReadingProfile that should be applied to the given series, walks up the tree.
Series -> Library -> Default

### Book (4 endpoints)

- `GET /api/Book/{chapterId}/book-info` — Retrieves information for the PDF and Epub reader. This will cache the file.
- `GET /api/Book/{chapterId}/book-page` — This returns a single page within the epub book. All html will be rewritten to be scoped within our reader,
all css is scoped, etc.
- `GET /api/Book/{chapterId}/book-resources` — This is an entry point to fetch resources from within an epub chapter/book.
- `GET /api/Book/{chapterId}/chapters` — This will return a list of mappings from ID -> page num. ID will be the xhtml key and page num will be the reading order
this is used to rewrite anchors in the book text so that we always load properly in our reader.

### Activity (1 endpoints)

- `GET /api/Activity/current` — Returns active reading sessions on the Server

## All Other Tags

- Admin (1 endpoints)
- Cbl (14 endpoints)
- Chapter (5 endpoints)
- ColorScape (3 endpoints)
- Download (12 endpoints)
- Email (1 endpoints)
- Filter (12 endpoints)
- Font (6 endpoints)
- Health (1 endpoints)
- Image (15 endpoints)
- KavitaPlusAudit (4 endpoints)
- License (9 endpoints)
- Locale (1 endpoints)
- Manage (1 endpoints)
- Metadata (13 endpoints)
- Oidc (2 endpoints)
- Person (10 endpoints)
- Plugin (5 endpoints)
- Server (17 endpoints)
- Settings (19 endpoints)
- Stream (17 endpoints)
- Theme (7 endpoints)
- Upload (10 endpoints)
- Users (10 endpoints)
- Volume (4 endpoints)