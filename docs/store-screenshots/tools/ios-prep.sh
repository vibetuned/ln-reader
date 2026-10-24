#!/usr/bin/env bash
# Boot a simulator into screenshot condition and load it with the demo library.
#
# Usage: ios-prep.sh <udid> [demo-dir]     (demo-dir defaults to ~/demo)
#
# Everything runs through DEBUG-only launch arguments (see LnReaderApp.swift and
# ReaderViewModel.swift in the ln-reader-ios repo); no test fixture ships in the app.
set -euo pipefail

UDID="$1"
DEMO="${2:-$HOME/demo}"
# The Automation configuration (xcodebuild -configuration Automation) is its own app.
APP_ID=com.vibetuned.lnreader.debug
APP="${APP_PATH:-/Users/osf/pprojects/ln-reader-ios/build/dd/Build/Products/Automation-iphonesimulator/LnReader.app}"

S() { xcrun simctl "$@"; }
# launch <args...> — relaunch the app from scratch with debug hooks and let it settle.
launch() {
  S terminate "$UDID" "$APP_ID" >/dev/null 2>&1 || true
  sleep 1
  S launch "$UDID" "$APP_ID" "$@" >/dev/null
  sleep "${WAIT:-8}"
}

echo "== boot =="
S boot "$UDID" 2>/dev/null || true
S bootstatus "$UDID" -b >/dev/null 2>&1 || true
S ui "$UDID" appearance dark
# A fixed status bar: the real clock and a partial battery would differ in every shot.
S status_bar "$UDID" override --time "9:41" --wifiBars 3 --cellularBars 4 \
  --batteryState charged --batteryLevel 100

echo "== install =="
S uninstall "$UDID" "$APP_ID" >/dev/null 2>&1 || true
S install "$UDID" "$APP"

echo "== import =="
# Reverse library order: the grid sorts newest first, so the last book imported is the first
# tile. Alice goes last because it is the book every other scene features.
IMPORTS=()
for slug in fairy-dreams flatland; do
  m4b=$(find "$DEMO/books/$slug" -maxdepth 1 -iname '*.m4b' | head -1)
  [ -n "$m4b" ] && IMPORTS+=(-autoImport "$m4b")
done
for epub in "$DEMO"/epubs/*.epub; do
  [ -f "$epub" ] && IMPORTS+=(-autoImport "$epub")
done
for slug in oz alice; do
  m4b=$(find "$DEMO/books/$slug" -maxdepth 1 -iname '*.m4b' | head -1)
  [ -n "$m4b" ] && IMPORTS+=(-autoImport "$m4b")
done
WAIT=40 launch "${IMPORTS[@]}" -seedStats -seedDays 400 -skipNotifAuth

echo "== stage positions =="
# Library progress comes from saved positions, so each book is opened once and left long
# enough for the autosave. Alice parks on the Cheshire Cat beat (6266 s), the line the reader
# highlights in scenes 05 and 06.
WAIT=14 launch -openTitle "the wonderful" -seekTo 3600 -play -skipNotifAuth
WAIT=14 launch -openTitle "flatland" -seekTo 1500 -play -skipNotifAuth
# The reader hooks need longer than the player ones: the page mark is applied after the EPUB
# is parsed, and Monte Cristo has 122 spine items to get through.
WAIT=28 launch -openTitle "the count" -readerPage 37 -skipNotifAuth
WAIT=28 launch -openTitle "frankenstein" -readerPage 11 -skipNotifAuth
WAIT=14 launch -openTitle alice -seekTo 6266 -play -skipNotifAuth
S terminate "$UDID" "$APP_ID" >/dev/null 2>&1 || true
echo "done"
