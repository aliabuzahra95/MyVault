"""Run existing disposable presentation tests on the test emulator only."""
from pathlib import Path
import subprocess

adb = '/Users/aliah/Library/Android/sdk/platform-tools/adb'
out = Path(__file__).parent
for width in [360, 390, 430]:
    subprocess.run([adb, '-s', 'emulator-5554', 'shell', 'wm', 'size', f'{width}x892'], check=True, timeout=30)
    run = subprocess.run([adb, '-s', 'emulator-5554', 'shell', 'am', 'instrument', '-w', '-e', 'class',
        'com.myvault.app.ui.screens.ReflectionsPortDeviceTest',
        'com.myvault.app.test/androidx.test.runner.AndroidJUnitRunner'], capture_output=True, text=True, timeout=180)
    log = run.stdout + run.stderr
    (out / f'native-{width}-tests.log').write_text(log)
    print(f'{width}dp: {log[-160:]}', flush=True)
    if run.returncode != 0 or 'OK (4 tests)' not in log:
        raise SystemExit(f'{width}dp verification did not pass')
