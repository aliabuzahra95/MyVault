"""Exercise the installed release UI, not replacement Compose screens or a mock graph."""
import json
from pathlib import Path
import re
import subprocess
import time
from rc_device import ADB, SERIAL, adb, bounds, nodes

OUT = Path("artifacts/rc-torture-20260909")
OUT.mkdir(parents=True, exist_ok=True)
results = []


def record(name, **details):
    results.append({"workflow": name, **details})
    (OUT / "startup-navigation.json").write_text(json.dumps(results, indent=2))
    print(json.dumps(results[-1]), flush=True)


def tap(label):
    options = [n for n in nodes() if label in (n.get("text"), n.get("content-desc"))]
    if not options:
        raise RuntimeError(f"Missing visible target: {label}")
    x1, y1, x2, y2 = bounds(options[0])
    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(0.3)


def assert_screen(label):
    current = nodes()
    texts = {n.get("text") for n in current} | {n.get("content-desc") for n in current}
    if "Close navigation" in texts or label not in texts:
        raise AssertionError(f"Expected {label}; drawer still open or destination missing")


def capture(name):
    output = subprocess.check_output([ADB, "-s", SERIAL, "exec-out", "screencap", "-p"])
    # Samsung may prefix the binary stream with a multiple-display warning.
    signature = bytes.fromhex("89504e470d0a1a0a")
    (OUT / f"{name}.png").write_bytes(output[output.index(signature):])


try:
    for cycle in range(3):
        adb("shell", "am", "force-stop", "com.myvault.app")
        started = time.monotonic()
        launch = adb("shell", "am", "start", "-W", "-n", "com.myvault.app/.MainActivity")
        assert_screen("Dashboard")
        record("cold launch", cycle=cycle+1, result="PASS",
               activity_ms=int(re.search(r"TotalTime: (\d+)", launch).group(1)),
               dashboard_observed_within_ms=round((time.monotonic()-started)*1000))
        if cycle == 0:
            capture("release-dashboard")
        adb("shell", "input", "keyevent", "3")
        time.sleep(1)
        launch = adb("shell", "am", "start", "-W", "-n", "com.myvault.app/.MainActivity")
        assert_screen("Dashboard")
        record("home and resume", cycle=cycle+1, result="PASS", launch=launch.strip())
    sequence = ["Study", "Library", "Qur'an", "Dashboard", "Courses", "Search", "Memorise", "Settings"]
    for cycle in range(3):
        for destination in sequence:
            tap("Open navigation")
            tap(destination)
            assert_screen("Qur'an options" if destination == "Qur'an" else destination)
            if cycle == 0:
                capture("root-" + destination.replace("'", ""))
            record("drawer switch", cycle=cycle+1, destination=destination, result="PASS")
        adb("shell", "input", "keyevent", "4")
        assert_screen("Dashboard")
        record("root Back", cycle=cycle+1, result="PASS")
    print("PASS: real-release startup/navigation sequence", flush=True)
except Exception as error:
    capture("navigation-failure")
    record("sequence stopped", result="FAIL", error=str(error))
    raise
