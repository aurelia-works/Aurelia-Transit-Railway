"""Generate a realistic MTR rail segment OBJ (same length, gauge, head height as MTR's rail.obj) plus its UV atlas list."""
import sys, json
out_obj, out_meta = sys.argv[1], sys.argv[2]
Z0, Z1 = -0.3, 0.3
quads = []  # (category, [4 verts], uv size (w,h) in metres)
def quad(cat, a, b, c, d):
    quads.append((cat, [a, b, c, d]))
C = 0.7517
# right-hand half profile, x offset from rail centre (positive = outward for the +x rail), from foot edge up to the crown
prof = [(0.075, 0.109, 'foot'), (0.075, 0.119, 'foot'), (0.0085, 0.134, 'web'), (0.0085, 0.222, 'web'),
        (0.034, 0.236, 'headside'), (0.034, 0.257, 'headside'), (0.027, 0.264, 'crown')]
for s in (1, -1):
    c = C * s
    for side in (1, -1):
        for i in range(len(prof) - 1):
            (x0, y0, _), (x1, y1, cat) = prof[i], prof[i + 1]
            xa, xb = c + side * x0, c + side * x1
            p0, p1 = (xa, y0, Z1), (xb, y1, Z1)
            q0, q1 = (xa, y0, Z0), (xb, y1, Z0)
            # outward on this side: order so the normal points away from the rail centre
            if side == 1:
                quad(cat, p0, q0, q1, p1)
            else:
                quad(cat, q0, p0, p1, q1)
    xt = 0.027
    quad('top', (c - xt, 0.264, Z1), (c + xt, 0.264, Z1), (c + xt, 0.264, Z0), (c - xt, 0.264, Z0))
# sleeper: monoblock, slightly tapered sides
L, T, B, H = 1.2, 0.11, 0.135, 0.0971
quad('sleeper_top', (-L, H, T), (L, H, T), (L, H, -T), (-L, H, -T))
quad('sleeper_side', (-L, 0, B), (L, 0, B), (L, H, T), (-L, H, T))
quad('sleeper_side', (L, 0, -B), (-L, 0, -B), (-L, H, -T), (L, H, -T))
quad('sleeper_end', (L, 0, B), (L, 0, -B), (L, H, -T), (L, H, T))
quad('sleeper_end', (-L, 0, -B), (-L, 0, B), (-L, H, T), (-L, H, -T))
for s in (1, -1):
    c = C * s
    # rail pad / base plate
    x0, x1, y0, y1, zb = c - 0.1, c + 0.1, H, 0.109, 0.085
    quad('plate_top', (x0, y1, zb), (x1, y1, zb), (x1, y1, -zb), (x0, y1, -zb))
    quad('plate_side', (x0, y0, zb), (x1, y0, zb), (x1, y1, zb), (x0, y1, zb))
    quad('plate_side', (x1, y0, -zb), (x0, y0, -zb), (x0, y1, -zb), (x1, y1, -zb))
    # clip shoulders either side of the foot
    for side in (1, -1):
        xi, xo = c + side * 0.077, c + side * 0.1
        xa, xb = (xi, xo) if side == 1 else (xo, xi)
        y0, y1, zc = 0.109, 0.137, 0.03
        quad('clip_top', (xa, y1, zc), (xb, y1, zc), (xb, y1, -zc), (xa, y1, -zc))
        quad('clip_side', (xa, y0, zc), (xb, y0, zc), (xb, y1, zc), (xa, y1, zc))
        quad('clip_side', (xb, y0, -zc), (xa, y0, -zc), (xa, y1, -zc), (xb, y1, -zc))
        if side == 1:
            quad('clip_side', (xo, y0, zc), (xo, y0, -zc), (xo, y1, -zc), (xo, y1, zc))
        else:
            quad('clip_side', (xo, y0, -zc), (xo, y0, zc), (xo, y1, zc), (xo, y1, -zc))
# UV packing: each quad's rectangle is its edge lengths at a fixed density, shelf-packed
import math
def dist(a, b): return math.sqrt(sum((a[i] - b[i]) ** 2 for i in range(3)))
SIZE, DEN, PAD = 512, 190, 3
rects = []
for idx, (cat, v) in enumerate(quads):
    w = max(2, round(dist(v[0], v[1]) * DEN)); h = max(2, round(dist(v[1], v[2]) * DEN))
    rects.append([idx, w, h])
order = sorted(rects, key=lambda r: -r[2])
x = y = shelf = 0; place = {}
for idx, w, h in order:
    if x + w + PAD > SIZE: x = 0; y += shelf + PAD; shelf = 0
    place[idx] = (x + 1, y + 1, w, h); x += w + PAD; shelf = max(shelf, h)
assert y + shelf < SIZE, 'atlas overflow %d' % (y + shelf)
lines = ['# Aurelia rail segment (ATR O2). Same length, gauge and head height as MTR rail.obj', 'g obj1', 'usemtl rail']
vts = []; faces = []; meta = []
for idx, (cat, v) in enumerate(quads):
    px, py, w, h = place[idx]
    uv = [(px, py + h), (px + w, py + h), (px + w, py), (px, py)]
    base = len(vts)
    for k in range(4):
        lines.insert(2 + len(vts), 'v %.6f %.6f %.6f' % v[k])
        vts.append(uv[k])
    faces.append((base, cat))
    meta.append({'cat': cat, 'v': v, 'uv': uv})
vt_lines = ['vt %.5f %.5f' % (u / SIZE, 1 - t / SIZE) for (u, t) in vts]
def fnorm(v):
    u = [v[1][i] - v[0][i] for i in range(3)]; w = [v[2][i] - v[0][i] for i in range(3)]
    n = (u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]); l = math.sqrt(sum(c * c for c in n)) or 1
    return tuple(c / l for c in n)
vn_lines = ['vn %.5f %.5f %.5f' % fnorm(q[1]) for q in quads]
f_lines = ['f ' + ' '.join('%d/%d/%d' % (b + k + 1, b + k + 1, n + 1) for k in range(4)) for n, (b, _) in enumerate(faces)]
v_lines = [l for l in lines if l.startswith('v ')]
open(out_obj, 'w').write('\n'.join([lines[0], *v_lines, *vt_lines, *vn_lines, 'g obj1', 'usemtl rail', *f_lines]) + '\n')
json.dump({'size': SIZE, 'quads': meta}, open(out_meta, 'w'))
print('quads', len(quads), 'atlas rows to', y + shelf)
