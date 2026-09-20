#!/usr/bin/env bash
# Wipe the app and rebuild the demo library the store screenshots are taken from.
#
# Usage: seed.sh <serial> [demo-dir]     (demo-dir defaults to ~/demo)
#
# Expects ln-vox output under <demo-dir>/books/<slug>/ — an .m4b beside its .epub and
# sync_manifest.json — plus any audio-less EPUBs under <demo-dir>/epubs/.
#
# Staging goes through `run-as` rather than a plain `adb push` into the app's external files
# dir: adb writes those as root, and the app then cannot traverse its own directory (EACCES).
# The redirect has to happen on the device, too — `run-as ... < file` from the host opens the
# file in the wrong process and lands a zero-byte result.
#
# Import order is reverse library order: the grid sorts newest first, so the last book imported
# is the first tile. Alice goes last because it is the book every other scene features.
set -euo pipefail

SERIAL="$1"
DEMO="${2:-$HOME/demo}"
PKG=com.vibetuned.ln_reader
FILES=files/demo

A() { adb -s "$SERIAL" "$@"; }

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

# stage <local file> <name under files/demo/>
stage() {
  local src="$1" dest="$2"
  A push "$src" /data/local/tmp/stage.bin >/dev/null
  A shell "run-as $PKG mkdir -p $FILES/$(dirname "$dest")"
  A shell "cat /data/local/tmp/stage.bin | run-as $PKG sh -c 'cat > $FILES/$dest'"
  A shell rm -f /data/local/tmp/stage.bin
}

echo "== clearing app data =="
A shell pm clear "$PKG" >/dev/null
# pm clear revokes runtime grants along with the data, and an ungranted POST_NOTIFICATIONS
# puts a system permission dialog on top of the first scene.
A shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS 2>/dev/null || true

IMPORTS=()

echo "== staging audiobooks =="
for slug in fairy-dreams flatland oz alice; do
  dir="$DEMO/books/$slug"
  [ -d "$dir" ] || { echo "  skip $slug (no $dir)"; continue; }
  m4b=$(find "$dir" -maxdepth 1 -iname '*.m4b' | head -1)
  epub=$(find "$dir" -maxdepth 1 -iname '*.epub' | head -1)
  sync="$dir/sync_manifest.json"
  [ -n "$m4b" ] || { echo "  skip $slug (no .m4b)"; continue; }
  stage "$m4b" "$slug/book.m4b"
  [ -n "$epub" ] && stage "$epub" "$slug/book.epub"
  [ -f "$sync" ] && stage "$sync" "$slug/sync_manifest.json"
  echo "  staged $slug"
done

echo "== staging EPUB-only books =="
n=0
for epub in "$DEMO"/epubs/*.epub; do
  [ -f "$epub" ] || continue
  n=$((n + 1))
  stage "$epub" "epub$n/book.epub"
  echo "  staged $(basename "$epub")"
done

# Audio first, then the EPUB-only pair, then Alice last so it heads the grid.
for slug in fairy-dreams flatland; do IMPORTS+=("/data/data/$PKG/$FILES/$slug/book.m4b"); done
for i in $(seq "$n"); do IMPORTS+=("/data/data/$PKG/$FILES/epub$i/book.epub"); done
for slug in oz alice; do IMPORTS+=("/data/data/$PKG/$FILES/$slug/book.m4b"); done

echo "== importing ${#IMPORTS[@]} books + seeding the usage log =="
amstart --esa autoImport "$(IFS=,; echo "${IMPORTS[*]}")" --ez seedStats true --ei seedDays 400
sleep 45

echo "== staging positions =="
# Progress bars in the library come from saved positions, so each book has to be opened once
# and left long enough for the 5 s autosave to fire. Alice parks on the Cheshire Cat beat
# (6266 s), which is the line the reader highlights in scenes 05 and 06.
open_and_wait() {
  amstart "$@"
  sleep 14
}
open_and_wait --es openTitle "the wonderful" --ei seekTo 3600 --ez play true
open_and_wait --es openTitle "flatland" --ei seekTo 1500 --ez play true
open_and_wait --es readTitle "the count" --ei readerPage 37
open_and_wait --es readTitle "frankenstein" --ei readerPage 11
open_and_wait --es openTitle alice --ei seekTo 6266 --ez play true
A shell am force-stop "$PKG"

# There is no sqlite3 on the emulator image, so read the library back on the host. The -wal
# and -shm files have to come along: Room runs in WAL mode and the newest rows live in the log,
# not the main database file.
TMP=$(mktemp -d)
for f in ln-reader.db ln-reader.db-wal ln-reader.db-shm; do
  A shell "run-as $PKG cat databases/$f" > "$TMP/$f" 2>/dev/null || true
done
echo "== library =="
sqlite3 "$TMP/ln-reader.db" \
  'select mediaKind, count(*), title from books group by title order by importedAt desc' 2>/dev/null || true
echo "== usage log =="
sqlite3 "$TMP/ln-reader.db" 'select count(*) || " sessions" from read_log' 2>/dev/null || true
rm -rf "$TMP"
echo "done"
