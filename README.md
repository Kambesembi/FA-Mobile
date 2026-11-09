# FA Mobile

An unofficial Android app for [Fur Affinity](https://www.furaffinity.net): light, quick and built around
the submission inbox, notifications and notes. Not affiliated with or endorsed by Fur Affinity.

## Features

- **Inbox:** the new-submissions grid with infinite scroll, swipe between submissions, zoom, fav,
  download, share, remove one, several or all.
- **Activity and notifications:** watches, comments, favorites, journals and shouts; a background check
  (every 30 minutes by default) sends phone notifications for what's new.
- **Notes:** folders, reading, writing and replying with a BBCode toolbar.
- **Profiles and explore:** galleries, scraps, favorites and journals; browse and search with FA's
  filters; watch artists and save artists or submissions for later.
- **Writing:** comments, replies, shouts, journal comments.
- **Uploads:** one or several files from the app or Android's share menu, with FA's full details form
  and a queue that keeps going in the background.
- **Comic reader:** a whole series on one screen, found from the artist's Prev/Next links or numbered
  titles.
- **SFW mode and blocklist:** a switch in Settings keeps Mature and Adult out of every grid and sets FA's own SFW mode; tags and users blocked on the website stay hidden in the app too.
- English, German and Russian; dark and light themes; tablet and foldable layout.

## Install

Download `FA-Mobile-<version>.apk` from the [latest release](../../releases/latest) and open it on the phone.
Android asks once to allow installs from your browser or file manager. Each new release installs over the
old one, and the app tells you when there is a new one.

## Login and privacy

You type your username and password into the app's login fields; the app puts them into Fur Affinity's own
login form, so the password goes only to furaffinity.net and is never stored. The app keeps only the session
cookies, on the phone, and logging out deletes them. It talks to furaffinity.net and, twice a day for the
update check, to GitHub's API (nothing is sent but that request; it can be turned off in Settings). Pages
load one at a time, like a person browsing.

## Building

Every push to `main` is built by GitHub Actions (`.github/workflows/build.yml`):

- unit tests on the saved pages in `fixtures/`
- a signed release APK, published as a GitHub release
- on request only (a manual run with "screens" ticked, or `[screens]` in the commit message):
  emulator screenshots of every screen, as the `screens` artifact. The debug build has a demo mode
  that reads `fixtures/` instead of the network, so no account is needed on CI.

Locally, Gradle 8.9 with JDK 17 and the Android SDK builds it: `gradle assembleDebug`.

Signing uses two repository secrets: `SIGNING_KEYSTORE_B64` (the PKCS#12 keystore in base64) and
`SIGNING_PASSWORD`. Without them the build still runs, but it signs with a throwaway key and doesn't publish
a release.

## Test pages

`fixtures/` holds only anonymised or hand-built pages with made-up names. Raw pages saved from a real
account belong in the git-ignored `local/` folder and must never be committed; `tools/scrub_fixture.py`
turns one into a safe test page.

See [PLAN.md](PLAN.md) for how the app was planned and built.

## License

None: the code is public to read, but all rights are reserved.
