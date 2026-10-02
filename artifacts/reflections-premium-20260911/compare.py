"""Factual screenshot comparison; never modifies approved source images."""
from pathlib import Path
import sys
from PIL import Image, ImageDraw

native = Path(sys.argv[1])
reference = Path('/Users/aliah/Documents/Codex/2026-08-25/i-have-a/work/myvault-ui-prototype/design-master/reflections-premium-proposed')
output = Path(__file__).parent / 'comparisons'
output.mkdir(exist_ok=True)
for state in ['01-light', '02-dark', '03-oled', '04-surah-sheet', '05-al-baqarah']:
    approved = Image.open(reference / f'{state}.png').convert('RGB')
    android = Image.open(native / f'412-{state}.png').convert('RGB')
    # Normalise pixel density only. Keep native status/navigation insets visible.
    android = android.resize((412, round(android.height * 412 / android.width)))
    canvas = Image.new('RGB', (824, max(approved.height, android.height) + 28), '#e8edf3')
    canvas.paste(approved, (0, 28))
    canvas.paste(android, (412, 28))
    draw = ImageDraw.Draw(canvas)
    draw.text((12, 8), 'Approved mockup', fill='#172238')
    draw.text((424, 8), 'Android emulator - system insets retained', fill='#172238')
    canvas.save(output / f'{state}-side-by-side.png')
