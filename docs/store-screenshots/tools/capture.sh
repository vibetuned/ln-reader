#!/usr/bin/env bash
# Drive ln-reader through the six store screens and capture each one.
# Usage: capture.sh <serial> <output-dir>
#
# Element coordinates are resolved live from uiautomator, so the same run works
# on the 1080x1920 phone and the 1600x2560 tablet.
#
# Book casting:
#   Alice - library / player / timer / reader / search. Its sync manifest puts five
#           image markers on the player scrubber, and it is the only demo book whose
#           EPUB carries enough lnvox-beat spans for reader auto-follow to highlight.
#   Oz    - images only. Its embedded plates are opaque, so the grid reads richer than
#           Alice's line art, which the viewer renders on its dark backing.
set -euo pipefail

SERIAL="$1"
OUT="$2"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$OUT"

A() { adb -s "$SERIAL" "$@"; }
UI() { python3 "$HERE/ui.py" -s "$SERIAL" "$@"; }
shot() { sleep "${2:-3}"; A exec-out screencap -p > "$OUT/$1.png"; echo "  captured $1"; }

play_book() {  # play_book "<content-desc of the cover>"
  UI tap text=Library >/dev/null; sleep 3
  UI tap "desc=$1" >/dev/null; sleep 5
  UI tap text=Open >/dev/null; sleep 10
}

echo "== reset app =="
A shell am force-stop com.vibetuned.ln_reader
A shell am start -n com.vibetuned.ln_reader/.MainActivity >/dev/null
sleep 8

echo "== Oz, for the image grid =="
play_book "The Wonderful Wizard of Oz"
UI tap text=Images >/dev/null
shot 03_images 5

echo "== Alice, for everything else =="
play_book "Alice's Adventures in Wonderland"

echo "== 02 player =="
UI tap text=Player >/dev/null
shot 02_player 5

echo "== 01 library =="
UI tap text=Library >/dev/null
shot 01_library 4

echo "== 04 timer =="
UI tap text=Timer >/dev/null; sleep 3
if UI find text=Cancel >/dev/null 2>&1; then UI tap text=Cancel >/dev/null; sleep 2; fi
UI tap "text=30 min" >/dev/null
shot 04_timer 4

echo "== 05 reader =="
UI tap desc=Read >/dev/null; sleep 14
# The reader's light/dark toggle is remembered per-install; force dark.
if UI find "desc=Dark mode" >/dev/null 2>&1; then
  UI tap "desc=Dark mode" >/dev/null; sleep 5
fi
shot 05_epubreader 4

echo "== 06 search =="
UI tap "desc=Search in book" >/dev/null; sleep 3
A shell input text "Cheshire"; sleep 2
A shell input keyevent KEYCODE_ENTER
shot 06_epubsearch 6

echo "done -> $OUT"
