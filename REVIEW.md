# Code review (2026-11-10, at v0.4 / `dfc4505`)

## How this was done

Read by hand, not built: this machine has no Android SDK, so **nothing below was reproduced on a
device and the unit tests were not run**. Each finding says how sure it is.

- Read in full: the app container, `MainActivity`, the whole network and session layer, every
  repository, the background check and notifications, the updater, the upload queue and file
  handling, the login and WebView code, the build files and the CI workflow.
- Read in part: the parsers (header, submission, inbox, activity, notes, journal, profile, explore,
  comic) and the navigation root (`AppRoot`), plus the loading code of the viewer, the comic reader
  and the tab shell.
- Not read: `UploadParser`, the FA-settings parser and form, `UploadCodec`, `DemoSource`, and most
  of the screens' Compose code (layout, sheets, the loader animation). Accessibility and visual
  behaviour are not covered.

Checked and fine: no keystore, cookie or `local/` file was ever committed; the fixtures hold no
e-mail addresses or real form keys; the three string files have the same entries; backups are off;
cleartext traffic is off by default; the update is safe against a swapped APK because Android
checks the signature.

## Status (2026-11-11, after three rounds of fixes)

All bugs are fixed or settled (7 did not happen on a phone). Under "Decisions worth a second look",
each entry now says what was done. Still open there: notifications by item instead of by counter,
downloads on Android 8 and 9, and the two entries about the debug build and the demo code, left as
they are because there is one build line for now. "Build and CI", "Tests" and "Documentation" are
open except where marked.

## Bugs

Ordered by how much they hurt.

### 1. An upload cut off by Android stays "Sending" for good

**Fixed:** a cancelled send puts the job back (`Sending` to `Queued`, `Finishing` to `Failed` with the
"interrupted" text), and the worker does the same for anything left over when it starts.

`data/UploadRepository.kt:352-427`, `data/upload/UploadWorker.kt`

The queue needs a connection (`NetworkType.CONNECTED`). When the connection drops mid-upload,
WorkManager stops the worker, which cancels its coroutine. `sendOnce` catches `IOException` only,
so the cancellation passes through and the job keeps the status `Sending` (or `Finishing`) that was
already saved. Nothing sets it back while the app's process lives: `recover()` (line 97) only runs
when the repository is created, `nextQueued()` (line 307) only takes `Queued` jobs, and the uploads
list shows no button for a `Sending` job. The worker runs again when the connection is back, finds
nothing queued and ends. The upload sits at "Sending 37 %" until the app is killed and reopened.

Sure by reading; not reproduced. Fix: in `sendOnce`, on cancellation, put a `Sending` job back to
`Queued` (and a `Finishing` one to `Failed` with the "interrupted" text) before rethrowing; and let
the worker apply `recover()` to stale jobs when it starts.

### 2. A comic stops loading when the screen is left or the phone is turned

**Fixed:** the walk runs in the app's scope, one job per comic; Back from the reader ends it
(`close()`), opening a page's submission or turning the phone doesn't. Tested in `LoadingTest`.

`ui/viewer/ComicScreen.kt:84`, `data/ComicRepository.kt:42`

The page-by-page walk runs inside the screen's `LaunchedEffect`, so it is cancelled when the reader
taps a page's label (which opens the submission), or rotates the phone (the activity is recreated).
The state is left at `loading = true`. Coming back calls `open()` again, but line 42 returns early
whenever the same comic is "loading or without error". The remaining pages never load and the
spinner at the end never goes away. `close()` (line 58) is never called by anything.

Sure by reading. Fix: run the walk in the app scope and keep one `Job` per comic in the repository
(cancel it in `close()`, call `close()` when the screen is popped), or reset `loading` on
cancellation.

### 3. "Remove location" fails open

**Fixed (the first paragraph):** when the tags can't be removed, or are still there afterwards, the
picture is re-encoded, which drops all of its metadata. So is a picture that names a place in its
XMP data, and one whose content isn't what its name says.

`data/upload/UploadFiles.kt:157-165`

`removeLocation` wraps everything in `runCatching` and ignores the result. If the EXIF data can't be
read or rewritten (a damaged header, an unusual JPEG), the photo is uploaded with its GPS tags, and
nothing tells the user. For a privacy switch that is on by default, the safe direction is the other
one: when stripping fails, re-encode the picture (which drops all metadata, as `reencode` already
does for other formats) or refuse the upload.

Two smaller holes in the same feature: only EXIF GPS tags are removed, so a location stored in XMP
stays; and the choice between "strip" and "re-encode" follows the file name's extension, not the
file's real type.

### 4. Large artwork can run the app out of memory, then again at every start

**Fixed:** pictures that have to be re-encoded are decoded to 16 million pixels at most, and running
out of memory fails that one upload with a message. Not tried with a real file.

`data/upload/UploadFiles.kt:109-138`, limit at line 242

A picture over FA's 10 MB is decoded whole up to 40 million pixels: 160 MB as a bitmap. Rotating it
makes a second copy, and each shrinking step makes another while the previous one is still held.
That is past the heap limit of most phones (256-512 MB without `largeHeap`). `OutOfMemoryError` is
not an `IOException`, so it kills the process with the job still `Sending`; the next start turns it
back to `Queued`, `resume()` starts the worker, and it crashes again.

A 6000 x 6000 PNG over 10 MB is an ordinary thing for an artist to have, so this is a likely case,
but it is a risk judged from the numbers, not a crash I saw. Fix: decode to about 16-20 million
pixels at most, recycle before scaling, catch `OutOfMemoryError` and fail the job with a message.

### 5. Comic detection misreads many ordinary titles and links

**Fixed:** keywords and link words must be whole words, and a link text over 30 characters is not a
series link. A short one like "my first commission" still counts. Tested in `ComicParserTest`.

`data/parse/ComicParser.kt:19-22` and `47-54`

- The page-number pattern allows the words `page|pg|part|pt|chapter|ch|episode|ep|no` right before
  the number without requiring them to be whole words, so the end of a normal word is eaten. Checked
  by running the pattern: "Sketch 24" gives the base title "Sket", "Piano 2" gives "Pia", "March 12"
  gives "Mar", "Casino 7" gives "Casi". The search for the other pages then asks FA for
  `@title "Sket"` and finds nothing. Fix: require a word boundary before the keyword (`\b(?:page|…)`).
- Link kinds are matched as substrings of the link text: "back" also matches "background" and
  "feedback", "first" matches "my first commission", "last" matches "blast", "next" matches any
  sentence with the word. Any description that links another submission with such text is offered
  as a comic and walks to unrelated submissions. Fix: match whole words, and only short link texts.

### 6. Uploads and drafts carry over to the next account

**Fixed (the first paragraph):** every upload keeps the account it was queued under. Another account
sees and sends only its own; the first one's form is put aside and its queue goes on when it logs
in again. Logging out now also sends FA's own "Log Out" first (read from the page header; the form's
shape is assumed, not yet seen on a live page), and answers that arrive after a logout are dropped.

`AppContainer.kt:177-187`, `data/UploadRepository.kt:455`

`logout()` clears every repository, but `uploads.clear()` only resets the upload page's info. The
half-filled form, the queue, failed jobs (with their Retry button) and the list of finished uploads
stay in `state.json`. After logging in as someone else, those are shown, and a retry sends the
first account's file to the second account. Saved artists and submissions and recent keywords are
shared across accounts the same way, which may be intended; the upload queue should not be.

Related: logging out only deletes the cookies on the phone (`LiveSource.clearSession`, line 104).
FA's session itself stays valid on FA's side. Calling FA's own logout first would end it properly.
Requests already under way are not cancelled either, so a refresh that finishes after logout can
fill a repository again.

### 7. Reopening the app from "recents" repeats the share or the link

**Not a bug:** tried on a phone, the upload form does not come back. Nothing changed.

`MainActivity.kt:70-73`

`handleLink` and `handleOpen` run whenever `savedInstanceState` is null. After the activity was
closed with Back and is started again from the recents list, Android delivers the original intent
once more, so the app jumps to the upload form again (and tries to import the same files) or
reopens the same submission. Likely by how Android behaves; not tried. Fix: skip both when the
intent has `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`.

### 8. Smaller ones

- **Any app can crash the start.** *(Fixed: the extra is read in debug builds only.)* `MainActivity.kt:68` reads the `screen` extra in release builds
  too (the comment says it is for CI). `AppRoot.kt:162` navigates to anything starting with `user/`
  or `journal/`; `user/a/b` matches no route and `navigate` throws. Gate the extra behind
  `BuildConfig.DEBUG` like `view` and `demo`.
- **The Cloudflare check loads the address that failed** *(fixed: it shows FA's front page)* (`data/FaClient.kt:61`). For FA's action
  links (`/fav/<id>/?key=…`, `/watch/…`) the WebView performs the action and OkHttp then repeats it.
  Harmless for fav and watch today, but a neutral FA page would be the safer thing to show.
- **A stale note key is kept after a failed send** *(fixed)* (`data/NotesRepository.kt:133`). If FA rejects
  the cached `key`, every further try fails until the notes list is loaded again. Drop the key when
  a send isn't confirmed.
- **Explore can get stuck on "loading more"** *(fixed)* (`data/ExploreRepository.kt:95`). A `loadMore` whose
  answer is dropped because the query changed leaves `loadingMore = true`; if the refresh that
  follows fails, further pages don't load until a refresh succeeds.
- **Two requests for one page.** *(Fixed: one request per submission at a time.)* The viewer loads the next submission ahead
  (`ui/viewer/ViewerScreen.kt:154`) and the page itself loads it again if the swipe comes first;
  `SubmissionRepository.load` has no "already on its way" check.
- **The background check swallows cancellation** *(fixed; the background work now shares one client)* (`data/notify/CheckWorker.kt:33`, `runCatching`
  around a suspending call) and builds a new `OkHttpClient` on every run.

## Decisions worth a second look

- *(Open: exact tracking needs the detail pages on every check, three more requests each time.)*
  **Notifications follow counters, not items** (`data/notify/ActivityCheck.kt:39`). News is
  "the counter is higher than last time". If three comments are removed on the website and three new
  ones arrive between two checks, nothing is announced. Remembering the newest item id per category
  would be exact, and the detail pages are already loaded when a counter grows.
- *(Done: one notification when the check finds the login gone, or Cloudflare's check three rounds in a row.)*
  **A silent stop.** When the session has expired or Cloudflare wants a check, the background check
  returns quietly, every time, for good. One notification "Open the app to keep getting
  notifications" would tell the user.
- *(Done: the check asks for the inbox, unless the app is showing another folder at that moment.)*
  **Notes details depend on the folder last shown.** FA keeps the notes folder on its side; the
  check reads `/msg/pms/` in whatever folder the app switched to last, so after looking at Sent or
  Archive the notification falls back to a bare count.
- *(Partly: requests can now be cancelled and hold no thread; the one-at-a-time lock stays, on purpose.)*
  **One lock for all requests, held for the whole request** (`data/net/LiveSource.kt:69-79`). It is
  what keeps the app polite, but one slow page (30 s timeout) holds up everything behind it,
  including the login check. The calls are blocking `execute()`s inside a coroutine, so cancelling a
  screen does not cancel its request. A cancellable call (`suspendCancellableCoroutine` with
  `call.cancel()`) would fix the second part and also make bug 1 easier to handle.
- *(Done for page loads: up to two waits, as long as FA says or 3 s and 8 s. Forms are never re-sent. No page cache.)*
  **No waiting on 429/503.** Only uploads read FA's flood message. Page loads report the HTTP code
  and stop. PLAN.md also promises "cached pages" and backing off when many users are online; neither
  exists.
- *(Done: `FaClient.parsed` for pages that are already there.)*
  **`FaClient.read` is not used everywhere.** It exists so a parser tripping over changed markup
  becomes an error message, off the main thread. `NotesRepository.open` (line 95),
  `ActivityRepository.submit` (line 62), `postReply` (`Posting.kt:25`) and `UploadRepository.prepare`
  parse the page themselves, on the main thread, without that net.
- *(Done: the raw answer is kept and used.)*
  **FA's JSON answer is read through the HTML parser** (`data/FaSettingsRepository.kt:73`). It works
  for the short answers FA sends now; a `<` or `&` in an error text would change it. A raw-body
  request in `PageSource` would be exact.
- *(Done: the script checks https and the host; release builds set `FLAG_SECURE` on the login.)*
  **The password is put into whatever page the login WebView shows** (`ui/login/LoginActivity.kt:170`).
  Navigation is held to `*.furaffinity.net` and Cloudflare's check host, so the risk is small, but
  checking `location.protocol` and `location.hostname === 'www.furaffinity.net'` inside the script
  costs one line. `FLAG_SECURE` on that screen would keep the fields out of screenshots.
- *(Left as it is: one build line for now.)*
  **The debug build is signed with the release key** (`app/build.gradle.kts:48`) and uploaded as a
  CI artifact. It is debuggable, has the exported login activity and the demo extras, and installs
  over the real app with its data. An `applicationIdSuffix = ".debug"` or the plain debug key for the
  artifact avoids that.
- *(Done: lists wait in the container under a ticket; the viewer opened from Explore loads further pages.)*
  **Id lists travel in navigation routes** (`ui/AppRoot.kt:314` and four more). A long Explore
  session puts thousands of ids into one route string that is saved with the back stack. The list is
  also fixed when the viewer opens, so swiping ends where the grid had loaded to. A shared holder in
  the container (as the inbox viewer nearly does) solves both.
- *(Done: the 30 most recent of each.)*
  **Memory.** Profiles and journals are kept for the whole run without a cap
  (`data/ProfileRepository.kt:21`, `data/JournalRepository.kt:15`); submissions are capped at 60.
- *(Open: needs a storage permission and a phone with Android 8 or 9 to try it.)*
  **Downloads on Android 8 and 9** go to the app's own folder (`ui/util/Actions.kt:45`), where no
  gallery sees them. `MediaStore` or the storage permission for those two versions would put them
  with the others.
- *(Done: `Words`, with German and Russian texts.)*
  **English-only messages in a three-language app.** About twenty fallback texts in the data layer
  ("Network error", "This note couldn't be shown.", "Fur Affinity didn't take the change.") are
  fixed strings, next to FA's own messages which are rightly left as FA sends them.
- *(Left as it is: one build line for now.)*
  **`DemoSource` ships in release builds** (`AppContainer.kt:73`). It can't be switched on there,
  but it belongs in the debug source set next to its fixtures.

## Build and CI

- **No Gradle wrapper.** CI installs Gradle 8.9 by version and the README asks the same of a local
  build. A committed wrapper pins the version and its checksum for everyone.
- **Write access for the whole workflow** *(fixed: only the build job has it; pinning is open)* (`.github/workflows/build.yml:15`). Only the release step
  needs `contents: write`; the screenshots job runs a third-party emulator action with it too. Set
  it on the build job only. The third-party actions are pinned by tag (`@v2`, `@v4`), not by commit.
- **Every push to `main` is a public release**, with no notes *(notes are now generated; the rest is open)*. Users are asked to update for each
  push, and the dialog can't say what changed. `generate_release_notes: true` is one line; a manual
  release step (or a tag) would make releases a decision.
- **Re-running a workflow run** reuses its run number, so it replaces the APK of an existing release
  with a different build of the same version code.
- **The version is worked out twice**, in the workflow's shell and in `build.gradle.kts`; they can
  drift apart. Let Gradle print it for the workflow.
- **No lint or static analysis.** `gradle lint` would have flagged a few of the things above.
- **Dependencies are about two years old** (AGP 8.7.3, Kotlin 2.0.21, Compose BOM 2024.12, Coil 2,
  OkHttp 4.12, WorkManager 2.9.1), and nothing watches them. Dependabot or Renovate would.

## Tests

The parsers are well covered by fixtures. What has no test at all: every repository except
`FaClient`, the upload queue (status changes, flood waits, retries), `UploadFiles`, the comic walk,
the cookie jar, and all UI. Bugs 1, 2 and 5 are each a few lines of JVM test with a fake
`PageSource`. Most fixtures are hand-built from the live structure (PLAN.md says which), so the
tests show the parsers agree with the author's reading of FA, not with FA; a scrubbed real page per
parser would close that gap.

## Documentation

PLAN.md has grown into a changelog and contradicts itself in places: the table at the top says the
APK is "signed with a committed key" (later corrected), the architecture section lists Room, cached
pages and the online-users back-off, none of which exist. Splitting it into a short "how it works
now" and a dated history would keep the first part true.

## Suggested order

1. Bugs 1 and 2 (stuck states the user can't get out of), with a test each.
2. Bug 3 (privacy) and bug 4 (crash loop).
3. Bug 6 (accounts) and the `screen` extra.
4. Bug 5, then the smaller ones.
5. Wrapper, workflow permissions, lint, release notes.
