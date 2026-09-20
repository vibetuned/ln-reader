#!/usr/bin/env bash
# Put a device into screenshot condition: dark theme, no animations, and a clean demo status
# bar (fixed 12:00 clock, full battery, wifi only, no notification icons). Usage: prep.sh <serial>
set -euo pipefail
SERIAL="$1"
A() { adb -s "$SERIAL" "$@"; }

A shell cmd uimode night yes >/dev/null
for s in window_animation_scale transition_animation_scale animator_duration_scale; do
  A shell settings put global "$s" 0
done
A shell settings put global sysui_demo_allowed 1
D() { A shell am broadcast -a com.android.systemui.demo "$@" >/dev/null; }
# Leave demo mode before entering it: re-entering on top of a session that is already running
# stacks the icons it was told to show, and the status bar ends up with two or three wifi
# glyphs side by side.
D -e command exit
sleep 1
D -e command enter
D -e command clock -e hhmm 1200
D -e command battery -e level 100 -e plugged false
D -e command network -e wifi show -e level 4 -e fully true -e mobile hide
D -e command notifications -e visible false
echo "prepped $SERIAL"
