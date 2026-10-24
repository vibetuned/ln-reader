# Store screenshots

Regenerated per release. Seven screens per set, in listing order: library, player, images,
sleep timer, EPUB reader, reader search, time spent.

Play Store sets: `phone/` is 1080x1920 (9:16), `tablet10/` is 1600x2560 (1:1.6); both are
24-bit PNG with no alpha, which is what Play accepts. Play's hard rule is that the longer side
may not exceed twice the shorter one — that is why the phone set is 1080x1920 and not a modern
20:9 shape, which would be rejected at 2.22:1.

App Store sets (same seven scenes, from the iOS port): `iphone/` is 1320x2868 — the required
6.9" size, captured on the iPhone 17 Pro Max simulator — and `ipad13/` is 2048x2732, the 13"
iPad size, from the iPad Air 13" (M4) simulator.

The website uses the phone and iPhone sets, downscaled to 760px wide, in
`docs/assets/shots/android/` and `docs/assets/shots/ios/`.

## Library

Six books: the four ln-vox demo audiobooks plus two EPUB-only imports, which is what makes the
library tile subtitles worth looking at — a duration for a book with audio, a page count for one
without.

Two books are featured in the other scenes, on purpose:

- **Alice's Adventures in Wonderland** — library, player, timer, reader, search and stats. Its
  sync manifest puts five image markers on the player scrubber, and it is the only demo book
  whose EPUB carries enough `lnvox-beat` spans for reader auto-follow to highlight. (Oz has 102
  spans against 1396 matched beats in its manifest; Flatland has 23 against 687. Auto-follow
  cannot engage for either.)
- **The Wonderful Wizard of Oz** — images only. Its embedded plates are opaque, so the grid
  reads richer than Alice's line art, which the viewer renders on its dark backing.

The usage log behind the "time spent" scene is generated, not real: `seedStats` writes several
hundred plausible sessions across the library, seeded off the date so a rerun produces the same
history and the charts stay comparable between releases.

## Tools

| script | what it does |
| --- | --- |
| `tools/prep.sh <serial>` | dark theme, animations off, demo status bar (12:00, full battery, wifi only) |
| `tools/seed.sh <serial> [demo-dir]` | wipes the app, stages the demo books, imports them, seeds the usage log, stages reading positions |
| `tools/capture.sh <serial> <out>` | the seven Android scenes |
| `tools/ios-prep.sh <udid> [demo-dir]` | the same for a simulator: boot, status bar, install, import, seed, stage |
| `tools/ios-capture.sh <udid> <out>` | the seven iOS scenes |
| `tools/ui.py` | resolves element coordinates from a live uiautomator dump (used by `capture.sh`) |
| `tools/idev.py` | taps and drags a *physical* iOS device through Xcode's Device Hub mirror |

Scenes are driven by DEBUG-only hooks rather than by tapping: Intent extras on Android
(`DebugLaunch.kt`) and launch arguments on iOS (`LnReaderApp.swift`, `ReaderViewModel.swift`).
The two sets use the same hook names, so the scene lists read almost identically. Android still
needs a couple of real gestures, because the Settings sections sit below the fold and the
sleep-timer controls only render once a book is loaded; iOS cannot gesture at all — `simctl` has
no input verbs and Xcode 26 dropped Simulator.app, so there is no window to send events to —
which is what `-settingsSection` in the app is for.

## Reproducing — Android

Needs a debuggable build; the demo books are staged through `run-as`, so no `adb root` and no
`androidTest` fixture.

```sh
# The automation build: debuggable, with the launch hooks, and its own app (.debug), so none of
# this touches the everyday install.
./gradlew :app:assembleAutomation
adb install -r -g app/build/outputs/apk/automation/app-automation.apk   # -g grants POST_NOTIFICATIONS

tools/prep.sh emulator-5554
tools/seed.sh emulator-5554 ~/demo

# Phone: 1080x1920 at 400dpi. Tablet: 1600x2560 at 360dpi. One AVD does both.
adb shell wm size 1080x1920 && adb shell wm density 400 && tools/prep.sh emulator-5554
tools/capture.sh emulator-5554 phone
adb shell wm size 1600x2560 && adb shell wm density 360 && tools/prep.sh emulator-5554
tools/capture.sh emulator-5554 tablet10
adb shell wm size reset && adb shell wm density reset

# Play wants 24-bit PNG with no alpha; screencap writes RGBA.
for f in phone/*.png tablet10/*.png; do magick "$f" -alpha off -define png:color-type=2 "$f"; done
```

`prep.sh` leaves demo mode *before* entering it. Entering on top of a session that is already
running stacks the icons it was told to show, and the status bar ends up with two or three wifi
glyphs side by side. The shield next to the clock is not ours and demo mode cannot clear it: it
is the system's own indicator on this Android 17 image.

## Reproducing — iOS

```sh
cd ../../ln-reader-ios
xcodegen generate
# The Automation configuration: Debug plus its own bundle ID (.debug), so none of this touches
# the everyday install.
xcodebuild -scheme LnReader -configuration Automation \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath build/dd build

cd ../ln-reader/docs/store-screenshots
PHONE=$(xcrun simctl list devices available | sed -n 's/.*iPhone 17 Pro Max (\([0-9A-F-]*\)).*/\1/p' | head -1)
PAD=$(xcrun simctl list devices available | sed -n 's/.*iPad Air 13-inch (M4) (\([0-9A-F-]*\)).*/\1/p' | head -1)
tools/ios-prep.sh "$PHONE" ~/demo && tools/ios-capture.sh "$PHONE" iphone
tools/ios-prep.sh "$PAD"   ~/demo && tools/ios-capture.sh "$PAD"   ipad13
```

`ios-prep.sh` passes `-skipNotifAuth`, which suppresses the notification request the sleep timer
would otherwise raise once per simulator.

## Website set

```sh
cd ..
for n in 01_library 02_player 03_images 04_timer 05_epubreader 06_epubsearch 07_stats; do
  magick store-screenshots/phone/$n.png  -resize 760x -quality 82 assets/shots/android/$n.webp
  magick store-screenshots/iphone/$n.png -resize 760x -quality 82 assets/shots/ios/$n.webp
done
```

## Palette

Shot with the shipped default, `LnReaderTheme(dynamicColor = false)` — the Athenaeum palette
from `visual-design.md`, the same greens the iOS app and the site use. Material You is still
wired up behind that flag, so passing `dynamicColor = true` would derive the scheme from the
device wallpaper instead and every accent in these screenshots would change with it; that is not
what ships, and not what these captures show.
