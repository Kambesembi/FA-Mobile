#!/usr/bin/env bash
# Runs on the CI emulator: the real login page from the release build, then every screen in demo mode.
set -eux

PKG=com.kambesembi.famobile
OUT=screens
mkdir -p "$OUT"

shot() {
  sleep "${2:-3}"
  adb exec-out screencap -p > "$OUT/$1.png"
}

# Starts the app fresh, so the intent extras apply.
start() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" "$@"
}

# Taps the on-screen view whose given attribute matches, found in a fresh UI dump.
tap() {
  for attempt in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    adb exec-out cat /sdcard/ui.xml > ui.xml
    xy=$(python3 - "$1" "$2" <<'EOF' || true
import re, sys
attr, value = sys.argv[1:3]
xml = open("ui.xml", encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", xml):
    if re.search(rf'{attr}="{re.escape(value)}"', node, re.I):
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        print((a + c) // 2, (b + d) // 2)
        break
EOF
)
    if [ -n "$xy" ]; then
      adb shell input tap $xy
      sleep 1
      return 0
    fi
    sleep 5
  done
  echo "no view with $1=$2"
  return 1
}

# Long-presses the view whose attribute matches (tap's lookup, then a swipe that doesn't move).
press() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb exec-out cat /sdcard/ui.xml > ui.xml
  xy=$(python3 - "$1" "$2" <<'EOF' || true
import re, sys
attr, value = sys.argv[1:3]
xml = open("ui.xml", encoding="utf-8").read()
for node in re.findall(r"<node [^>]*>", xml):
    if re.search(rf'{attr}="{re.escape(value)}"', node, re.I):
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        print((a + c) // 2, (b + d) // 2)
        break
EOF
)
  [ -n "$xy" ] || { echo "no view with $1=$2"; return 1; }
  adb shell input swipe $xy $xy 900
  sleep 1
}

# Scrolls the screen content up by most of a screen.
scroll_down() {
  adb shell input swipe 540 1800 540 600 400
  sleep 1
}

# Taps the floating action button: the lowest clickable view in the right half above the tab bar.
# (Its label isn't exposed as text in the dump, so tap's lookup can't find it.)
tap_fab() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb exec-out cat /sdcard/ui.xml > ui.xml
  xy=$(python3 - <<'EOF' || true
import re
xml = open("ui.xml", encoding="utf-8").read()
best = None
for node in re.findall(r"<node [^>]*>", xml):
    if 'clickable="true"' not in node:
        continue
    a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
    if a > 500 and (c - a) < 500 and 1500 < b < 2250 and (best is None or b > best[1]):
        best = ((a + c) // 2, b + (d - b) // 2)
if best:
    print(best[0], best[1])
EOF
)
  [ -n "$xy" ] || { echo "no floating button"; return 1; }
  adb shell input tap $xy
  sleep 1
}

# Closes the on-screen keyboard if it is up (BACK without one would close the sheet instead).
hide_keyboard() {
  if adb shell dumpsys input_method | grep -q "mInputShown=true"; then
    adb shell input keyevent KEYCODE_BACK
    sleep 1
  fi
}

# Like tap, but the last matching view on screen (the form's button rather than the title above it).
tap_last() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  adb exec-out cat /sdcard/ui.xml > ui.xml
  xy=$(python3 - "$1" "$2" <<'EOF' || true
import re, sys
attr, value = sys.argv[1:3]
xml = open("ui.xml", encoding="utf-8").read()
found = None
for node in re.findall(r"<node [^>]*>", xml):
    if re.search(rf'{attr}="{re.escape(value)}"', node, re.I):
        a, b, c, d = map(int, re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node).groups())
        found = ((a + c) // 2, (b + d) // 2)
if found:
    print(found[0], found[1])
EOF
)
  [ -n "$xy" ] || { echo "no view with $1=$2"; return 1; }
  adb shell input tap $xy
  sleep 1
}

# Scrolls down until a view with exactly this text is on screen (at most eight times).
scroll_to_text() {
  for attempt in 1 2 3 4 5 6 7 8; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    if adb exec-out cat /sdcard/ui.xml | grep -q "text=\"$1\""; then return 0; fi
    scroll_down
  done
  echo "no text $1 after scrolling"
  return 1
}

# Waits until a view with the given attribute is on screen (the slow CI emulator can take 10 s and more).
wait_for() {
  for attempt in $(seq 1 30); do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    if adb exec-out cat /sdcard/ui.xml | grep -qi "$1=\"$2"; then return 0; fi
    sleep 2
  done
  echo "timed out waiting for $1=$2"
}

adb shell settings put global hide_error_dialogs 1

# Release build, logged out: FA's real login page (or Cloudflare's check) inside the app.
adb install -r app/build/outputs/apk/release/app-release.apk
start
# The welcome screen stays until the button is tapped.
shot 01-release-welcome 8
tap text "Log in to Fur Affinity" || true
shot 01-release-login 15
adb logcat -d -b crash > "$OUT/crash-release.txt" || true

# Debug build in demo mode: saved pages instead of the network.
adb install -r app/build/outputs/apk/debug/app-debug.apk
for mode in dark light; do
  if [ "$mode" = dark ]; then adb shell cmd uimode night yes; else adb shell cmd uimode night no; fi
  sleep 3
  start --ez demo true --ei tab 0
  wait_for content-desc "Submission 10000610"
  shot "10-$mode-inbox"
  # The viewer, opened from the inbox grid, then its details and comments further down.
  tap content-desc "Submission 10000610" || true
  wait_for text "1 of 7"
  shot "16-$mode-viewer"
  scroll_to_text "DETAILS" || true
  shot "17-$mode-viewer-details-folded" 1
  # Details start folded; open them.
  tap text "DETAILS" || true
  shot "17-$mode-viewer-details" 2
  scroll_down
  shot "18-$mode-viewer-comments" 1
  start --ez demo true --ei tab 1
  wait_for text "artist2 is now watching you"
  shot "11-$mode-activity"
  if [ "$mode" = dark ]; then
    # Swipe the first watch away, then clear the favorites.
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    adb exec-out cat /sdcard/ui.xml > ui.xml
    y=$(python3 -c "
import re
xml = open('ui.xml', encoding='utf-8').read()
m = re.search(r'text=\"artist2 is now watching you\"[^>]*bounds=\"\[\d+,(\d+)\]\[\d+,(\d+)\]\"', xml)
print((int(m.group(1)) + int(m.group(2))) // 2 if m else 600)
")
    adb shell input swipe 900 "$y" 100 "$y" 250
    shot "11-$mode-activity-swiped" 2
    tap text "Clear" || true
    shot "11-$mode-activity-clear-confirm" 1
    adb shell input keyevent KEYCODE_BACK
  fi
  start --ez demo true --ei tab 2
  wait_for text "Commission question"
  shot "12-$mode-notes"
  tap text "Commission question" || true
  wait_for text "From"
  shot "12-$mode-note" 2
  tap_fab || true
  shot "12-$mode-note-reply" 2
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  adb shell input keyevent KEYCODE_BACK
  start --ez demo true --ei tab 3
  wait_for content-desc "Browse 1"
  shot "13-$mode-explore"
  # A search, then its options.
  if tap class "android.widget.EditText"; then
    adb shell input text "fox"
    adb shell input keyevent KEYCODE_ENTER
    wait_for content-desc "Fox 1"
    shot "13-$mode-explore-search" 2
    # The first tap can land while the field still holds focus; tap again if the sheet isn't up.
    tap content-desc "Filters" || true
    sleep 2
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    if ! adb exec-out cat /sdcard/ui.xml | grep -q 'text="Search options"'; then
      tap content-desc "Filters" || true
    fi
    shot "13-$mode-explore-search-options" 2
    adb shell input keyevent KEYCODE_BACK
    sleep 1
    tap content-desc "Back to browsing" || true
    wait_for content-desc "Browse 1"
  fi
  tap content-desc "Filters" || true
  shot "13-$mode-explore-browse-filters" 2
  adb shell input keyevent KEYCODE_BACK
  sleep 1
  tap content-desc "Account menu"
  shot "14-$mode-account-menu" 1
  adb shell input keyevent KEYCODE_BACK
  start --ez demo true --es screen settings
  wait_for text "Theme"
  shot "15-$mode-settings"
  # A profile: home, then its gallery and journals tabs.
  start --ez demo true --es screen user/artist2
  wait_for text "Gallery"
  shot "19-$mode-profile" 2
  scroll_down
  tap text "DETAILS" || true
  shot "19-$mode-profile-more" 2
  tap text "Gallery" || true
  wait_for content-desc "Sketch 24"
  shot "19-$mode-profile-gallery" 2
  tap text "Journals" || true
  wait_for text "Commissions open!"
  shot "19-$mode-profile-journals" 2
done

# Profile interactions (dark only): watch, a gallery submission, the account sheet's own profile.
adb shell cmd uimode night yes
sleep 3
start --ez demo true --es screen user/artist2
wait_for text "Gallery"
tap text "Watch" || true
wait_for text "Unwatch"
shot 41-profile-watched 2
tap content-desc "Save artist" || true
shot 41-profile-saved 1
tap text "Gallery" || true
wait_for content-desc "Sketch 24"
shot 42-profile-gallery-top 1
tap content-desc "Sketch 24" || true
wait_for text "1 of"
tap text "Save" || true
shot 42-profile-gallery-viewer 2
adb shell input keyevent KEYCODE_BACK
sleep 1
tap text "Favs" || true
wait_for content-desc "Fan art 1"
shot 43-profile-favorites 2
start --ez demo true --ei tab 0
wait_for content-desc "Submission 10000610"
tap content-desc "Account menu"
tap text "My profile" || true
wait_for text "Gallery"
shot 44-own-profile 2
# The artist and submission saved above, on the Saved page.
start --ez demo true --es screen saved
wait_for text "Saved"
shot 45-saved-submissions 2
tap text "Artists (1)" || true
shot 46-saved-artists 2

# Writing (dark only): a comment and a reply in the viewer, a journal reply, a shout. Each runs in one
# app start, so the demo shows what was posted.
start --ez demo true --ei tab 0
wait_for content-desc "Submission 10000610"
tap content-desc "Submission 10000610" || true
wait_for text "1 of 7"
if scroll_to_text "Comment" && tap text "Comment"; then
  sleep 2
  adb shell input text "Lovely%scolours!"
  shot 50-comment-sheet 1
  hide_keyboard
  tap text "Post" || true
  sleep 3
  scroll_down
  shot 51-comment-posted 1
  tap text "Reply" || true
  sleep 2
  adb shell input text "Thank%syou!"
  shot 52-reply-sheet 1
  hide_keyboard
  tap text "Post" || true
  sleep 3
  shot 53-reply-posted 1
fi
start --ez demo true --es screen journal/11000003
wait_for text "Commissions open!"
shot 54-journal 2
if tap text "Reply"; then
  sleep 2
  adb shell input text "See%syou%sthere!"
  shot 55-journal-reply-sheet 1
  hide_keyboard
  tap text "Post" || true
  sleep 3
  shot 56-journal-replied 1
fi
start --ez demo true --es screen user/artist2
wait_for text "Gallery"
if scroll_to_text "Shout" && tap text "Shout"; then
  sleep 2
  adb shell input text "Great%sgallery!"
  shot 57-shout-sheet 1
  hide_keyboard
  tap text "Post" || true
  sleep 3
  shot 58-shout-posted 1
fi
# +Fav from grids: the long-press menu in Explore, and the inbox selection.
start --ez demo true --ei tab 3
wait_for content-desc "Browse 1"
press content-desc "Browse 1" || true
shot 59-quick-menu 1
tap text "Add to favorites" || true
shot 60-quick-faved 2
# Now faved: the menu offers to remove it instead.
press content-desc "Browse 1" || true
shot 62-quick-menu-faved 1
adb shell input keyevent KEYCODE_BACK
sleep 1
start --ez demo true --ei tab 0
wait_for content-desc "Submission 10000610"
press content-desc "Submission 10000610" || true
tap content-desc "Add selected to favorites" || true
shot 61-inbox-faved 3
adb logcat -d -b crash > "$OUT/crash-writing.txt" || true

# Uploads (light): the empty form, a picture in it, filled in, sent in the background, then opened.
adb shell cmd uimode night no
sleep 3
start --ez demo true --es screen upload
wait_for text "What would you like to upload?"
shot 70-upload-empty 2
start --ez demo true --es screen upload-demo
wait_for text "Evening sketch"
shot 71-upload-form 2
tap text "General" || true
if scroll_to_text "Tell people about it" && tap text "Tell people about it"; then
  sleep 1
  adb shell input text "A%swarm%sevening%ssketch."
  hide_keyboard
fi
if scroll_to_text "Type, then space or comma" && tap text "Type, then space or comma"; then
  sleep 1
  adb shell input text "sunset%sfox%ssketch%s"
  hide_keyboard
fi
shot 72-upload-filled 1
if scroll_to_text "CATEGORY &amp; SPECIES" && tap text "CATEGORY &amp; SPECIES"; then
  scroll_down
  shot 73-upload-category 1
fi
tap_last text "Upload" || true
shot 74-upload-started 2
tap text "Show" || tap content-desc "1 upload in progress" || true
shot 75-uploads-sending 2
wait_for text "Uploaded: tap to open"
shot 76-uploads-done 1
tap text "Open" || true
wait_for text "Evening sketch"
shot 77-uploaded-viewer 3
adb logcat -d -b crash > "$OUT/crash-upload.txt" || true

# Inbox interactions (dark only): fav, zoom, swipe to the next submission, selection, clear all.
adb shell cmd uimode night yes
sleep 3
start --ez demo true --ei tab 0
wait_for content-desc "Submission 10000610"
tap content-desc "Submission 10000610" || true
wait_for text "1 of 7"
tap text "Fav" || true
shot 30-viewer-faved 2
tap content-desc "Submission 10000610" || true
shot 31-viewer-zoom 2
adb shell input keyevent KEYCODE_BACK
sleep 1
wait_for text "1 of 7"
adb shell input swipe 900 700 150 700 250
wait_for text "2 of 7"
shot 32-viewer-next 2
adb shell input keyevent KEYCODE_BACK
sleep 2
shot 34-inbox-after-remove 1
press content-desc "Submission 10000610" || true
shot 35-inbox-selecting 1
tap content-desc "Select all" || true
shot 36-inbox-all-selected 1
tap content-desc "Close" || true
tap content-desc "Clear inbox" || true
shot 37-inbox-clear-confirm 1
tap text "Clear" || true
shot 38-inbox-cleared 2

# A furaffinity.net link from another app opens straight in the viewer.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --el view 10000610
wait_for text "Submission 10000610"
shot 39-link-viewer 2

# A comic page: "Read as comic" puts the whole series on one screen, then one page per swipe.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --el view 66950002
wait_for text "Demo Comic - Page 2"
shot 47-comic-page 1
scroll_to_text "Read as comic" || true
tap text "Read as comic" || true
wait_for text "3 pages" || true
shot 47-comic-strip 2
tap content-desc "One page per swipe" || true
shot 47-comic-swipe 2
tap content-desc "Pages one under the other" || true

# A tablet-sized screen (800 x 1280 dp): tabs in a side rail, more columns, forms kept readable.
adb shell wm size 1600x2560
adb shell wm density 320
start --ez demo true
wait_for content-desc "Submission 10000610" || true
shot 48-tablet-inbox 2
tap content-desc "Submission 10000610" || true
shot 48-tablet-viewer 3
adb shell input keyevent KEYCODE_BACK
tap content-desc "Account menu" || true
tap text "Settings" || true
shot 48-tablet-settings 2
adb shell wm size reset
adb shell wm density reset
sleep 2

# SFW mode: the grids keep General submissions only, until it is switched off again.
start --ez demo true
wait_for content-desc "Submission 10000610" || true
tap content-desc "Account menu" || true
tap text "Settings" || true
scroll_to_text "Safe for work" || true
tap text "Safe for work" || true
shot 49-sfw-settings 2
adb shell input keyevent KEYCODE_BACK
shot 49-sfw-inbox 2
tap content-desc "Account menu" || true
tap text "Settings" || true
scroll_to_text "Safe for work" || true
tap text "Safe for work" || true
adb shell input keyevent KEYCODE_BACK
sleep 1

# FA settings: pick what to show (all account settings, the profile text, blocked tags, contacts), then the page.
start --ez demo true
wait_for content-desc "Submission 10000610" || true
tap content-desc "Account menu" || true
tap text "Settings" || true
scroll_to_text "Choose what to show" || true
tap text "Choose what to show" || true
shot 60-fa-settings-picker 2
tap text "All" || true
scroll_to_text "Profile Description" && tap text "Profile Description" || true
scroll_to_text "Blocked Tags" && tap text "Blocked Tags" || true
scroll_to_text "Contacts and Social Media" && tap text "Contacts and Social Media" || true
shot 60-fa-settings-picked 1
tap text "Show selected (11)" || true
shot 60-fa-settings-page 3
scroll_down
shot 60-fa-settings-page-2 1
adb shell am force-stop "$PKG"

# The loading animation: submission pages made slow, shot while it spins, uncoils and underlines.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --ez demoSlow true
wait_for content-desc "Submission 10000610" || true
tap content-desc "Submission 10000610" || true
shot 59-loader-spin 0
sleep 2
shot 59-loader-word 0
sleep 2
shot 59-loader-word2 0
adb shell input keyevent KEYCODE_BACK
adb shell am force-stop "$PKG"

# Sample notifications from the background check, in the notification shade.
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.MainActivity" --ez demo true --ez demoNotify true
sleep 5
adb shell cmd statusbar expand-notifications
shot 40-notifications 3
adb shell cmd statusbar collapse
adb shell dumpsys jobscheduler | grep -c "$PKG" > "$OUT/jobs.txt" || true
adb logcat -d -b crash > "$OUT/crash-demo.txt" || true

# Logged-out demo: the screen behind the login, without the network.
start --ez demo true --es demoPage header-loggedout.html
wait_for text "Log in to Fur Affinity"
shot 20-demo-logged-out 1

# The login screen itself (plain Views, FA's real login page), then typing into the username field:
# the keyboard must stay up and every character must arrive.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/.ui.login.LoginActivity" --ez dark true
shot 21-login-page 15
# The app's own username field (its hint is its text while empty).
if tap text "Username"; then
  adb shell input text "abcdefgh"
  sleep 1
  adb shell input text "ijkl"
  shot 22-login-typing 2
  adb shell dumpsys input_method | grep -E "mInputShown|mIsInputViewShown" > "$OUT/ime-state.txt" || true
fi
adb logcat -d -b crash > "$OUT/crash-debug.txt" || true
