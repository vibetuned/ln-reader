#!/usr/bin/env python3
"""Tap and swipe an iOS device by driving Xcode's DeviceHub mirror — the iOS counterpart of
`ui.py`, which does the same job for Android through uiautomator.

  idev.py calib                        # measure the mirrored screen (run first, and after moving
                                       # or resizing the window)
  idev.py tap   <x> <y>                # device points, e.g. 50 822 for the first tab
  idev.py swipe <x1> <y1> <x2> <y2> [steps]

Why this exists: `simctl` has no input verbs, and Xcode 26 dropped Simulator.app, so a simulator
has no window of its own to click. DeviceHub mirrors the device in a normal macOS window, which
*can* be clicked — so coordinates are converted from device points to screen points and posted as
real mouse events.

Calibration finds the mirrored screen by its black bezel in a screenshot of the window, then takes
the **bottom** edge and derives the top from the device's aspect ratio. The top edge is unusable
directly: the Dynamic Island is a black island *inside* the screen, and in dark mode the app's own
surface is nearly as dark as the bezel, so "first bright row" lands too low. Getting this wrong is
silent — swipes still scroll something and only taps miss.

Requirements: DeviceHub open with the device mirrored (Xcode ▸ Window ▸ Devices), ImageMagick on
PATH, and pyobjc-framework-Quartz (`pip install pyobjc-framework-Quartz`, ideally in a venv).
Terminal needs Accessibility and Screen Recording permission, or the clicks and the screenshot
that calibration depends on both fail.

The device size below is an iPhone 17 Pro (402x874 pt); change `DEVICE_PT` for another device.
"""
import json, os, subprocess, sys, time
import Quartz

HERE = os.path.dirname(os.path.abspath(__file__))
CALIB = os.path.join(HERE, "idev_calib.json")

# Logical size of the mirrored device, in points. iPhone 17 Pro; an iPad or another phone needs
# its own values (and a fresh `calib`).
DEVICE_PT = (402.0, 874.0)


def window_rect():
    out = subprocess.run(
        ["osascript", "-e",
         'tell application "System Events" to tell process "DeviceHub" '
         'to get {position, size} of window 1'],
        capture_output=True, text=True).stdout.strip()
    x, y, w, h = [int(v) for v in out.split(", ")]
    return x, y, w, h


def calibrate():
    wx, wy, ww, wh = window_rect()
    shot = os.path.join(HERE, "_calib.png")
    subprocess.run(["screencapture", "-x", f"-R{wx},{wy},{ww},{wh}", shot], check=True)
    ident = subprocess.run(["magick", "identify", "-format", "%w %h", shot],
                           capture_output=True, text=True).stdout.split()
    cw, ch = int(ident[0]), int(ident[1])
    px = cw / ww  # capture pixels per window point (2 on retina)

    def scan(crop, axis):
        """axis 0 = walk x along a row, axis 1 = walk y down a column."""
        out = subprocess.run(["magick", shot, "-crop", crop, "+repage", "-depth", "8", "txt:-"],
                             capture_output=True, text=True).stdout.splitlines()[1:]
        vals = []
        for ln in out:
            xy, rest = ln.split(":", 1)
            coords = [int(v) for v in xy.split(",")]
            r, g, bb = [int(v) for v in rest.split("(")[1].split(")")[0].split(",")[:3]]
            vals.append((coords[axis], r + g + bb))
        return vals

    def dark_runs(vals, thresh=30, min_len=5):
        """Runs of (near-)black — the device bezel. The window chrome is dark grey but not black,
        so this isolates the phone frame rather than the surrounding UI."""
        runs = []; start = None; prev = None
        for coord, v in vals:
            if v <= thresh:
                if start is None: start = coord
                prev = coord
            else:
                if start is not None and prev - start + 1 >= min_len: runs.append((start, prev))
                start = None
        if start is not None and prev - start + 1 >= min_len: runs.append((start, prev))
        return runs

    def bracket(runs, centre):
        """The bezel run just before the centre and just after it; the screen lies between."""
        before = [r for r in runs if r[1] < centre]
        after = [r for r in runs if r[0] > centre]
        return (before[-1][1] + 1 if before else 0,
                after[0][0] - 1 if after else None)

    row = scan(f"{cw}x1+0+{ch // 2}", 0)
    left, right = bracket(dark_runs(row), cw // 2)
    if right is None: right = cw - 1
    # Down a column near the screen's left edge, clear of the Dynamic Island.
    col_x = int(left + (right - left) * 0.08)
    col = scan(f"1x{ch}+{col_x}+0", 1)
    # Take the BOTTOM edge and derive the top from the device's aspect ratio. The top edge is
    # unreliable: the Dynamic Island is a black island inside the screen, and in dark mode the
    # app's own surface is nearly as dark as the bezel, so "first bright row" lands too low.
    # The bottom bezel has neither problem.
    _, bottom = bracket(dark_runs(col), ch // 2)
    if bottom is None: bottom = ch - 1
    screen_h_px = (right - left) * (DEVICE_PT[1] / DEVICE_PT[0])
    top = bottom - screen_h_px

    screen_w_px = right - left
    scale = (screen_w_px / px) / DEVICE_PT[0]   # window points per device point
    data = {
        "origin_x": wx + (left + 1) / px,
        "origin_y": wy + (top + 1) / px,
        "scale": scale,
        "device_pt": list(DEVICE_PT),
    }
    json.dump(data, open(CALIB, "w"), indent=2)
    print(f"calibrated: origin=({data['origin_x']:.1f},{data['origin_y']:.1f}) "
          f"scale={scale:.4f}  screen={screen_w_px/px:.0f}x{(bottom-top)/px:.0f} window pt")
    return data


def load():
    if not os.path.exists(CALIB):
        return calibrate()
    return json.load(open(CALIB))


def to_screen(c, x, y):
    return c["origin_x"] + c["scale"] * x, c["origin_y"] + c["scale"] * y


def focus():
    subprocess.run(["osascript", "-e",
                    'tell application "System Events" to set frontmost of process "DeviceHub" to true'],
                   capture_output=True)
    time.sleep(0.25)


def post(kind, x, y):
    Quartz.CGEventPost(Quartz.kCGHIDEventTap,
                       Quartz.CGEventCreateMouseEvent(None, kind, (x, y), Quartz.kCGMouseButtonLeft))


def tap(c, x, y):
    sx, sy = to_screen(c, x, y)
    focus()
    post(Quartz.kCGEventMouseMoved, sx, sy); time.sleep(0.05)
    post(Quartz.kCGEventLeftMouseDown, sx, sy); time.sleep(0.09)
    post(Quartz.kCGEventLeftMouseUp, sx, sy)


def swipe(c, x1, y1, x2, y2, steps=16):
    ax, ay = to_screen(c, x1, y1)
    bx, by = to_screen(c, x2, y2)
    focus()
    post(Quartz.kCGEventMouseMoved, ax, ay); time.sleep(0.05)
    post(Quartz.kCGEventLeftMouseDown, ax, ay); time.sleep(0.10)
    for i in range(1, steps + 1):
        post(Quartz.kCGEventLeftMouseDragged, ax + (bx - ax) * i / steps, ay + (by - ay) * i / steps)
        time.sleep(0.016)
    time.sleep(0.06)
    post(Quartz.kCGEventLeftMouseUp, bx, by)


if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "calib":
        calibrate()
    elif cmd == "tap":
        tap(load(), float(sys.argv[2]), float(sys.argv[3]))
    elif cmd == "swipe":
        a = [float(v) for v in sys.argv[2:6]]
        swipe(load(), *a, int(sys.argv[6]) if len(sys.argv) > 6 else 16)
