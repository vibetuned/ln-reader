#!/usr/bin/env bash
# Drive ln-reader through the store screens and capture each one.
#
# Usage: capture.sh <serial> <output-dir>
#
# Every scene is one `am start` carrying DEBUG-only Intent extras (see DebugLaunch.kt) — the
# same hook-per-scene shape the iOS side uses, so the two sets stay in step. Scenes no longer
# tap their way through the app: tapping raced recompositions and drifted between the phone
# and tablet geometries. The two places a tap survives are the Settings sections, which sit
# below the fold and have to be scrolled into view; those resolve their coordinates live from
# uiautomator, so one script still drives 1080x1920 and 1600x2560 alike.
#
# The library must already be populated — see seed.sh, which imports the demo books and seeds
# the usage log.
#
# Book casting:
#   Alice - library / player / timer / reader / search / stats. Its sync manifest puts five
#           image markers on the player scrubber, and it is the only demo book whose EPUB
#           carries enough lnvox-beat spans for reader auto-follow to highlight.
#   Oz    - images only. Its embedded plates are opaque, so the grid reads richer than
#           Alice's line art, which the viewer renders on its dark backing.
set -euo pipefail

SERIAL="$1"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
PKG=com.vibetuned.ln_reader
mkdir -p "$OUT"

A() { adb -s "$SERIAL" "$@"; }
UI() { python3 "$HERE/ui.py" -s "$SERIAL" "$@"; }
shot() { sleep "${2:-3}"; A exec-out screencap -p > "$OUT/$1.png"; echo "  captured $1"; }

# adb hands the command line to the device's shell as one string, which re-splits it on
# whitespace — `--es openTitle "the wonderful"` arrives as two words and `wonderful` is taken
# for a package filter. Quote every argument for that second shell.
# -S force-stops the app first and -W waits for the launch to settle. The flags are
# NEW_TASK|CLEAR_TASK (0x10000000|0x00008000): without them the launcher task survives a
# force-stop, Android resumes it instead of creating the Activity, and the Intent arrives at
# onNewIntent — which nothing reads, so the scene's hooks silently do nothing.
amstart() {
  local cmd="am start -S -W -f 0x10008000 -n $PKG/.MainActivity" a
  # POSIX single-quote escaping: close the quote, emit an escaped one, reopen.
  for a in "$@"; do cmd+=" '$(printf '%s' "$a" | sed "s/'/'\\\\''/g")'"; done
  A shell "$cmd" >/dev/null
}

# launch <extras...> — cold-start the app with debug hooks and let it settle. The hooks are
# read in onCreate, so the previous process has to be gone or the Intent is ignored.
launch() {
  amstart "$@"
  sleep "${WAIT:-9}"
}

# The Alice beat the reader highlights in scenes 05/06: 6266 s, "Therefore I'm mad",
# beat 09_s5_b0024 in the sync manifest.
ALICE=(--es openTitle alice --ei seekTo 6266 --ez play true)

# screen_size — the size the app actually sees. `wm size` prints Physical first and Override
# second when one is set, and the store resolutions are set with an override; reading the first
# line puts swipe coordinates outside the display, where they do nothing at all.
screen_size() {
  A shell wm size | sed -n 's/.*: \([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -1
}

# wait_for <ui.py selector> [tries] — poll until the node exists. Hooks that touch playback
# wait on the media controller before they navigate, so a fixed sleep after `launch` is not
# enough to know which screen is up; scrolling before it arrives swipes at the player.
wait_for() {
  local sel="$1" tries="${2:-15}"
  for _ in $(seq "$tries"); do
    if UI find "$sel" >/dev/null 2>&1; then return 0; fi
    sleep 1
  done
  echo "  !! never saw $sel" >&2
  return 1
}

# drag_up <from-y> <to-y> — one scroll gesture, kept clear of the floating mini-player, which
# overlays the bottom of the screen and swallows any drag that starts on it. Anything below
# five eighths of the height is inside that overlay, and a swipe from there does nothing at all.
drag_up() {
  local w h from="$1" to="$2" ms="${3:-400}"
  read -r w h < <(screen_size)
  local ceiling=$((h * 5 / 8))
  if [ "$from" -gt "$ceiling" ]; then from=$ceiling; fi
  if [ "$to" -gt "$ceiling" ]; then to=$ceiling; fi
  A shell input swipe $((w / 2)) "$from" $((w / 2)) "$to" "$ms"
  sleep 1.5
}

# scroll_to <ui.py selector> — swipe up until the node is on screen (or give up after 6 tries).
scroll_to() {
  local sel="$1" h w
  read -r w h < <(screen_size)
  for _ in $(seq 6); do
    if UI find "$sel" >/dev/null 2>&1; then return 0; fi
    drag_up $((h * 5 / 8)) $((h / 5))
  done
  echo "  !! never found $sel" >&2
  return 1
}

# centre_on <selector> [divisor] — bring the section into view and park its heading that
# fraction of the way down the screen (a sixth by default: clear of the title bar, which the
# content scrolls under, while leaving most of the screen to the section itself).
# Iterative, because one gesture does not move the list by the distance it is dragged: the
# swipe ends in a fling and the list keeps travelling, so a single computed nudge over- or
# undershoots. Repeating until the heading is within tolerance converges in two or three goes.
centre_on() {
  local sel="$1" divisor="${2:-6}" x y w h target delta
  # "Downloads", not "Settings": the bottom navigation carries a Settings label on every
  # screen, so waiting on that name matches immediately and proves nothing.
  wait_for "text=Downloads" || true
  scroll_to "$sel" || return 0
  read -r w h < <(screen_size)
  target=$((h / divisor))
  for _ in 1 2 3 4; do
    if ! read -r x y < <(UI find "$sel" 2>/dev/null); then
      # Overshot far enough that the heading left the viewport; come back a third of a screen
      # and let the next pass measure again.
      drag_up $((h / 3)) $((h * 5 / 8)) 800
      continue
    fi
    delta=$((y - target))
    if [ "${delta#-}" -lt 60 ]; then return 0; fi
    # One formula both ways: a negative delta makes this a downward drag, which scrolls back up.
    # Slow, because a quick flick ends in a fling and the list sails past the mark.
    drag_up $((h / 2 + delta / 2)) $((h / 2 - delta / 2)) 800
  done
}

echo "== 01 library =="
launch "${ALICE[@]}" --es tab library
shot 01_library 4

echo "== 02 player =="
launch "${ALICE[@]}"
shot 02_player 5

echo "== 03 images =="
launch --es openTitle "the wonderful" --ez play true --ez showImages true
shot 03_images 6

echo "== 04 timer =="
# armTimer lands on Settings; the timer sits below Downloads and Time spent. Until the book
# is actually in the session the whole section is a single "Open a book" line, so wait for the
# mini-player transport before scrolling — otherwise there is nothing to scroll to.
launch "${ALICE[@]}" --ei armTimer 30
wait_for "desc=Back 10 seconds" || true
# On the section heading, not the first preset: starting at "Stop after time" puts the tail of
# the stats chart above it, and a sleep-timer shot that opens on a bar chart reads as neither.
centre_on "text=Sleep timer"
shot 04_timer 3

echo "== 05 reader =="
WAIT=16 launch "${ALICE[@]}" --ez readerDark true --ez showReader true
shot 05_epubreader 4

echo "== 06 search =="
WAIT=16 launch "${ALICE[@]}" --ez readerDark true --ez showReader true --es readerSearch Cheshire
# Focusing the search field raises the keyboard, which covers more than half the screen and
# most of the results. Back dismisses the keyboard only; the search and its results stay up.
A shell input keyevent KEYCODE_BACK
shot 06_epubsearch 4

echo "== 07 stats =="
launch "${ALICE[@]}" --es tab settings
# Higher up than the default, so the chart, the per-book legend and Details all fit below it.
centre_on "text=Time spent"
shot 07_stats 3

echo "done -> $OUT"
