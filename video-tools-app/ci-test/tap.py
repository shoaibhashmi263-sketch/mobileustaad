#!/usr/bin/env python3
"""UI-automation helper for the Video Tools emulator test.

Usage:
  tap.py find <ui.xml> <needle>   -> prints "x y" of first node whose text or
                                     content-desc contains <needle> (case-insensitive)
  tap.py edit <ui.xml> <n>        -> prints "x y" of the n-th (0-based)
                                     android.widget.EditText node
  tap.py check <ui.xml> <needle>  -> exit 0 if needle found in any text/desc
Always exits 0 for find/edit (empty output = not found).
"""
import sys
import xml.etree.ElementTree as ET


def center(bounds):
    # bounds like "[x1,y1][x2,y2]"
    parts = bounds.replace("][", ",").strip("[]").split(",")
    x1, y1, x2, y2 = (int(p) for p in parts)
    return f"{(x1 + x2) // 2} {(y1 + y2) // 2}"


def main():
    mode, path = sys.argv[1], sys.argv[2]
    arg = sys.argv[3] if len(sys.argv) > 3 else ""
    try:
        root = ET.parse(path).getroot()
    except Exception:
        return
    nodes = list(root.iter("node"))
    if mode == "find" or mode == "check":
        needle = arg.lower()
        for n in nodes:
            hay = ((n.get("text") or "") + "|" + (n.get("content-desc") or "")).lower()
            if needle and needle in hay:
                if mode == "find":
                    print(center(n.get("bounds")))
                else:
                    sys.exit(0)
                return
        if mode == "check":
            sys.exit(1)
    elif mode == "edit":
        idx = int(arg)
        edits = [n for n in nodes if (n.get("class") or "") == "android.widget.EditText"]
        if 0 <= idx < len(edits):
            print(center(edits[idx].get("bounds")))


if __name__ == "__main__":
    main()
