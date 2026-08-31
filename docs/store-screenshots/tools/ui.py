#!/usr/bin/env python3
"""Locate and tap UI nodes on a connected emulator by text / content-desc / resource-id.

Coordinates are resolved from a live uiautomator dump each time, so the same script
drives both the 1080x1920 phone and the 1600x2560 tablet without hard-coded taps.
"""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

SERIAL = None


def adb(*args, binary=False):
    cmd = ["adb"]
    if SERIAL:
        cmd += ["-s", SERIAL]
    cmd += list(args)
    out = subprocess.run(cmd, capture_output=True)
    if out.returncode != 0:
        raise SystemExit(f"adb {' '.join(args)} failed:\n{out.stderr.decode(errors='replace')}")
    return out.stdout if binary else out.stdout.decode(errors="replace")


def dump():
    """uiautomator dump, retried — it intermittently races a recomposition."""
    for attempt in range(6):
        adb("shell", "rm", "-f", "/sdcard/ui.xml")
        res = adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
        if "dumped to" in res:
            xml = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", "replace")
            if xml.strip().startswith("<?xml"):
                return ET.fromstring(xml)
        time.sleep(1.0)
    raise SystemExit("uiautomator dump failed after 6 attempts")


def bounds_center(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return (x1 + x2) // 2, (y1 + y2) // 2


def find(root, *, text=None, desc=None, rid=None, cls=None, exact=True, index=0):
    hits = []
    for node in root.iter("node"):
        if text is not None:
            v = node.get("text", "")
            if not (v == text if exact else text.lower() in v.lower()):
                continue
        if desc is not None:
            v = node.get("content-desc", "")
            if not (v == desc if exact else desc.lower() in v.lower()):
                continue
        if rid is not None and rid not in node.get("resource-id", ""):
            continue
        if cls is not None and cls not in node.get("class", ""):
            continue
        hits.append(node)
    if len(hits) <= index:
        return None
    return hits[index]


def main():
    global SERIAL
    args = sys.argv[1:]
    if args and args[0] == "-s":
        SERIAL = args[1]
        args = args[2:]
    cmd, rest = args[0], args[1:]

    kw = {}
    for token in rest:
        if "=" in token:
            k, v = token.split("=", 1)
            kw[k] = v
    if "exact" in kw:
        kw["exact"] = kw["exact"] not in ("0", "false", "no")
    if "index" in kw:
        kw["index"] = int(kw["index"])

    if cmd == "list":
        root = dump()
        for node in root.iter("node"):
            t, d, r = node.get("text", ""), node.get("content-desc", ""), node.get("resource-id", "")
            if t or d or r:
                print(f"{node.get('bounds')}  text={t!r} desc={d!r} id={r!r} cls={node.get('class')}")
        return

    root = dump()
    node = find(root, **kw)
    if node is None:
        raise SystemExit(f"NOT FOUND: {kw}")
    x, y = bounds_center(node)

    if cmd == "find":
        print(f"{x} {y}")
    elif cmd == "tap":
        adb("shell", "input", "tap", str(x), str(y))
        print(f"tapped {x},{y}")
    else:
        raise SystemExit(f"unknown command {cmd}")


if __name__ == "__main__":
    main()
