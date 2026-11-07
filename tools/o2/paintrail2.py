import sys, json, math
from PIL import Image
meta = json.load(open(sys.argv[1])); out = sys.argv[2]; SIZE = meta['size']; SIDING = len(sys.argv) > 3 and sys.argv[3] == 'siding'
def h(x, y, s=0):
    n = math.sin(x * 12.9898 + y * 78.233 + s * 37.719) * 43758.5453; return n - math.floor(n)
def vn(x, y, s=0):
    xi, yi = math.floor(x), math.floor(y); xf, yf = x - xi, y - yi
    a, b, c, d = h(xi, yi, s), h(xi + 1, yi, s), h(xi, yi + 1, s), h(xi + 1, yi + 1, s)
    u, v = xf * xf * (3 - 2 * xf), yf * yf * (3 - 2 * yf)
    return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v
def fbm(x, y, s=0): return 0.5 * vn(x, y, s) + 0.3 * vn(x * 2.1, y * 2.1, s + 1) + 0.2 * vn(x * 4.3, y * 4.3, s + 2)
def mix(a, b, t): t = max(0, min(1, t)); return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
CONC, CONC_D = (152, 149, 142), (121, 118, 112)
def paint(cat, p, edge):
    x, y, z = p
    if cat.startswith('sleeper') and SIDING:
        # creosoted timber: dark brown, grain along the sleeper, weathered grey on top
        grain = vn(x * 6, (z + y) * 90, 21) * 0.6 + vn(x * 30, (z + y) * 200, 22) * 0.4
        c = mix((52, 40, 30), (84, 66, 50), grain)
        if cat == 'sleeper_top':
            c = mix(c, (110, 104, 96), 0.35 * fbm(x * 4, z * 12, 23))
            if h(x * 50, z * 400, 24) > 0.985: c = mix(c, (30, 24, 18), 0.8)
        else:
            c = mix(c, (40, 31, 24), 0.3)
        return mix(c, (38, 30, 23), 0.4 * max(0, 1 - edge / 0.015))
    if cat.startswith('sleeper'):
        g = fbm(x * 9 + y * 3, z * 9, 3)
        c = mix(CONC_D, CONC, 0.5 + 0.5 * g)
        if cat == 'sleeper_top':
            c = mix(c, (106, 101, 94), 0.25 * max(0, 1 - abs(x) / 0.45) * fbm(x * 5, z * 5, 7) * 2)
            if h(x * 500, z * 500, 9) > 0.975: c = mix(c, (170, 167, 160), 0.3)
            if h(x * 500, z * 500, 12) > 0.975: c = mix(c, (110, 107, 100), 0.4)
        else:
            c = mix(c, (100, 97, 92), 0.3 + 0.25 * (1 - y / 0.1))
        return mix(c, (104, 101, 96), 0.45 * max(0, 1 - edge / 0.018))
    if cat.startswith('plate'):
        return mix((44, 44, 46), (60, 60, 63), fbm(x * 60, z * 60, 4)) if cat == 'plate_top' else (36, 36, 38)
    if cat.startswith('clip'):
        c = mix((52, 54, 58), (70, 72, 76), fbm(x * 80, z * 80, 5))
        if cat == 'clip_top':
            # the e-clip toe pressing on the foot: a lighter steel bar across the shoulder
            if abs(z) < 0.011: c = mix(c, (150, 154, 160), 0.85)
            # a dab of yellow-green insulator under the toe
            if abs(z) < 0.004 and edge < 0.004: c = (104, 102, 66)
        else:
            c = mix(c, (40, 41, 44), 0.3)
        return c
    if cat == 'top':
        s = 0.86 + 0.14 * vn(x * 400, z * 5, 4)
        if SIDING: return mix(tuple(v * s for v in (176, 168, 160)), (120, 86, 62), 0.35 * fbm(x * 100, z * 10, 25))
        return tuple(v * s for v in (208, 211, 215))
    if cat == 'crown':
        return mix((150, 153, 158), (190, 193, 198), 0.5 + 0.5 * vn(x * 300, z * 8, 2))
    if cat == 'headside':
        return mix((92, 82, 74), (122, 108, 96), fbm(x * 80, z * 18 + y * 60, 6))
    t = fbm(x * 60, z * 22 + y * 50, 8)
    c = mix((68, 56, 48), (98, 74, 58), t)
    c = mix(c, (130, 88, 56), 0.7 * max(0, fbm(x * 20, z * 7, 13) - 0.55) * 2)
    if cat == 'foot': c = mix(c, (74, 56, 42), 0.35)
    if SIDING: c = mix(c, (126, 80, 48), 0.3)
    return c
img = Image.new('RGBA', (SIZE, SIZE), (0, 0, 0, 0)); px = img.load()
def lerp(a, b, t): return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))
for q in meta['quads']:
    v = q['v']; uv = q['uv']
    x0, x1 = min(u for u, _ in uv), max(u for u, _ in uv); y0, y1 = min(t for _, t in uv), max(t for _, t in uv)
    W = math.dist(v[0], v[1]); Hh = math.dist(v[1], v[2])
    for yy in range(int(y0) - 1, int(y1) + 1):
        for xx in range(int(x0) - 1, int(x1) + 1):
            s = min(1, max(0, (xx + 0.5 - x0) / (x1 - x0))); t = min(1, max(0, (y1 - (yy + 0.5)) / (y1 - y0)))
            # uv[0]=v0 at (left,bottom), uv[1]=v1 (right,bottom), uv[2]=v2 (right,top), uv[3]=v3 (left,top)
            p = lerp(lerp(v[0], v[1], s), lerp(v[3], v[2], s), t)
            edge = min(s * W, (1 - s) * W, t * Hh, (1 - t) * Hh)
            if 0 <= xx < SIZE and 0 <= yy < SIZE:
                px[xx, yy] = tuple(max(0, min(255, int(c))) for c in paint(q['cat'], p, edge)) + (255,)
img.save(out); print('ok')
