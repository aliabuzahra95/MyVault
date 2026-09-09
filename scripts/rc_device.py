"""Portrait-only host driver for real release-app acceptance; never clears app data."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = "/Users/aliah/Library/Android/sdk/platform-tools/adb"
SERIAL = "RFCY70CMWZR"


def adb(*args):
    return subprocess.check_output([ADB, "-s", SERIAL, *args], text=True)


def nodes():
    adb("shell", "uiautomator", "dump", "/data/local/tmp/rc-ui.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/data/local/tmp/rc-ui.xml"))
    if root.attrib.get("rotation") != "0":
        raise RuntimeError("Portrait-only acceptance: unexpected rotation")
    return list(root.iter("node"))


def bounds(node):
    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=["dump", "tap", "hold", "text", "back", "home", "swipe", "launch"])
    parser.add_argument("value", nargs="*", default=[])
    parser.add_argument("--index", type=int, default=0)
    args = parser.parse_args()
    if args.action == "dump":
        print(json.dumps([{
            "text": n.get("text"), "description": n.get("content-desc"),
            "bounds": n.get("bounds"), "class": n.get("class"),
            "enabled": n.get("enabled"), "clickable": n.get("clickable"),
        } for n in nodes() if n.get("text") or n.get("content-desc") or n.get("class", "").endswith("EditText")], ensure_ascii=False, indent=2))
    elif args.action in ("tap", "hold"):
        label = " ".join(args.value)
        matches = [n for n in nodes() if label in (n.get("text"), n.get("content-desc"))]
        if len(matches) <= args.index:
            raise RuntimeError(f"Visible target not found: {label}")
        n = matches[args.index]
        if n.get("enabled") == "false":
            raise RuntimeError(f"Target disabled: {label}")
        x1, y1, x2, y2 = bounds(n)
        x, y = (x1 + x2) // 2, (y1 + y2) // 2
        if args.action == "tap":
            adb("shell", "input", "tap", str(x), str(y))
        else:
            adb("shell", "input", "swipe", str(x), str(y), str(x), str(y), "700")
        time.sleep(0.4)
        print(f"{args.action}: {label}")
    elif args.action == "text":
        value = " ".join(args.value)
        if not value.isascii():
            raise RuntimeError("Use a normal Share intent for Unicode fixture text")
        import shlex
        adb("shell", "input text " + shlex.quote(value.replace(" ", "%s")))
    elif args.action in ("back", "home"):
        adb("shell", "input", "keyevent", "4" if args.action == "back" else "3")
    elif args.action == "swipe":
        if len(args.value) != 5 or not all(v.isdigit() for v in args.value):
            raise RuntimeError("Expected x1 y1 x2 y2 duration")
        adb("shell", "input", "swipe", *args.value)
    elif args.action == "launch":
        print(adb("shell", "am", "start", "-W", "-n", "com.myvault.app/.MainActivity"))


if __name__ == "__main__":
    main()
