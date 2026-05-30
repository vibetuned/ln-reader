# Lectures and Narrations
Lectures and Narrations (LN) This project was born out of a small AI experiment in book understanding and knowledge presentation. By using language models to analyze narrative structures, identify character traits, and map scene dynamics, it quickly became clear that this deep-text comprehension could be repurposed. The experiment naturally evolved into a complete, end-to-end pipeline for creating highly immersive, multi-voice audiobooks.

Today, the Lectures and Narrations ecosystem consists of two halves:

> ln-vox (The Creator): An offline, self-hosted AI pipeline that ingests raw text or EPUBs. It orchestrates LLMs to cast characters and direct emotional delivery, then uses local TTS to generate a fully acted, multi-voice .m4b audiobook complete with text-to-audio sync markers.

> ln-reader (The Consumer): A custom Android application built specifically to play these enhanced audiobooks. It features chapter-relative scrubbing, sleep timers, and an EPUB companion reader that automatically highlights the text in sync with the audio.

## ln-reader in a nutshell

An Android audiobook player for `.m4b` files, focused on audiobooks. Not on the Play Store yet.

## Features

### Library
- Import `.m4b` files from any SAF source (device storage, Drive, OneDrive, Dropbox, …).
- Grid view with embedded cover art, title, author, and total duration.
- Tap a book to see chapter and image counts plus the Open / View images / Remove actions.

### Player
- Foreground media session — keeps playing in the background, controllable from the system media notification and lock screen.
- Now-playing screen with cover, chapter title, **chapter-relative scrubber** (shows time-in-chapter, not whole-book), and "Chapter N of M" line.
- Transport: ±10 s / ±30 s skips, previous / next chapter, play / pause.
- Playback speed presets from 0.5× to 3×, pitch-preserving.
- Chapter list bottom sheet, auto-scrolls to the current chapter.
- Buffering spinner inside the play button while ExoPlayer is loading.
- Reading position auto-saves every 5 s while playing and on each pause; resumes within ≈ 5 s on next open.
- Re-entering the Player tab while a book is playing reattaches to the live session instead of showing an empty screen.

### Image viewer
- Grid of every image embedded in the m4b.
- Tap → full-screen pager. Pinch to zoom (up to 5×), pan when zoomed, swipe between images at rest.
- Reachable from the Library detail sheet, the Player top bar, or the bottom-nav Images tab (which picks up the currently-playing book).

### Sleep timer
- Two modes:
  - **Time** — 5 / 15 / 30 / 45 / 60 / 90 min presets. The countdown freezes when you manually pause and resumes when you press play.
  - **Chapters** — "end of current chapter" or +2 / +3 / +5 chapters from your position.
- Linear volume fade-out over the last 10 seconds (toggle).
- When the timer fires it pauses playback and posts a notification with **Postpone** (restart the same timer) and **Dismiss** actions.
- **Shake-to-postpone** while the expired-state notification is up — accelerometer-driven, ignored when the timer isn't pending.
- Reachable from the player's top bar (bottom sheet) or the bottom-nav Timer tab (full screen).

### Downloads
- Cloud SAF imports (Drive, OneDrive, etc.) are detected by content authority and **copied locally during import** so playback streams from disk, not the network.
- Local SAF imports are referenced in place — no copy.
- Import progress is phase-aware: `Downloading: 12.3 MB / 245.0 MB` → `Parsing m4b…` → done.
- Settings → Downloads lets you point new downloads at any SAF tree folder (e.g. an SD card folder), or fall back to internal app storage. Existing books stay where they were.

### EPUB companion + sync
Each book can have two optional companions, attached from its detail sheet:
- **EPUB** — a built-in WebView reader, reachable from the player top bar or the book's "Read" button. Manual page turning, white page background.
- **Sync manifest** (`sync_manifest.json`) — ties audio timestamps to EPUB beats and illustration positions.

What they unlock, depending on what's attached:

| EPUB | sync | Result |
|------|------|--------|
| ✓ | ✓ | Reader **auto-follows** the audio: highlights the active `lnvox-beat` span, scrolls to it, turns pages as playback advances. Scrubber shows tappable image markers. |
| ✓ | ✗ | Plain reader, manual paging only. |
| ✗ | ✓ | Image markers on the scrubber only (no reader). |
| ✗ | ✗ | Plain audiobook. |

- In auto-follow mode, manually flipping a page pauses following; a **Resume** button re-engages it and jumps to the current beat.
- Scrubber **image markers** sit at each manifest image's position (within the current chapter); tapping one opens the matching embedded m4b image.

## Requirements

- Android 13 or newer (minSdk 33, targetSdk 36).
- A file manager / cloud app that exposes a `DocumentsProvider` for the source of your `.m4b` files. Google Drive, OneDrive, the system Files app, and most third-party file managers work.

## Constraints / known limits

- **Drive folder picking does not work** for the download-location setting. Google removed `ACTION_OPEN_DOCUMENT_TREE` support from the Drive Android app. Single-file picking from Drive still works (that's the import flow).
- **No reading-position sync across devices.** A SAF-based sync feature was prototyped and removed; see [design.md](design.md).
- **No streaming** — cloud books are fully downloaded at import time. A 300 MB audiobook takes the time the import bar shows, and uses 300 MB of local storage until you remove the book.
- **Chapter parsing is Nero `chpl` only** (the format most m4b creators write). Files that store chapters as a QuickTime text track will load but show an empty chapter list.
- The first embedded image (`covr` data atom) is treated as the cover. Other embedded images are exposed in the viewer but not tied to specific chapters.
- **Scrubber image markers are matched to embedded m4b images by ordinal index** (1st manifest image ↔ 1st embedded image, …). A marker only appears if the m4b actually has an image at that index; manifest `src` paths are not resolved from the EPUB.
- **Reader highlighting needs matching spans.** The EPUB must contain `<span class="lnvox-beat" data-beat-id="…">` elements whose ids match the sync manifest; beats without a matching span just don't highlight (no error).
- The reader renders the EPUB's own CSS on a forced-white page; there's no dedicated dark reading theme yet.
- **Re-importing the same file creates a duplicate book** (UUID-keyed library, no content hashing).
- **No resume for interrupted downloads.** If you kill the app mid-download, the partial file is cleaned up and you need to re-import.
- **Single-device only.** The app stores everything in its own database / file storage; uninstalling drops your library and saved positions.

## Building

JDK 21 required. From the project root:

```sh
./gradlew assembleDebug
```

The debug APK lands in `app/build/outputs/apk/debug/`. Install with `adb install`.

Architecture, design decisions, and tooling quirks live in [design.md](design.md).
