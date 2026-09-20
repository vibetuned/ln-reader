#!/usr/bin/env bash
# Drive the iOS port through the store screens and capture each one.
#
# Usage: ios-capture.sh <udid> <output-dir>
#
# One `simctl launch` per scene, carrying DEBUG-only launch arguments (LnReaderApp.swift and
# ReaderViewModel.swift). Nothing is tapped: simctl has no input verbs and Xcode 26 dropped
# Simulator.app, so there is no window to send events to either — every scene has to be
# reachable from a cold launch. The library must already be loaded; see ios-prep.sh.
#
# Book casting matches the Android set: Alice everywhere except the image grid, which is Oz.
set -euo pipefail

UDID="$1"
OUT="$2"
APP_ID=com.vibetuned.lnreader
mkdir -p "$OUT"

S() { xcrun simctl "$@"; }
launch() {
  S terminate "$UDID" "$APP_ID" >/dev/null 2>&1 || true
  sleep 1
  S launch "$UDID" "$APP_ID" -skipNotifAuth "$@" >/dev/null
  sleep "${WAIT:-10}"
}
shot() { sleep "${2:-2}"; S io "$UDID" screenshot --type=png "$OUT/$1.png" >/dev/null; echo "  captured $1"; }

# Alice on the Cheshire Cat beat (6266 s — "Therefore I'm mad", beat 09_s5_b0024 in the sync
# manifest), which is the line the reader highlights in scenes 05 and 06.
ALICE=(-openTitle alice -seekTo 6266 -play)

echo "== 01 library =="
launch "${ALICE[@]}" -tab library
shot 01_library

echo "== 02 player =="
launch "${ALICE[@]}"
shot 02_player

echo "== 03 images =="
launch -openTitle "the wonderful" -play -showImages
shot 03_images 3

echo "== 04 timer =="
# The sleep timer sits below the usage chart, so -settingsSection scrolls to it; nothing here
# can swipe.
launch "${ALICE[@]}" -armTimer 30 -settingsSection timer
shot 04_timer

echo "== 05 reader =="
WAIT=18 launch "${ALICE[@]}" -readerDark -showReader
shot 05_epubreader

echo "== 06 search =="
WAIT=18 launch "${ALICE[@]}" -readerDark -showReader -readerSearch Cheshire
shot 06_epubsearch 4

echo "== 07 stats =="
launch "${ALICE[@]}" -tab settings
shot 07_stats

echo "done -> $OUT"
