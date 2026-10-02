"""Read-only physical viewport probe on an existing disposable long note."""
import json
import re
import sys
from pathlib import Path
sys.path.insert(0, 'scripts')
from rc_ui import *

tap('RC-20260909-Long-mixed', 0)
time.sleep(.8)
results = []
for i, steps in enumerate([0, 4, 4, 4, 4, 5, 0, -5, -5, -5]):
    for _ in range(abs(steps)):
        adb('shell', 'input', 'swipe', '550', '1900' if steps > 0 else '650',
            '550', '650' if steps > 0 else '1900', '220')
    time.sleep(.3)
    ns = nodes()
    assert any(n.get('content-desc') == 'Edit' for n in ns)
    e = next(n for n in ns if n.get('content-desc') == 'Edit')
    x1,y1,x2,y2 = bounds(e)
    capture(f'cursor-repeat-{i+1}-reading')
    adb('logcat', '-c')
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    time.sleep(.7)
    capture(f'cursor-repeat-{i+1}-editor')
    adb('shell','input','tap','470', '1850' if i%2 else '850')
    time.sleep(1.3)
    log = adb('logcat','-d','-s','NoteViewport:D','*:S')
    Path(f'artifacts/rc-torture-20260909/cursor-repeat-{i+1}.log').write_text(log)
    rows = [tuple(map(int,(a,b,d)))+(c=='true',) for a,b,c,d in
            re.findall(r'scroll=(\d+) cursor=(\d+) focused=(true|false) ime=(\d+)', log)]
    focused = [r for r in rows if r[3]]
    assert focused, f'No body focus at iteration {i+1}'
    assert max(r[2] for r in focused)>500, 'Keyboard did not open'
    positions = [r[0] for r in focused]
    assert all(a<=b for a,b in zip(positions,positions[1:])), f'Overshoot/return {positions}'
    assert max(positions)-min(positions)<1300, f'Unexpected large movement {positions}'
    assert len(set(r[1] for r in focused))==1, 'Stale selection changed after focus'
    result = dict(iteration=i+1, reading_anchor=next((r[0] for r in reversed(rows) if not r[3]),None),
                  focus_scroll=positions[0], final_scroll=positions[-1], cursor=focused[-1][1],
                  max_ime=max(r[2] for r in focused),pass_motion=True)
    results.append(result)
    print(json.dumps(result),flush=True)
    capture(f'cursor-repeat-{i+1}-keyboard')
    back()
    back()
    time.sleep(.3)
Path('artifacts/rc-torture-20260909/cursor-repeat-results.json').write_text(json.dumps(results,indent=2))
