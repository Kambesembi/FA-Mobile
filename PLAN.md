# FA Mobile – plan (draft 1)

An unofficial Android app for Fur Affinity. Light, quick, and easy to use. The core is the
**submission inbox, notifications and notes**. Browsing, search, profiles, favorites and journals
are all reachable, but they come second.

## Decisions (2026-11-04)

- **Stack:** native Kotlin + Jetpack Compose.
- **Look:** FA-style dark palette with the rounded "bubbles" style from FA-ImprovedLayout, plus a light variant.
- **Background check:** every 30 min by default, adjustable or off in settings.
- **Data:** the repo is private for now and goes public when finished. Anything personal (raw saved
  pages from the account, cookies, logs) stays in the git-ignored `local/` folder and is never
  committed. Only scrubbed test pages go into the repo.

## What we have to work with

| Resource | What we take from it |
|---|---|
| FA website (your Chrome session, logged in) | FA has no public API, so the app reads the same HTML pages a browser gets. I can inspect the live markup in your session while I build each screen. |
| [FA-ImprovedLayout](https://github.com/Kambesembi/FA-ImprovedLayout) | Your own code and ideas, free to reuse: the bubbles look, quick view with ← →, full-screen zoom/pan, comic reader, infinite scroll, pinned artists/submissions, clear-all notifications, SFW switch, +Fav without reload, "more from this artist", link warnings, EN/DE/RU. |
| [Ceylo/FurAffinityApp](https://github.com/Ceylo/FurAffinityApp) | Swift/Skip, iOS first, Android is only a preview. **It has no license, so we read it for lessons and copy no code.** Lessons: log in through FA's own login page in a WebView and keep only the cookies, never the password; Cloudflare's "verify you are human" check has to be solved once in the WebView; FA staff (2022) allow apps like this as long as they don't make excessive requests. |
| Your existing Android setup (Mood-Check, widgets) | No JDK/SDK on this PC. APKs build in GitHub Actions on push, are signed with a committed key and published as a release; emulator screenshots come back as an artifact for review. |

## Architecture

- **Native Kotlin + Jetpack Compose (Material 3)**: one small APK, smooth scrolling, real Android notifications.
- **Networking:** OkHttp, with cookies shared with the login WebView and the same User-Agent as the WebView, so Cloudflare accepts both.
- **Parsing:** Jsoup turns FA pages into plain data models (`SubmissionPreview`, `Submission`, `Comment`, `Note`, `Notification…`, `UserProfile`). Every parser gets unit tests against saved HTML pages, so CI notices when FA changes its markup.
- **Images:** Coil (with GIF support), a zoomable viewer, and a disk cache.
- **Polite to FA:** one request at a time per screen, cached pages, no gallery scraping, and the background check reads one small page. The footer shows how many registered users are online; the background check backs off when that is high.
- **Storage:** DataStore for settings and pins, Room for the notes/notification cache (if we need offline).
- **Package:** `com.kambesembi.famobile`, minSdk 26, target 35.

## Navigation

Bottom bar with 4 tabs and badges with FA's own unread counts:

1. **Inbox**: new submissions from people you watch (grid or feed).
2. **Activity**: watches, comments, shouts, favorites, journals.
3. **Notes**
4. **Explore**: browse, search, pinned artists.

Your profile, settings (with the SFW switch) and logout sit behind the avatar in the top bar.
Opening any furaffinity.net link (from other apps or inside descriptions) goes to the matching in-app screen.

## Iterations

Each one ends with a signed APK in GitHub Releases plus emulator screenshots, and then your feedback before the next.

| # | Scope | Done when |
|---|---|---|
| 0 | **Foundation:** repo, CI, signing, theme, nav shell, WebView login + Cloudflare, session/cookie handling, unread counts in the header | You can log in on the phone and see your real counts on the tab badges |
| 1 | **Submission inbox + viewer:** grid/feed with infinite scroll, swipe between submissions, full-screen zoom, fav/unfav, download, share, description, comments (read), remove from inbox / clear all; stories, audio, PDF and flash fallbacks | Daily inbox triage works entirely on the phone |
| 2 | **Activity + push notifications:** all categories from `/msg/others`, grouped, delete per item or section; WorkManager background check (every 30 min by default) → Android notifications per category | A new comment shows up as a phone notification |
| 3 | **Notes:** inbox/sent/folders, read thread, compose & reply (BBCode toolbar), mark unread, move, delete | Notes fully usable |
| 4 | **Profiles:** user page, gallery/scraps/favorites/journals with infinite scroll, watch/unwatch, shouts, pin artist | Any artist fully browsable |
| 5 | **Explore:** browse with filters, search with all options, ratings filter, pinned artists/submissions | Browse & search feature parity |
| 6 | **Writing:** post/reply comments, shouts, journal view + comments, +Fav from grids | Full interaction |
| 7 | **Polish:** comic reader, link warnings, EN/DE/RU, accessibility, performance pass, tablet/foldable layout | Release candidate |

## Notes from Iteration 0

- **Counts:** every page's header updates the tab badges. The explicit refresh loads `/controls/settings/`
  (about 58 KB), the smallest logged-in page that still has the full header.
- **Login detection:** a page in the login WebView showing `img.loggedin_user_avatar` means we're logged in.
  Logging out deletes the cookies on the phone.
- **Cloudflare:** a 403/503 with the interstitial (or `cf-mitigated: challenge`) opens a full-size check
  WebView. When it passes, the connections are dropped and the request is retried once. The WebViews and
  OkHttp send the same User-Agent (WebView default + `FAMobile/<version>`).
- **Signing:** the keystore lives only in `local/signing/` and in the `SIGNING_KEYSTORE_B64` /
  `SIGNING_PASSWORD` secrets, so it's never in git history once the repo goes public. Releases are
  published only when those secrets are set.
- **Theme:** the app reads the modern theme's markup. Accounts set to FA's classic theme aren't supported yet.

## Notes from Iteration 1

- **Inbox:** `/msg/submissions/new@72/`, 72 per page, newest first; "Next 72" links to the older page.
  Removing posts `messagecenter-action=remove_checked` with `submissions[]`; "clear inbox" posts
  `nuke_notifications`. Neither needs a hidden token.
- **Thumbnails:** `t.furaffinity.net/<id>@<size>-<ts>.jpg` comes in 150–800 px, so the grid asks for the
  size that fits a cell. The aspect ratio comes from the page, so the grid doesn't jump while images load.
- **Viewer:** the inbox order is fixed when the viewer opens, so "Done" (remove from inbox) doesn't move
  the page you are reading. Fav/unfav is FA's own `/fav/<id>/?key=…` link, so no extra request is needed
  to get a key. Stories, music and flash show the cover plus a button that opens the file.
- **Links:** `furaffinity.net/view/…` and `/full/…` open in the app. On Android 12 and later this needs
  "Open by default" → "Add link" in the app's system settings once.
- **Saving:** files go through Android's download manager with the same cookies and User-Agent, into
  Pictures/FA Mobile (images) or Download/FA Mobile (everything else).
- **Test pages:** the scrubber (`tools/scrub_fixture.py`) reads the account's own names from
  `local/own-names.json`, so they never reach the repo.

## Prep for Iteration 2

- **Background check (done early):** a WorkManager job loads `/controls/settings/` every 30 minutes by
  default (15 min to 3 h, or off) and notifies for each counter that grew since the app or the previous
  check last saw it. There is one channel per category. It never shows a Cloudflare check; it skips that round.
- **`/msg/others/`** (checked live, values masked): one `form` posting to `/msg/others/`, one
  `section#messages-<kind>.section_container` per category with a `.section-header` (Select all,
  `remove-<kind>` and `nuke-<kind>` submit buttons) and a `li` per item. A journal item is
  `input[name="journals[]"][value=<id>]`, `a[href=/journal/<id>/] em.journal_subject`, a
  `c-contentRating--*` span, the author's `c-usernameBlockSimple`, and `.popup_date[data-time]`. A
  `section#section-global-controls` has `remove-all`. Only journals were present at the time; the other
  kinds (watches, comments, favorites, shouts) still need a look when the account has some.
- **Activity tab (v0.1.11):** built on that structure. The parser reads each section's checkbox and
  remove/nuke button names from the page, so an unknown kind still lists and removes. `fixtures/msg-others.html`
  is hand-built: journals as checked live, the other sections assumed to follow the same layout.
- **Feedback on v0.1.7:** cards show only the image in FA's rating frame (grey/blue/red); the viewer's
  "Done" button is gone. The login focus bug was still there, so the login WebView no longer resizes for
  the keyboard (see `LoginActivity`).

## Notes from Iteration 3

- **Notes (v0.1.14):** `/msg/pms/` lists 50 notes per page (`.c-noteListItem`, unread links carry
  `unread`); "Next" goes to `/msg/pms/2/`. The folder is FA-side state, switched with
  `/controls/switchbox/<folder>/`, so every refresh switches first. Opening `/msg/pms/1/<id>/` marks the
  note read. Moving posts `manage_notes`, `items[]` and `move_to` (trash, archive, unread) to `/msg/pms/`.
- **Replies:** the note page carries FA's own reply form (`form#note-form` to `/msg/send/` with `key`,
  `to`, "RE:" `subject` and the quoted `message`), so the app starts from that. Sending succeeds when FA
  answers with the notes page; anything else is shown as the reason.
- **Test pages:** `msg-pms.html` and `msg-pms-note.html` are hand-built from the live structure (checked
  with values masked); no real note content. Priorities (`set_prio`) are parsed by FA but not offered yet.

## Notes from Iteration 4

- **User pages** (checked on public pages, logged out): every page under `/user/`, `/gallery/`, `/scraps/`,
  `/favorites/` and `/journals/` starts with `userpage-nav-header`: avatar, display and URL name, the user
  title and registration date in `.user-title`, and `a#watch-button`. Logged out, its `?key=` is empty, so
  the app shows Watch only when the key is there. `/watch/<name>/?key=…` and `/unwatch/…` are FA's own links.
- **Home:** profile text (`.userpage-profile`), stats (`span.highlight` labels), the "User Profile" table,
  the latest submissions and favorites, watcher counts from the watchlist links, and shouts
  (`#shouts .comment_container`, same markup as comments, anchor `shout-<id>`). Shouts have their own pages
  (a POST form); the app shows the first page only.
- **Galleries:** one page of figures like the inbox (48 favorites per page logged out); "Next" is a GET form (`/gallery/<name>/2/`,
  favorites `/favorites/<name>/<fav-id>/next`). **Journals:** 25 full journals per page, "Older" goes on.
  Journal comments open on FA until Iteration 6.
- **Links:** user links anywhere (artist, comment authors, activity, notes, profile text) open the profile in
  the app; "My profile" is in the account sheet. Pinned artists move to Iteration 5 with Explore.
- **Test pages:** `user-*.html` are hand-built from that structure with made-up names; no real profile content.

## Notes from Iteration 5

- **Browse** (`/browse/`) and **search** (`/search/?q=…`) are GET pages (checked on public pages). Their
  filter lists (category, type, species, gender) are read from the page itself, so they stay current.
  Mature and Adult are offered only when FA doesn't grey them out for the account.
- **Account defaults:** the first page of a plain browse or search is loaded without options, so FA applies
  the account's own ratings and page size; the page says which, and later pages ask for exactly that.
  Changed filters send FA's full option set.
- **Saved** (replaced the pins after feedback): a flag on profiles (top bar) and in the viewer (Save) keeps an
  artist or submission on the phone; the account menu's Saved page lists both, newest first.
- **Test pages:** `browse.html` and `search.html` hold FA's published filter lists around made-up figures.
  The logged-in rating checkboxes on browse are assumed to be named like the hidden fields (`rating_mature`);
  still to check on a real logged-in page.

## Feedback on v0.1.21 (before Iteration 6)

- Thumbnail frames are 1 dp and toned down (general 45 %, mature/adult 80 % opacity).
- Notes show avatars: FA serves `a.furaffinity.net/<name>.gif` without the timestamp its own URLs carry.
- Notifications say who did what. When a counter grew, the background check also loads that one page
  (`/msg/others/`, `/msg/pms/` or the inbox) and names up to five of the newest items; if that page
  can't be loaded, the notification falls back to the count.
- Profile tabs open at the top: they show a loader until the first page is in (the list used to keep its
  loading footer in view and so scrolled past the new items).
- The Watch icon in the profile top bar is gone; only the button on the profile's home stays.
- Pins became Saved (see Iteration 5). The search and browse sheets show ratings as checkboxes, and the
  Kinds chips carry a check mark when on.

## Feedback on v0.1.22

- The login screen says to type into the app's own fields, not into FA's page below them.
- The welcome screen stays until "Log in" is tapped (it used to open the login by itself).
- The viewer makes room for the image before it loads: FA's "Resolution" line gives the size, the 600 px
  preview shows in that space and the full image replaces it, so the page no longer jumps.
- Activity entries without an avatar on FA's page (journals) use the avatar under the user's name, and a
  phone notification about one person shows their avatar.

## Notes from Iteration 6

- **Forms (checked on a logged-in page, field names only):** comments post `#add_comment_form` to
  `/view/<id>/` or `/journal/<id>/` with `action=reply`, `replyto` empty and the text in `reply`; a
  reply is what FA's own script sends: `action=replyto` and the comment id in `replyto`. Shouts post
  `#JSForm` to `/user/<name>/` with `action=shout`, `key`, `name` and `shout` (255 characters at most).
  The app sends whatever hidden fields the page has, so a new one on FA's side is passed along.
- **Did it work?** It counts as posted once a comment or shout id shows up that wasn't on the page
  before; FA's answer is read first, then the page once more. Otherwise FA's message (or a hint to look
  again before posting twice) shows in the sheet, which stays open with the text.
- **Journals** open in the app (`/journal/<id>/`: title, author, rating, text, comments, form) from
  profiles, activity and any link.
- **+Fav from grids:** long-press a thumbnail in Explore, a profile or "more from" for +Fav and Save;
  in the inbox, select and tap the heart. Grids load the submission page for FA's fav link first. A
  small heart marks thumbnails the app knows are faved.
- **Test pages:** `journal.html` and the forms in `view-image-loggedin.html` and `user-page.html` are
  hand-built from that structure with made-up names; the demo keeps what is posted and shows it.

## Feedback on v0.1.25

- Profiles: the home tab no longer repeats recent submissions and favorites; the tabs have them.
- Tab labels stay on one line (with a large system font "Activity" wrapped).
- The long-press menu checks the submission's page first: faved ones offer "Remove from favorites".
- The viewer draws the picture like FA's page: a thumbnail already in memory at once, the 600 px preview
  over it, the full image over that, all without fades (the app-wide crossfade made it flicker). Only
  the next submission's page and preview are loaded ahead, not its full file.
- Details on submissions and profiles start folded to one line with a short summary.

## Uploads (after Iteration 6)

FA's own upload is three pages of forms. The app makes it one screen, and nothing waits on it.

- **Getting files in:** share one or several pictures (or a story, a song) from the gallery, a file
  manager or an editor straight to the app, or tap the upload button in the top bar and pick them.
  Files are copied into the app right away, so the source app can close.
- **One screen:** the files on top, then rating (required, three buttons), title, description with a
  small BBCode bar, keywords as chips, and folded "Category & species", "Folders" and "Options". Several
  files become one submission each (FA takes one file per submission) and share everything but their
  titles, covers, ratings and genders (each file can set its own rating and gender keywords, or follow
  the form's); a note on the form says so. A switch per file ("Same description, keywords and details")
  sets a file apart: it isn't sent with this form, and once the form's files are queued the form opens
  again for it, fresh, then for the next one set apart, until none are left; titles start from the file name ("my_new-pic.png" → "My new
  pic"). The type (artwork, story, poetry, music) follows the file; stories and music can get a cover.
- **FA's rules, checked before sending:** a title (60 characters at most), a rating, a description and at
  least 3 keywords. Keywords are cleaned to what FA takes (a-z, 0-9, `_`; "Café-Art" → `cafe_art`) and stop
  at FA's 500 characters. FA's gender boxes only add keywords (`male`, `trans_female`, …), so they are chips
  next to the keywords.
- **Remembered:** the last rating, category, theme, species, folders and options fill the next
  upload; keywords used before show up as suggestions. A half-filled form stays when you leave.
- **Background:** "Upload" queues the files and you're back where you were. WorkManager sends them one
  after another with a progress notification, also with the app closed; a finished upload's notification
  opens the submission. The upload button in the top bar shows progress; "Uploads" lists what's queued,
  done (open) or failed (FA's reason, retry or edit).
- **Files:** FA takes JPEG, PNG and GIF up to 10 MB for artwork. Other image types (WebP, HEIC, …) are
  converted, and pictures over 10 MB are re-encoded or scaled down until they fit. Location data is
  removed from photos (on by default, can be turned off).
- **How it talks to FA:** `GET /submit/` for the form's key, then a multipart `POST /submit/upload/` with
  `submission_type`, the file and the optional thumbnail (field names checked on the live page). FA
  answers with the details form; the app reads whatever fields that page has, keeps FA's defaults and
  fills in what was chosen: select options by value, else by their label; the rating by its label. FA's
  redirect to `/view/<id>/?upload-successful` means done; otherwise FA's message is the reason. The
  details form (`/submit/finalize/`, checked with a real test upload on 2026-11-05) has `key`, `cat`,
  `atype`, `species` (starting at "All" / "Unspecified / Any"), `rating` (0 General, 2 Mature, 1 Adult),
  `title`, `message`, `keywords`, `lock_comments`, `scrap`, `folder_ids[]` (under group headings),
  `create_folder_name` and the `finalize` button. The lists the app offers come from the browse page
  (the same lists) until the first upload shows FA's own (then those are kept).
- **Flood protection:** FA refuses an upload that follows the last one too closely ("Flood protection.
  Please wait 16 seconds before trying again."). The queue keeps 30 s between uploads, with a countdown
  on the upload, and when FA still says so it waits as long as FA asks and tries again (three times).
- **Cloudflare:** the background upload never shows a check; it stops with "Open the app to continue",
  and Retry in the app gets through the check first.
- **Test pages:** `submit.html` and `submit-finalize.html` are hand-built from the live structure with
  made-up names; the demo answers the upload with a new submission.

## App updates

Twice a day (WorkManager, and once at app start) the app asks GitHub's API for the latest release of
`Kambesembi/FA-Mobile` and compares its tag with its own version. A newer one gets one notification
(tap: opens the app); Settings → App updates shows the state, a "Look now" button and an off switch.
Nothing but that one GET is sent. While the repository is private GitHub answers 404, shown as "not
public yet".

With the app open (at start, from the notification or the settings button) a dialog asks "Update to
x?". On yes the release's APK downloads to the cache with its progress, is checked to be this package
with a higher versionCode, and goes to Android's `PackageInstaller` (`REQUEST_INSTALL_PACKAGES`). The
first time Android asks to allow installs from the app; each install shows Android's own confirmation
(from Android 12 later ones may skip it, `setRequireUserAction(NOT_REQUIRED)`). Android refuses an APK
signed with another key. "Later" doesn't ask about that version again until the app starts anew.
When the install fails or comes back "aborted" (also what Play Protect's scan or a phone's own guard
causes, not only "Cancel") the dialog shows Android's message and status number, and offers "Open in
Android's installer": the download goes by FileProvider (`<applicationId>.updates`, cache/update/ only)
to ACTION_VIEW, the same as opening the APK from the files app.

## Notes from Iteration 7

- **Comic reader:** "Read as comic" shows on a submission whose description has the artist's series
  links (First / Prev / Next / Latest, also «, », <<, >>, arrows) or whose title ends in a page number
  ("Name - Page 9", "Name 3/10", "Name part 3 of 10", "Name #4"; years 1900–2100 don't count). With
  links it walks Next forward, then Prev back (or First up to the start page); without them it searches
  `@title "Name" @lower artist` (up to 5 result pages) and orders the hits by number. 200 ms between
  pages, at most 150. Pages one under the other or one per swipe (kept in settings); tap to zoom, the
  label opens the page's submission. The demo has a made-up three-page comic (66950001–66950003).
- **Link warnings:** done in v0.1.29 (the app's own Open/Copy dialog instead of FA's page).
- **Languages:** English, German (du), Russian (вы, with one/few/many/other plurals). Android 13+ picks
  the language per app (`locales_config.xml`; Settings links to the system page); older phones follow
  the phone's language. FA's own words (categories, species, its error messages) stay in English, as
  FA sends them. Rating names (General, Mature, Adult) stay FA's.
- **Accessibility:** section titles are headings; switch rows toggle as a whole (TalkBack reads label,
  hint and state at once); cards say their rating (when not General) and whether they're selected;
  long-press and zoom actions are named.
- **Performance:** pages are parsed off the main thread (`FaClient.read`); the comic walk runs in the
  background; Coil keeps FA's images without asking the server again (their addresses change when the
  file does).
- **Wide screens:** from 600 dp (tablets, unfolded foldables, phones held sideways) the tabs sit in a
  rail at the side. Grid columns scale with the width (the setting counts for a phone held upright).
  Forms and text stop at 720 dp, the viewer and comics at 960 dp, centred.

## SFW mode and blocklist (v0.2)

Settings → Content switches SFW mode (off by default). While it is on, every grid (inbox, browse,
search, galleries, scraps, favorites, saved, "more from") shows General submissions only, swiping in the
viewer skips the hidden ones, and "Select all" in the inbox takes only what is shown. The switch also
sets FA's own SFW cookie, the way the website's toggle does, so the app and FA agree; after a new login
the app sets it again. A submission opened directly (a link, a notification) still opens.

The tag and user blocklist from the account's FA settings comes with every logged-in page (on `<body>`)
and every thumbnail carries its tags, so the grids hide blocked submissions just like the website
(FA's censor.js rules, "hide tagless" included). It is changed on the website only.

Grids the filter thins out page more slowly, and after three pages in a row that add nothing visible
they stop and offer "Load more" (with the hidden count), so a search the blocklist empties doesn't
page through FA until FA answers 503.

## Loading animation

Slow full-screen loads (a submission, a profile, the inbox, a journal, a note, a comic, the first
search page) show a spinner that circles once, then uncoils into a small line that zips around and
marks furry words as they pop up ("OwO", "rawr", ":3"...): underlined, wavy or circled, from the left
or the right. Each word pops with comic action lines before it fades. Short loads only see the spinner.

A very long pull-to-refresh (from the top of the screen to the bottom) slides the animation in
full-screen until a tap: a small easter egg.

FA's bare error pages ("Database Error", HTTP 200 without the site's header) are an error with a
retry, not an endless wait.

## FA settings page

Settings → FA settings: the user picks entries from FA's control pages (account, site, user settings,
profile, contacts; from blocked tags to the profile description) and gets their own page that shows
just those, read fresh from FA. A tap changes an entry: the app sends FA's own form as the page has it
(hidden fields included), with only that entry changed and the form's save button, then reads the page
again and reports the change only once FA shows it. Blocked tags are chips with an add field; each
change goes to `/route/tag_blocking` one tag at a time, as FA's own script sends it (with the page's
`data-tag-blocklist-nonce`, waiting out FA's `rate-limited` answers). Contacts are changed on FA
("Change on FA"), and so is disabling the account. Passwords, the account e-mail, the date of birth and the
PayPal address are not offered.

## v0.3.0

- A shout in Activity opens your own page, scrolled to that shout and marked.
- Back in the app after more than 3 minutes away: the counts and the tab on screen load again.
- FA's [center] and [right] text is aligned that way in descriptions, journals and comments.
- The version is 0.<fa.iteration>.<run - fa.runBase>, so each iteration starts at .0.
- Profiles show the user's own banner (FA's `<site-banner>` with a `profile_banner` image, the
  full-size one). FA's seasonal site banner is not shown. FA banners are 1850 x 300 at most, so the
  banner box has that shape from the start (cropped to fill) and nothing shifts when it loads.
- In Activity the name opens the user and the title (or the rest of the row) what it is about.
- Activity and Notes load again when shown if FA's count for them went up or they were last shown
  more than 2 minutes ago; the inbox only when its count went up.

## Favorites, storage

- Hearts on cards: what was faved in the app, plus the newest page of the account's FA favorites, read
  again on every inbox refresh, so favorites made on the website show too.
- Uploads: after a successful upload only a 256 px preview stays (for the uploads list), the last 20
  finished jobs at most; failed ones keep their files for a retry until removed. The image cache is
  capped at 150 MB; Android can clear it any time.

## Testing

- Raw pages saved from the account go to `local/fixtures/` (git-ignored). A scrub script writes
  anonymised copies (usernames, note/comment text, avatars, IDs of private items replaced) to
  `fixtures/`, and only those are committed. The parser unit tests run on those in CI.
- A **demo mode** that loads those saved pages, so CI can take screenshots on the emulator without logging in.
- Real-account testing happens on your phone with each APK.
