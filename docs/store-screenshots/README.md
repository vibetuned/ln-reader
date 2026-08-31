# Store screenshots

Regenerated per release. Six screens per set, in listing order: library, player, images,
timer, EPUB reader, EPUB search.

Play Store sets: `phone/` is 1080x1920 (9:16), `tablet10/` is 1600x2560 (1:1.6);
both are 24-bit PNG with no alpha, which is what Play accepts. Play's hard rule is that the
longer side may not exceed twice the shorter one — that is why the phone set is 1080x1920 and
not a modern 20:9 shape, which would be rejected at 2.22:1.

App Store sets (same six scenes, from the iOS port): `iphone/` is 1320x2868 — the required
6.9" size, captured on the iPhone 17 Pro Max simulator — and `ipad13/` is 2048x2732, the 13"
iPad size, from the iPad Air 13" (M4) simulator. Reproduction commands are at the bottom.

## Books used

Two of the four ln-vox demo books are featured, on purpose:

- **Alice's Adventures in Wonderland** — library / player / timer / reader / search. Its sync
  manifest puts five image markers on the player scrubber, and it is the only demo book whose
  EPUB carries enough `lnvox-beat` spans for reader auto-follow to highlight. (Oz has 102 spans
  against 1396 matched beats in its manifest; Flatland has 23 against 687. Auto-follow cannot
  engage for either.)
- **The Wonderful Wizard of Oz** — images only. Its embedded plates are opaque, so the grid
  reads richer than Alice's line art on the viewer's dark backing.

## Reproducing

Needs an `x86_64` `google_apis` system image (not `playstore` — the capture needs `adb root`).

```sh
# 1. AVDs, created once. Resolution/density are set in ~/.android/avd/<name>.avd/config.ini:
#      phone   hw.lcd.width=1080  hw.lcd.height=1920  hw.lcd.density=400
#      tablet  hw.lcd.width=1600  hw.lcd.height=2560  hw.lcd.density=360
#    Also set hw.initialOrientation=portrait, disk.dataPartition.size=16G, hw.mainKeys=no.
emulator -avd ln_phone -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -port 5554

# 2. Install both APKs. -g grants POST_NOTIFICATIONS so no permission dialog lands mid-capture.
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# 3. Push the demo books, then hand them to the app's uid — adb pushes them as root and the
#    app cannot read through root-owned directories on its own external storage.
DEST=/sdcard/Android/data/com.vibetuned.ln_reader/files/demo
for slug in alice oz flatland fairy-dreams; do
  adb shell mkdir -p $DEST/$slug
  adb push "<demo>/$slug/06_final/"*.m4b        $DEST/$slug/book.m4b
  adb push "<demo>/$slug/07_sync/"*.epub        $DEST/$slug/book.epub
  adb push "<demo>/$slug/07_sync/sync_manifest.json" $DEST/$slug/sync.json
done
UID=$(adb shell pm list packages -U | sed -n 's/.*com.vibetuned.ln_reader uid:\([0-9]*\).*/\1/p')
adb shell chown -R $UID:1078 /data/media/0/Android/data/com.vibetuned.ln_reader

# 4. Seed the library (androidTest fixture, never ships — see ScreenshotSeeder.kt).
adb shell am instrument -w -e class com.vibetuned.ln_reader.ScreenshotSeeder \
  com.vibetuned.ln_reader.test/androidx.test.runner.AndroidJUnitRunner

# 5. Dark theme, no animations, clean status bar (fixed 12:00 clock, full battery, wifi only).
adb shell cmd uimode night yes
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  adb shell settings put global $s 0
done
adb shell settings put global sysui_demo_allowed 1
D="adb shell am broadcast -a com.android.systemui.demo"
$D -e command enter
$D -e command clock -e hhmm 1200
$D -e command battery -e level 100 -e plugged false
$D -e command network -e wifi show -e level 4 -e fully true -e mobile hide
$D -e command notifications -e visible false

# 6. Capture, then strip alpha to satisfy Play's 24-bit-no-alpha requirement.
tools/capture.sh emulator-5554 phone
python3 -c "from PIL import Image; import glob; [Image.open(f).convert('RGB').save(f) for f in glob.glob('phone/*.png')]"
```

## Palette

Shot with the shipped default, `LnReaderTheme(dynamicColor = true)`, so the palette is Material
You derived from the device wallpaper — a pale periwinkle (`#B2C5FF` primary) on the stock
emulator wallpaper. There is no single "app colour" to reproduce: change the wallpaper and every
accent in these screenshots changes with it.

`Color.kt` also carries a brand palette mirrored from `ln.vibetuned.com`, wired up as
`DarkColorScheme` / `LightColorScheme`. Since `minSdk` is 33, `SDK_INT >= S` is true on every
supported device, so that scheme is never reached at runtime — it is the fallback only.

To seed the palette deliberately instead of taking whatever the wallpaper gives (this is the
mechanism behind Wallpaper & Style's "basic colours"):

```sh
adb shell "settings put secure theme_customization_overlay_packages \
  '{\"android.theme.customization.system_palette\":\"8C5CFF\",
    \"android.theme.customization.theme_style\":\"VIBRANT\"}'"
# styles: TONAL_SPOT (Android default), VIBRANT, EXPRESSIVE (rotates hue off the seed), SPRITZ
```

Undoing it takes more than deleting the key: `settings delete` drops the request but leaves the
`com.android.systemui:dynamic` runtime overlay enabled, which leaves a mismatched palette
(green primary against a maroon track). Restarting SystemUI does not reconcile it either.
Delete the key **and reboot** — that restored the wallpaper palette pixel-for-pixel here.

## Reproducing — iOS (`iphone/`, `ipad13/`)

Everything is driven by DEBUG-only launch arguments in the ln-reader-ios repo (see
`LnReaderApp.swift` and `ReaderViewModel.swift`); no test fixture ships in the app. Build
once, then each scene is a single `simctl launch`.

```sh
# 0. Simulators: iPhone 17 Pro Max (1320x2868) and iPad Air 13" M4 (2048x2732), English
#    locale, dark appearance, clean status bar.
xcodegen generate && xcodebuild -scheme LnReader -configuration Debug \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath build/dd build
xcrun simctl boot "$UDID"
xcrun simctl ui "$UDID" appearance dark
xcrun simctl status_bar "$UDID" override --time "9:41" --wifiBars 3 --cellularBars 4 \
  --batteryState charged --batteryLevel 100
xcrun simctl install "$UDID" build/dd/Build/Products/Debug-iphonesimulator/LnReader.app

# 1. Import the four demo books (order matters: date-added sort shows newest first).
#    -autoImport auto-attaches the sibling .epub and sync_manifest.json.
xcrun simctl launch "$UDID" com.vibetuned.lnreader \
  -autoImport "<demo>/fairy-dreams/….m4b" -autoImport "<demo>/flatland/….m4b" \
  -autoImport "<demo>/oz/….m4b"          -autoImport "<demo>/alice/….m4b"

# 2. Stage positions: Oz gets a visible progress bar; Alice parks on the Cheshire Cat
#    beat (6266 s — "Therefore I'm mad", beat 09_s5_b0024 in the sync manifest), which is
#    what the reader highlights in scene 5. Let each run ~10 s so the 5 s auto-save fires.
xcrun simctl launch "$UDID" com.vibetuned.lnreader -openTitle "the wonderful" -seekTo 3600 -play
xcrun simctl launch "$UDID" com.vibetuned.lnreader -openTitle alice -seekTo 6266 -play

# 3. One launch + `simctl io screenshot` per scene:
#    01  -openTitle alice -seekTo 6266 -play -tab library
#    02  -openTitle alice -seekTo 6266 -play
#    03  -openTitle "the wonderful" -play -tab images        (Oz's plates read best)
#    04  -openTitle alice -seekTo 6266 -play -armTimer 30 -tab timer
#    05  -openTitle alice -seekTo 6266 -play -readerDark -showReader
#    06  -openTitle alice -seekTo 6266 -play -readerDark -showReader -readerSearch Cheshire
```

Arming the timer asks for notification permission once per simulator; run the
`NotificationPermissionUITests` UI test first to answer the dialog (or pass
`-skipNotifAuth`, which suppresses the request entirely).
