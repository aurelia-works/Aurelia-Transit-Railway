"""Aurelia look (O2) signal heads: UK-style colour-light heads over MTR's signal block models.
Keeps MTR's lens positions (the lit aspect is drawn in Java there) and its 22.5/45/67.5 rotations.
Usage: python3 gensignals.py <mtr assets dir> <pack assets/mtr dir>"""
import sys, json, os, math, random
from PIL import Image, ImageDraw
SRC, OUT = sys.argv[1], sys.argv[2]
MB, TB = os.path.join(OUT, 'models/block'), os.path.join(OUT, 'textures/block/aurelia')
os.makedirs(MB, exist_ok=True); os.makedirs(TB, exist_ok=True)
R = random.Random(5)
def jit(c, n): v = R.randint(-n, n); return tuple(max(0, min(255, x + v)) for x in c[:3]) + (255,)
def hx(s): return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4)) + (255,)
# textures
img = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16): img.putpixel((x, y), jit(hx('1d1e20'), 3))
d = ImageDraw.Draw(img); d.line([0, 0, 15, 0], fill=hx('2c2d30')); d.line([0, 15, 15, 15], fill=hx('121314'))
for p in ((2, 2), (13, 2), (2, 13), (13, 13)): img.putpixel(p, hx('3a3b3e'))
d.line([8, 1, 8, 14], fill=hx('151618'))
img.save(os.path.join(TB, 'signal_housing.png'))
bp = Image.new('RGBA', (32, 32))
for y in range(32):
    for x in range(32): bp.putpixel((x, y), jit(hx('141516'), 2))
d = ImageDraw.Draw(bp); d.rectangle([0, 0, 31, 31], outline=hx('ecebe6')); d.rectangle([1, 1, 30, 30], outline=hx('e2e1dc'))
d.rectangle([2, 2, 29, 29], outline=hx('202123'))
bp.save(os.path.join(TB, 'signal_backplate.png'))
hood = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16): hood.putpixel((x, y), jit(hx('17181a'), 3))
ImageDraw.Draw(hood).line([0, 0, 15, 0], fill=hx('303134'))
hood.save(os.path.join(TB, 'signal_hood.png'))
def make_lens(name, tint):
    # Unlit coloured glass: dark tint at the rim, a little brighter in the middle, a soft highlight top-left, thin steel rim
    img = Image.new('RGBA', (16, 16), hx('050506'))
    t = hx(tint)
    for y in range(16):
        for x in range(16):
            r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
            if r >= 7.6:
                continue
            if r > 6.6:
                img.putpixel((x, y), hx('3a3d41'))
                continue
            k = 0.55 - 0.35 * (r / 6.6)
            c = tuple(int(t[i] * k) for i in range(3)) + (255,)
            h = math.hypot(x + 0.5 - 5.5, y + 0.5 - 5.0)
            if h < 2.2:
                a = 0.45 * (1 - h / 2.2)
                c = tuple(int(c[i] + (235 - c[i]) * a) for i in range(3)) + (255,)
            img.putpixel((x, y), jit(c, 2))
    img.save(os.path.join(TB, name + '.png'))
make_lens('signal_lens', '7a838c')
make_lens('signal_lens_red', 'e0201a')
make_lens('signal_lens_yellow', 'f0a020')
make_lens('signal_lens_green', '20d070')
lamp = Image.new('RGBA', (16, 16), hx('000000'))
for y in range(16):
    for x in range(16):
        r = math.hypot(x + 0.5 - 8, y + 0.5 - 8)
        if r < 6.6:
            v = int(255 * (1.0 if r < 3.2 else max(0.35, 1 - (r - 3.2) / 5)))
            lamp.putpixel((x, y), (v, v, v, 255))
os.makedirs(os.path.join(OUT, 'textures/block'), exist_ok=True)
lamp.save(os.path.join(OUT, 'textures/block/signal_lamp.png'))
pole = Image.new('RGBA', (16, 16))
for y in range(16):
    for x in range(16): pole.putpixel((x, y), jit(hx('8f9296') if x % 4 else hx('9a9da1'), 5))
for y in range(16):
    if R.random() < 0.3: pole.putpixel((R.randrange(16), y), hx('7d7b74'))
pole.save(os.path.join(TB, 'signal_pole.png'))
# models
TEX = {'housing': 'mtr:block/aurelia/signal_housing', 'plate': 'mtr:block/aurelia/signal_backplate',
       'hood': 'mtr:block/aurelia/signal_hood', 'lens': 'mtr:block/aurelia/signal_lens', 'lens_red': 'mtr:block/aurelia/signal_lens_red', 'lens_yellow': 'mtr:block/aurelia/signal_lens_yellow', 'lens_green': 'mtr:block/aurelia/signal_lens_green', 'particle': 'mtr:block/aurelia/signal_housing'}
def box(f, t, faces):
    return {'from': f, 'to': t, 'faces': faces}
def all_faces(tex, skip=()):
    return {k: {'texture': '#' + tex} for k in ('north', 'east', 'south', 'west', 'up', 'down') if k not in skip}
def covers(orig):
    els = orig['elements']
    plate = els[0]; lenses = [e for e in els if e['from'][2] == 11.05]
    out = []
    pf, pt = plate['from'], plate['to']
    faces = all_faces('hood'); faces['south'] = {'texture': '#plate', 'uv': [0, 0, 16, 16]}; faces['north'] = {'texture': '#plate', 'uv': [16, 0, 0, 16]}
    out.append(box(pf, pt, faces))
    # MTR's lit order, bottom to top: red, yellow, green, then a second yellow on 4-aspect heads. 2-aspect heads can put
    # red on top at runtime, so their lenses stay neutral.
    order = sorted(lenses, key=lambda e: e['from'][1])
    tints = ['lens'] * len(order) if len(order) == 2 else ['lens_red', 'lens_yellow', 'lens_green', 'lens_yellow'][:len(order)]
    for L in lenses:
        lens_tex = tints[order.index(L)]
        (x0, y0, _), (x1, y1, _) = L['from'], L['to']
        cy = (y0 + y1) / 2
        # lens ring
        out.append(box([x0 - 0.5, y0 - 0.5, 10.5], [x1 + 0.5, y0, 11.1], all_faces('hood', ('north',))))
        out.append(box([x0 - 0.5, y0, 10.5], [x0, y1, 11.1], all_faces('hood', ('north',))))
        out.append(box([x1, y0, 10.5], [x1 + 0.5, y1, 11.1], all_faces('hood', ('north',))))
        # hood: deep top, sides running down to the lens centre
        out.append(box([x0 - 0.5, y1, 10.5], [x1 + 0.5, y1 + 0.5, 14.5], all_faces('hood', ('north',))))
        out.append(box([x0 - 0.5, cy, 11.1], [x0, y1, 14], all_faces('hood', ('north', 'up'))))
        out.append(box([x1, cy, 11.1], [x1 + 0.5, y1, 14], all_faces('hood', ('north', 'up'))))
        out.append(box([x0, y0, 11.05], [x1, y1, 11.05], {'south': {'texture': '#' + lens_tex, 'uv': [0, 0, 16, 16]}}))
    return out
def base(orig):
    (f, t) = orig['elements'][0]['from'], orig['elements'][0]['to']
    return [box(f, t, all_faces('housing')), box([f[0] - 0.5, t[1], f[2] - 0.5], [t[0] + 0.5, t[1] + 0.5, t[2] + 0.5], all_faces('hood'))]
written = 0
for n in (2, 3, 4):
    for suf in ('', '_22_5', '_45', '_67_5'):
        for kind, fn in (('signal_light_%d_covers' % n, covers), ('signal_light_base_%d_aspect' % n, base)):
            orig = json.load(open(os.path.join(SRC, 'models/block', kind + suf + '.json')))
            rot = orig['elements'][0].get('rotation')
            els = fn(orig)
            if rot:
                for e in els: e['rotation'] = dict(rot)
            model = {'parent': 'block/block', 'textures': dict(TEX), 'elements': els}
            json.dump(model, open(os.path.join(MB, kind + suf + '.json'), 'w'), indent='\t')
            written += 1
pole_model = json.load(open(os.path.join(SRC, 'models/block/signal_pole.json')))
pole_model['textures'] = {'particle': 'mtr:block/aurelia/signal_pole'}
for e in pole_model['elements']:
    for fc in e['faces'].values(): fc['texture'] = '#particle'
json.dump(pole_model, open(os.path.join(MB, 'signal_pole.json'), 'w'), indent='\t')
print('models', written + 1)
