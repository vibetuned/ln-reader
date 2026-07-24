# Changelog

## v1.3 — 2026-07-24

Adds collections, library sorting, an adjustable sleep-timer fade, and a dark
mode + text sizing for the EPUB reader.

### Play Store release notes (≤ 500 chars)

```
• Collections: group books into folders, shown as little cover shelves. Create one from +, open it to browse, and add/remove books from a book's menu.
• Sort your library by name or date added, ascending or descending — remembered across launches.
• Sleep-timer fade-out is now adjustable: Off, 10s, 30s, 1m or 5m.
• EPUB reader: dark mode and adjustable text size, both remembered.
```

### Library
- **Collections** — group books into folders. The **+** button now offers
  **Book** or **Collection**; naming a collection creates it. Open a collection
  to see its books, and its **+** adds books straight into it (no nesting).
  Each collection tile shows a small shelf of the covers inside, with the book
  count where a duration would be. Move a book in or out with **Add to
  collection** / **Remove from collection** in its detail sheet.
- **Deleting a collection** asks what to do with its books — **move them back to
  the library** or **delete them too**.
- **Sorting** — a sort control in the top bar orders books by **Name** or
  **Date added**, ascending or descending (tap the active field again to flip
  direction). The choice is **remembered across launches** and applies to the
  library and collections alike.

### Sleep timer
- **Adjustable fade-out** — the fade-out toggle is now a slider with **Off, 10s,
  30s, 1m and 5m** stops. The end-of-timer volume ramp scales to the length you
  pick (Off stops playback without fading).

### Reader
- **Dark mode** — a top-bar button toggles the EPUB reader between light and
  dark. Dark mode paints a dark page with light text while leaving
  illustrations and the audio-follow highlight intact.
- **Text size** — **A− / A+** buttons scale the reading font (80–250%). Both the
  theme and text size are **remembered across books and launches**.

## v1.2 — 2026-06-29

Adds a mini-player, per-book progress in the library, and a tappable chapter
selector.

### Play Store release notes (≤ 500 chars)

```
• New mini-player bar on every screen: play/pause, 10s/30s skips, and a Read button for EPUB books. Tap it to open the full player.
• Library covers now show how far you are through each book.
• Tap a book in the Library to start playing instantly.
• The chapter name and number are now one tappable chapter selector.
• More reliable resume — your last book reopens even after Android closes the app in the background.
```

### Player
- **Mini-player** — a compact playback bar now sits just above the bottom
  navigation on every screen except the full player. It shows the cover, title
  and author, a thin progress line, and skip-back 10 s / play-pause /
  skip-forward 30 s. Tap the bar to open the full player. When the current book
  has an attached EPUB, a **Read** button opens the reader (hidden while you're
  already in the reader). It appears whenever a book is loaded — playing or
  paused — and disappears when nothing is loaded.
- **Combined chapter selector** — the current chapter title and "Chapter N of
  M" are now stacked into a single control under the book title; tap it to open
  the chapter list. The standalone "Chapter N of M" line above the scrubber has
  been folded into it.
- **Resume on launch now survives memory eviction** — reopening your last book
  also works after Android shuts the app down in the background to reclaim
  memory, not only on a clean cold start. It reopens **paused** at your saved
  position, so launching never starts audio on its own — press play, or use the
  mini-player, to continue.

### Library
- **Per-book progress bars** — every cover now shows a thin progress bar of how
  far through the book you are, between the cover and the title. It updates live
  as you listen.
- **Tap to play** — tapping a book in the library now starts playback
  immediately; it previously opened the player paused.

## v1.1 — 2026-06-19

Player-experience and maintenance update.

### Play Store release notes (≤ 500 chars)

```
• Resumes your last audiobook on launch — straight back to where you left off.
• New media-notification controls: skip back 10s, skip forward 30s, and sleep at end of chapter.
• The sleep-timer alert is now silent, so it won't wake you.
• Smoother cloud imports, with a clearer "starting download" indicator.
• Polish and Android 15 compatibility fixes.
```

### Player
- **Resume on launch** — cold-starting the app reopens whatever book you were
  last listening to, at its saved position. The player is layered over the
  library, so Back still returns home, and rotating or reopening a screen
  won't yank you back to it.
- **Media-notification controls** — the system media / lock-screen notification
  now carries skip-back 10 s, skip-forward 30 s, and an end-of-chapter sleep
  button. The unused previous / next buttons (these are single-file books) are
  no longer shown.
- Fixed the sleep-timer (moon) icon in the player top bar rendering solid black
  when inactive; it now matches the other toolbar icons and only highlights
  while a timer is armed.

### Sleep timer
- The end-of-timer notification is now **silent** (low-importance channel), so
  it no longer plays a sound while you're trying to fall asleep. Its Postpone /
  Dismiss actions and shake-to-postpone are unchanged.

### Downloads
- While a cloud provider prepares a file, the import now shows an indeterminate
  **"Starting download…"** indicator instead of a progress bar frozen at 0 %,
  flipping to the byte-count bar once data actually starts arriving.
- Faster import start — the file name and size are fetched in a single metadata
  query (one network round-trip instead of two) before the download begins.

### Compatibility
- Removed an unused legacy UI dependency whose internals called the
  `setStatusBarColor` / `setNavigationBarColor` APIs deprecated in Android 15,
  clearing the Play Console deprecated-API warning. No visible change.

## v1.0 — 2026-05-31

First release of **ln-reader**, an Android audiobook player for `.m4b` files,
focused on light-novel audiobooks.

### Play Store release notes (≤ 500 chars)

```
Audiobook player for .m4b files, made for light-novel audiobooks.

• Full player: chapters, 0.5×–3× speed, lock-screen controls, auto-resume.
• Sleep timer by time or chapters with fade-out; shake to postpone.
• Embedded image viewer with pinch-zoom; auto-white backdrop for transparent line art.
• Drive / OneDrive imports are downloaded locally so playback never buffers.
• Optional EPUB + sync manifest: built-in reader follows the audio and highlights the current beat.

Requires Android 13+.
```

### Library
- Import `.m4b` files via SAF from device storage or any cloud
  `DocumentsProvider` (Drive, OneDrive, Dropbox, …).
- Grid of books with embedded cover art, title, author, and total duration.
- Book detail sheet shows chapter and image counts, and lets you attach
  optional EPUB + sync-manifest companions.

### Player
- Foreground media session — keeps playing in the background, controllable
  from the system media notification and the lock screen.
- Now-playing screen with cover, current chapter title, and a
  **chapter-relative scrubber** (time-in-chapter, not whole-book) plus
  "Chapter N of M".
- Transport: ±10 s / ±30 s skips, previous / next chapter, play / pause.
- Playback speed 0.5×–3×, pitch-preserving.
- Chapter list bottom sheet, auto-scrolling to the current chapter.
- Buffering spinner inside the play button while loading.
- Reading position auto-saves every 5 s while playing and on pause; resumes
  within ~5 s when you reopen the book.
- Re-entering the Player tab while a book is playing reattaches to the live
  session instead of showing an empty screen.

### Image viewer
- Grid of every image embedded in the m4b.
- Tap → full-screen pager. Pinch to zoom (up to 5×), pan when zoomed, swipe
  between images at rest.
- **Auto-detects transparent illustrations** and shows them on a white
  backdrop so dark line art stays visible. A contrast toggle flips between
  dark and light, and the choice is **remembered per book**.

### Sleep timer
- Time presets — 5 / 15 / 30 / 45 / 60 / 90 min. The countdown **freezes
  when you manually pause** and resumes when you hit play, so the timer
  counts play time, not wall time.
- Chapter mode — "end of current chapter" or +2 / +3 / +5 chapters from
  your current position.
- Linear 10-second volume fade-out at the tail (toggle).
- When the timer ends, a notification appears with **Postpone** (restart
  with the same config) and **Dismiss** actions.
- **Shake the device** to postpone, while the expired notification is up.

### Downloads
- Cloud imports (Drive, OneDrive, …) are detected by content authority and
  **copied locally during import**, so playback streams from disk instead
  of the network — no surprise buffering.
- Local imports are referenced in place, no copy.
- Phase-aware import progress:
  `Downloading: 12.3 MB / 245.0 MB` → `Parsing m4b…` → done.
- Settings → Downloads lets you point new downloads at any SAF tree folder
  (an SD card folder, etc.) or fall back to internal app storage. Existing
  books stay where they were.

### EPUB companion (optional)
- Attach an `.epub` and / or a `sync_manifest.json` to any book from its
  detail sheet — both are optional and combine independently.
- Built-in WebView reader reachable from the player top bar or the "Read"
  button in the book detail sheet.
- With sync attached, the reader **auto-follows the audio**: highlights
  the active `lnvox-beat` span, scrolls it into view, and turns pages as
  playback advances. Manual paging pauses follow; a contextual **Resume**
  button re-engages and jumps to the current beat.
- With sync attached, small **image markers** sit above the scrubber
  wherever an illustration is anchored. Tapping a marker opens the
  matching embedded m4b image.
- While the reader is open and the book is playing, the screen stays on.
  It sleeps again automatically once audio pauses — manually or because
  the sleep timer fired.

### Requirements
- Android 13 (API 33) or newer.
- A file manager or cloud app that exposes a `DocumentsProvider` for the
  source files. Google Drive, OneDrive, the system Files app, and most
  third-party file managers work.

### Known limits
- Google removed `ACTION_OPEN_DOCUMENT_TREE` from Drive's Android
  provider, so Drive **doesn't appear in the download-folder picker**.
  Single-file picking from Drive still works (that's the import flow).
- No reading-position sync between devices.
- No streaming — cloud books are fully downloaded at import time and use
  local storage until removed.
- Chapter parsing reads Nero `chpl` only; m4b files that store chapters
  as a QuickTime text track will load with an empty chapter list.
- The first embedded image is treated as the cover; other images are
  reachable from the viewer but aren't tied to specific chapters.
- **Scrubber image markers are matched to embedded m4b images by ordinal
  index** (manifest images[i] ↔ embedded image[i]). A marker only shows
  when the m4b has an image at that index.
- **Reader highlighting needs matching spans.** The EPUB must contain
  `<span class="lnvox-beat" data-beat-id="…">` elements whose ids match
  the sync manifest; beats without a matching span just don't highlight.
- Re-importing the same file creates a duplicate book (no content
  hashing).
- Interrupted downloads don't resume; the partial file is cleaned up and
  you re-import.
- The reader renders the EPUB's own CSS on a forced-white page; no
  dedicated dark reading theme yet.
