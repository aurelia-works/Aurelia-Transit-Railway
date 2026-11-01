#!/usr/bin/env python3
"""Paints ATR's passenger skins (64x64 player layout). Run: python3 tools/passenger_skins.py <output folder>.
Even numbers are wide models, odd are slim. Output is fixed for the seed, so rerunning changes nothing."""
import random, sys
from PIL import Image

SKIN = [(246,214,186),(234,192,155),(205,155,113),(168,114,76),(120,78,52),(84,54,38)]
HAIR = [(30,26,24),(62,42,28),(110,74,40),(176,136,76),(150,60,34),(148,146,144),(52,50,60)]
OUT = sys.argv[1]

def shade(c, n=0): return tuple(max(0, min(255, v + n)) for v in c) + (255,)

def faces(u, v, w, h, d):
    return dict(top=(u+d, v, w, d), bottom=(u+d+w, v, w, d), right=(u, v+d, d, h), front=(u+d, v+d, w, h),
                left=(u+d+w, v+d, d, h), back=(u+d+w+d, v+d, w, h))

def fill(img, box, color, rng=None, grain=5):
    x, y, w, h = box
    for i in range(w):
        for j in range(h):
            img.putpixel((x+i, y+j), shade(color, rng.randint(-grain, grain) if rng else 0))

def cuboid(img, uv, color, rng):
    for name, box in faces(*uv).items():
        fill(img, box, shade(color, 12 if name == 'top' else -14 if name == 'bottom' else 0)[:3], rng)

def px(img, box, x, y, color):
    img.putpixel((box[0]+x, box[1]+y), shade(color))

def paint(n, rng):
    slim = n % 2 == 1
    aw = 3 if slim else 4
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    skin = SKIN[n % len(SKIN)]
    hair = rng.choice(HAIR)
    outfit = OUTFITS[n % len(OUTFITS)]
    top, legs, shoes, kind = outfit(rng)
    head, body = (0, 0, 8, 8, 8), (16, 16, 8, 12, 4)
    rarm, larm = (40, 16, aw, 12, 4), (32, 48, aw, 12, 4)
    rleg, lleg = (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)
    cuboid(img, head, skin, rng)
    cuboid(img, body, top, rng)
    for arm in (rarm, larm):
        cuboid(img, arm, top, rng)
        f = faces(*arm)
        for name in ('right', 'front', 'left', 'back'):
            fill(img, (f[name][0], f[name][1]+10, f[name][2], 2), skin, rng)
        fill(img, f['bottom'], skin, rng)
    for leg in (rleg, lleg):
        cuboid(img, leg, legs, rng)
        f = faces(*leg)
        for name in ('right', 'front', 'left', 'back'):
            fill(img, (f[name][0], f[name][1]+10, f[name][2], 2), shoes, rng)
        fill(img, f['bottom'], shoes, rng)
    # face
    fr = faces(*head)['front']
    eye = rng.choice([(40,30,24),(50,70,110),(60,90,60),(70,50,30)])
    for x in (1, 5):
        px(img, fr, x, 4, (240,240,236)); px(img, fr, x+1, 4, eye)
    brow = shade(hair, -20)[:3]
    for x in (1, 2, 5, 6): px(img, fr, x, 3, brow)
    px(img, fr, 3, 5, shade(skin, -24)[:3]); px(img, fr, 4, 5, shade(skin, -24)[:3])
    for x in (3, 4, 5): px(img, fr, x, 6, shade(skin, -55)[:3])
    # hair on the head's own faces
    style = n % 5
    f = faces(*head)
    if style == 4:  # headscarf
        scarf = rng.choice([(40,60,110),(120,40,60),(50,90,80),(190,170,130)])
        fill(img, f['top'], scarf, rng); fill(img, f['back'], scarf, rng)
        fill(img, f['right'], scarf, rng); fill(img, f['left'], scarf, rng)
        fill(img, (fr[0], fr[1], 8, 2), scarf, rng)
        for y in range(2, 8):
            px(img, fr, 0, y, scarf); px(img, fr, 7, y, scarf)
        fill(img, (fr[0], fr[1]+7, 8, 1), scarf, rng)
        return img, top
    fill(img, f['top'], hair, rng)
    fill(img, (fr[0], fr[1], 8, 2), hair, rng)
    depth = {0: 3, 1: 8, 2: 4, 3: 2}[style]
    for name in ('right', 'left'):
        fill(img, (f[name][0], f[name][1], 8, depth), hair, rng)
    fill(img, (f['back'][0], f['back'][1], 8, 8 if style == 1 else depth + 2), hair, rng)
    if style == 1:
        for y in range(2, 7):
            px(img, fr, 0, y, hair); px(img, fr, 7, y, hair)
    if n % 7 == 3:  # glasses
        for x in (0, 1, 2, 3, 4, 5, 6, 7): px(img, fr, x, 4, (30,30,34))
        for x in (1, 5):
            px(img, fr, x, 4, (235,240,245)); px(img, fr, x+1, 4, eye)
    if n % 6 == 2 and not slim:  # beard
        for x in range(1, 7):
            px(img, fr, x, 7, hair)
        for x in (1, 2, 5, 6): px(img, fr, x, 6, hair)
    # the outfit's detail on the chest and back
    fb = faces(*body)['front']
    if kind == 'suit':
        for y in range(0, 12):
            for x in (3, 4): px(img, fb, x, y, (232,232,232) if y < 6 else top)
        for y in range(0, 7): px(img, fb, 3 + (y % 2), y, (150,30,40))
    elif kind == 'hoodie':
        fill(img, (fb[0]+2, fb[1], 4, 2), shade(top, -30)[:3], rng)
        fill(img, (fb[0]+1, fb[1]+7, 6, 3), shade(top, -18)[:3], rng)
        px(img, fb, 3, 2, (235,235,235)); px(img, fb, 4, 2, (235,235,235))
    elif kind == 'hivis':
        for y in (5, 9):
            fill(img, (fb[0], fb[1]+y, 8, 1), (225,225,215), rng, 2)
            b = faces(*body)['back']; fill(img, (b[0], b[1]+y, 8, 1), (225,225,215), rng, 2)
    elif kind == 'coat':
        for y in range(0, 12): px(img, fb, 4, y, shade(top, -35)[:3])
        for y in (3, 6, 9): px(img, fb, 3, y, (205,195,160))
    elif kind == 'dress':
        for leg in (rleg, lleg):
            f2 = faces(*leg)
            for name in ('right', 'front', 'left', 'back'):
                fill(img, (f2[name][0], f2[name][1], f2[name][2], 6), top, rng)
        fill(img, (fb[0], fb[1]+6, 8, 1), shade(top, -40)[:3], rng)
    elif kind == 'stripes':
        for y in range(0, 12, 3):
            fill(img, (fb[0], fb[1]+y, 8, 1), (235,235,230), rng, 2)
    return img, top

OUTFITS = [
    lambda r: (r.choice([(36,42,62),(44,44,48),(70,62,56)]), (34,38,54), (28,24,22), 'suit'),
    lambda r: (r.choice([(150,40,48),(50,90,130),(70,110,70),(110,110,115)]), (56,76,118), (232,232,226), 'hoodie'),
    lambda r: (r.choice([(170,132,86),(92,60,44),(60,70,62)]), (48,48,54), (50,34,26), 'coat'),
    lambda r: (r.choice([(170,60,110),(60,120,140),(190,150,60),(120,70,150)]), (214,190,160), (120,40,50), 'dress'),
    lambda r: ((236,122,28), (46,52,70), (70,48,30), 'hivis'),
    lambda r: (r.choice([(30,70,120),(120,40,40),(40,110,90)]), (150,136,110), (240,240,240), 'stripes'),
    lambda r: ((230,226,214), (40,52,92), (24,24,28), 'plain'),
]

for n in range(24):
    image, _ = paint(n, random.Random(7000 + n))
    image.save(f'{OUT}/p{n:02d}.png')
