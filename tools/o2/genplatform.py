"""Aurelia look, P2: platform tops and faces, direction B (real materials).

Writes 128 px textures (1 block = 1 m, so 1 px = 7.8 mm) into the pack and points the platform models' body at a concrete face
instead of vanilla stone or coal. The top textures keep MTR's layout: the platform edge is the top rows of the image.
Run: python3 -I tools/o2/genplatform.py fabric/src/main/resources/assets/mtr fabric/src/main/resources/resourcepacks/aurelia_look/assets/mtr
"""
import json
import math
import os
import random
import sys

from PIL import Image

SRC, OUT = sys.argv[1], sys.argv[2]
TB = os.path.join(OUT, 'textures/block')
TA = os.path.join(TB, 'aurelia')
MB = os.path.join(OUT, 'models/block')
for d in (TB, TA, MB):
    os.makedirs(d, exist_ok=True)
R = random.Random(7)
S = 128


def hx(s):
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def noise_field(seed, cell):
    """Smooth value noise in 0..1, tiling at 128."""
    rnd = random.Random(seed)
    n = S // cell
    grid = [[rnd.random() for _ in range(n)] for _ in range(n)]

    def f(x, y):
        gx, gy = x / cell, y / cell
        x0, y0 = int(gx) % n, int(gy) % n
        x1, y1 = (x0 + 1) % n, (y0 + 1) % n
        tx, ty = gx - int(gx), gy - int(gy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        a = grid[y0][x0] + (grid[y0][x1] - grid[y0][x0]) * tx
        b = grid[y1][x0] + (grid[y1][x1] - grid[y1][x0]) * tx
        return a + (b - a) * ty
    return f


N1, N2, N3 = noise_field(1, 32), noise_field(2, 8), noise_field(3, 4)


def concrete(x, y, base, var=14):
    v = (N1(x, y) - 0.5) * var + (N2(x, y) - 0.5) * var * 0.6 + (N3(x, y) - 0.5) * var * 0.3 + R.uniform(-3, 3)
    c = tuple(max(0, min(255, int(base[i] + v))) for i in range(3))
    if R.random() < 0.012:
        c = mix(c, (60, 60, 58), 0.35)
    return c


def put(img, x, y, c):
    img.putpixel((x, y), c + (255,))


# --- UK: white-edged coping, yellow line, buff offset-blister tactile, then flags -----------------------------------
def uk():
    img = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            if y < 38:  # coping stone, 300 mm, white painted nosing 100 mm
                c = concrete(x, y, hx('c9c7c0'), 12)
                if y < 13:
                    c = mix(c, (238, 238, 232), 0.85)
                    if y == 0:
                        c = mix(c, (150, 150, 145), 0.5)
                if y == 37 or x % 64 == 0:
                    c = mix(c, (95, 95, 92), 0.6)  # joints between coping units
            elif y < 51:  # yellow line, 100 mm
                c = mix(concrete(x, y, hx('e3c21c'), 10), (120, 110, 60), 0.12 * N2(x, y))
            elif y < 103:  # buff offset blister, 400 mm
                c = concrete(x, y, hx('c9b48c'), 10)
                if y == 51 or y == 102 or x % 32 == 0:
                    c = mix(c, (110, 100, 80), 0.55)
                # blisters 25 mm across on a 66.6 mm grid, every other row offset
                gy = (y - 51) / 8.5
                row = int(gy)
                ox = 4.25 if row % 2 else 0
                gx = (x + ox) / 8.5
                dx, dy = (gx - int(gx) - 0.5) * 8.5, (gy - row - 0.5) * 8.5
                r = math.hypot(dx, dy)
                if r < 2.2:
                    c = mix(c, (248, 238, 210), 0.75 * (1 - r / 2.6)) if dx + dy < 0 else mix(c, (105, 90, 64), 0.6 * (1 - r / 2.6))
            else:  # 400 mm concrete flags
                c = concrete(x, y, hx('a9a8a3'), 14)
                if y == 103 or x % 64 == 32:
                    c = mix(c, (80, 80, 78), 0.6)
            put(img, x, y, c)
    return img


# --- North America: yellow truncated domes (ADA) on a concrete edge -------------------------------------------------
def na(dark):
    img = Image.new('RGBA', (S, S))
    field = hx('2b2b2c') if dark else hx('e9b51a')
    for y in range(S):
        for x in range(S):
            if y < 13:  # concrete edge strip
                c = concrete(x, y, hx('bdbbb4'), 10)
                if y == 0:
                    c = mix(c, (130, 130, 125), 0.5)
            elif y < 91:  # 610 mm detectable warning
                c = concrete(x, y, field, 6)
                if y == 13 or y == 90 or x % 64 == 0:
                    c = mix(c, (40, 40, 40), 0.5)
                # domes 23 mm across at 60 mm spacing, square grid
                gx, gy = x / 7.7, (y - 13) / 7.7
                dx, dy = (gx - int(gx) - 0.5) * 7.7, (gy - int(gy) - 0.5) * 7.7
                r = math.hypot(dx, dy)
                if r < 2.0:
                    lit = (255, 248, 210) if not dark else (120, 120, 120)
                    c = mix(c, lit, 0.6 * (1 - r / 2.4)) if dx + dy < 0 else mix(c, (60, 40, 0) if not dark else (0, 0, 0), 0.5 * (1 - r / 2.4))
                elif r < 2.6 and dx + dy > 0:
                    c = mix(c, (0, 0, 0), 0.25)
            else:
                c = concrete(x, y, hx('a9a8a3'), 14)
                if y == 91 or x % 64 == 32:
                    c = mix(c, (80, 80, 78), 0.6)
            put(img, x, y, c)
    return img


# --- Hong Kong: yellow guide strips on light grey granite --------------------------------------------------------
def hk(plain):
    img = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            c = concrete(x, y, hx('c4c2bd'), 9)
            if R.random() < 0.02:
                c = mix(c, (90, 88, 86), 0.4)  # granite speckle
            if R.random() < 0.012:
                c = mix(c, (235, 233, 228), 0.5)
            if y % 64 == 0 or x % 64 == 0:
                c = mix(c, (110, 108, 104), 0.5)
            if not plain and y < 12:
                c = concrete(x, y, hx('e8c01e'), 8)  # yellow edge band
                if y == 0:
                    c = mix(c, (130, 110, 30), 0.5)
            if not plain and 16 <= y < 48 and y % 8 in (2, 3, 4):
                c = mix(c, (232, 190, 30), 0.85)  # raised guide bars
            put(img, x, y, c)
    return img


# --- Platform face: precast concrete with a coping lip shadow and grime toward the base --------------------------
def face():
    img = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            c = concrete(x, y, hx('9d9b95'), 16)
            c = mix(c, (70, 68, 62), 0.35 * (y / S) ** 2 + 0.15 * N1(x * 3, y))
            if x % 64 == 0:
                c = mix(c, (60, 60, 58), 0.5)
            put(img, x, y, c)
    return img


uk().save(os.path.join(TB, 'platform_uk_1.png'))
na(False).save(os.path.join(TB, 'platform_na_1.png'))
na(True).save(os.path.join(TB, 'platform_na_2.png'))
hk(False).save(os.path.join(TB, 'platform_hk_1.png'))
face().save(os.path.join(TA, 'platform_face.png'))

written = 0
for fam in ('uk_1', 'na_1', 'na_2', 'hk_1'):
    for suf in ('', '_indented', '_slab_top', '_slab_bottom'):
        name = 'platform_%s%s.json' % (fam, suf)
        model = json.load(open(os.path.join(SRC, 'models/block', name)))
        model['textures']['main'] = 'mtr:block/aurelia/platform_face'
        json.dump(model, open(os.path.join(MB, name), 'w'), indent='\t')
        written += 1
print('textures 5, models', written)
