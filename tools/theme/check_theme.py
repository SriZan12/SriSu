#!/usr/bin/env python3
"""Theme contract audit: run from any directory with Python 3, no dependencies."""
from pathlib import Path
import json
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'composeApp/src'
COLOR = SOURCE / 'commonMain/kotlin/com/srisu/srisu/theme/Color.kt'
text = COLOR.read_text()
errors = []
for path in SOURCE.rglob('*.kt'):
    if 'theme' in path.parts:
        continue
    for number, line in enumerate(path.read_text().splitlines(), 1):
        if re.search(r'\bColor\s*\(|\bColor\.\w+|\b\d+(?:\.\d+)?\.sp\b|\bRoundedCornerShape\s*\(|\bFontFamily\.', line):
            errors.append(f'{path.relative_to(ROOT)}:{number}: raw visual token: {line.strip()}')

spec = json.loads((ROOT / 'docs/design-system/figma-spec.json').read_text())
figma_hex = set(re.findall(r'#[0-9a-fA-F]{6}\b', json.dumps(spec)))
figma_hex = {h[1:].upper() for h in figma_hex}
implemented_hex = set(re.findall(r'0xFF([0-9A-F]{6})', text))
for missing in sorted(figma_hex - implemented_hex):
    errors.append(f'Missing documented Figma swatch #{missing}')

values = {name: value for name, value in re.findall(r'internal val (\w+) = Color\(0xFF([0-9A-F]{6})\)', text)}
def luminance(value):
    channels = [int(value[i:i+2], 16) / 255 for i in (0, 2, 4)]
    linear = [v / 12.92 if v <= 0.04045 else ((v + 0.055) / 1.055) ** 2.4 for v in channels]
    return sum(v * w for v, w in zip(linear, (0.2126, 0.7152, 0.0722)))
def contrast(a, b):
    x, y = sorted((luminance(a), luminance(b)))
    return (y + 0.05) / (x + 0.05)

roles = [('Primary', 'OnPrimary'), ('PrimaryContainer', 'OnPrimaryContainer'),
         ('Secondary', 'OnSecondary'), ('SecondaryContainer', 'OnSecondaryContainer'),
         ('Tertiary', 'OnTertiary'), ('TertiaryContainer', 'OnTertiaryContainer'),
         ('Error', 'OnError'), ('ErrorContainer', 'OnErrorContainer'),
         ('Background', 'OnBackground'), ('Surface', 'OnSurface'),
         ('SurfaceVariant', 'OnSurfaceVariant'), ('InverseSurface', 'InverseOnSurface')]
minimum = 100
for mode in ('Light', 'Dark'):
    for background, foreground in roles:
        ratio = contrast(values[mode + background], values[mode + foreground])
        minimum = min(minimum, ratio)
        if ratio < 4.5:
            errors.append(f'{mode} {background}/{foreground}: {ratio:.2f}:1, below 4.5:1')

# Verify that generated dark roles really use the recorded Material HCT tones.
tones = json.loads((ROOT / 'docs/design-system/hct-tones.json').read_text())
for role, seed in [('Primary', '2D2C2B'), ('Secondary', '3B197F'), ('Tertiary', '8144A8'), ('Error', 'D73431')]:
    for name, tone in [(role, 80), ('On'+role, 20), (role+'Container', 30), ('On'+role+'Container', 90)]:
        if values['Dark'+name] != tones[seed][str(tone)][2:]:
            errors.append(f'Dark {name} no longer matches the documented HCT derivation')
if errors:
    raise SystemExit('\n'.join(errors))
print(f'PASS: {len(figma_hex)} documented Figma colors retained; 24 M3 role pairs >= 4.5:1 (minimum {minimum:.2f}:1).')
print('PASS: HCT dark-role provenance; no raw Color, sp, font-family, or rounded-corner values outside theme.')
