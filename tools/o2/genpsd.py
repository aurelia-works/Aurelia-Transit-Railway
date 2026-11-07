"""Aurelia look, P2: platform screen doors and automatic platform gates in brushed stainless steel.

Keeps every MTR texture's alpha mask (the glass openings are cutouts) and its layout, so the door models and UVs are
untouched. Opaque pixels are repainted by their original tone: MTR's black frame becomes the shaded door edge, dark grey
becomes the brushed stainless front frame, light grey becomes a satin kick plate. Every glass opening gets a dark seal line. Small textures
are upscaled 4x first so the brushing reads.
Run: python3 -I tools/o2/genpsd.py fabric/src/main/resources/assets/mtr fabric/src/main/resources/resourcepacks/aurelia_look/assets/mtr
"""
import glob
import os
import random
import sys

from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
TB = os.path.join(OUT, 'textures/block')
os.makedirs(TB, exist_ok=True)
R = random.Random(11)

STEEL = (176, 180, 184)
STEEL_DARK = (128, 132, 137)
SEAL = (34, 35, 37)
KICK = (148, 151, 154)


def brushed(base, x, y, streaks):
    v = streaks[x % len(streaks)] + R.uniform(-1.5, 1.5) + 6 * ((y % 37) / 37 - 0.5) * 0.3
    return tuple(max(0, min(255, int(c + v))) for c in base) + (255,)


def repaint(path, out_path):
    src = Image.open(path).convert('RGBA')
    scale = 4 if src.width < 128 else 1
    img = src.resize((src.width * scale, src.height * scale), Image.NEAREST)
    w, h = img.size
    raw = [R.gauss(0, 4) for _ in range(w)]
    streaks = [(raw[i - 1] + 2 * raw[i] + raw[(i + 1) % w]) / 4 for i in range(w)]
    px = img.load()
    out = Image.new('RGBA', (w, h))
    po = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 128:
                po[x, y] = (0, 0, 0, 0)
                continue
            lum = (r * 299 + g * 587 + b * 114) // 1000
            if lum < 40:
                c = brushed(STEEL_DARK, x, y, streaks)  # side faces: the door edge, in shade
            elif lum < 110:
                c = brushed(STEEL, x, y, streaks)  # front frame
            elif lum < 230:
                c = brushed(KICK, x, y, streaks)
            else:
                c = brushed(STEEL, x, y, streaks)
            po[x, y] = c
    # seal line round every opening, and a soft edge shade so each frame member reads as a bevel
    for y in range(h):
        for x in range(w):
            if po[x, y][3] == 0:
                continue
            near_hole = any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] < 128 for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if near_hole:
                po[x, y] = SEAL + (255,)
            elif any(0 <= x + dx < w and 0 <= y + dy < h and px[x + dx, y + dy][3] < 128 for dx, dy in ((2, 0), (-2, 0), (0, 2), (0, -2))):
                po[x, y] = STEEL_DARK + (255,)
    out.save(out_path)


names = sorted(glob.glob(os.path.join(SRC, 'textures/block/psd_*.png')) + glob.glob(os.path.join(SRC, 'textures/block/apg_*.png')))
names = [n for n in names if 'light' not in os.path.basename(n)]
for n in names:
    repaint(n, os.path.join(TB, os.path.basename(n)))
print('repainted', len(names))
