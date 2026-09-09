"""Small helpers for serial, visible-UI acceptance on the connected Samsung."""
from pathlib import Path
import shlex
import subprocess
import time
from rc_device import ADB, SERIAL, adb, bounds, nodes


def tap(label, index=0):
    matched = [n for n in nodes() if label in (n.get("text"), n.get("content-desc"))]
    click_node(matched[index])


def click_node(node):
    assert node.get("enabled") != "false"
    x1, y1, x2, y2 = bounds(node)
    assert x2 > x1 and y2 > y1
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.3)


def field(index=0):
    click_node([n for n in nodes() if n.get("class", "").endswith("EditText")][index])


def text(value):
    assert value.isascii(), "Use normal Share for Unicode"
    adb("shell", "input text " + shlex.quote(value.replace(" ", "%s")))


def back():
    adb("shell", "input", "keyevent", "4")
    time.sleep(.3)


def visible():
    for n in nodes():
        value = n.get("text") or n.get("content-desc")
        if value or n.get("class", "").endswith("EditText"):
            print(n.get("bounds"), n.get("class").split(".")[-1], repr(value))


def capture(name):
    output = subprocess.check_output([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"])
    signature = bytes.fromhex("89504e470d0a1a0a")
    path = Path("artifacts/rc-torture-20260909") / (name + ".png")
    path.write_bytes(output[output.index(signature):])
    return path


def root(name):
    tap("Open navigation")
    tap(name)


if __name__ == "__main__":
    visible()
