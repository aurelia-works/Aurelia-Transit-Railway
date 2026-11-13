"""Cut the A320 OBJ's doorways and keep its cabin inside the hull.

The hull (a320_exterior) ran unbroken behind each door panel, so an open door showed solid skin, and the galley and
lining quads beside the front doors stuck out where the fuselage curves in above the door. This clips every hull
triangle on a door's side against that door's rectangle (in y, z), so the dark door_void shows through the opening,
and pulls any cabin vertex that reaches the hull back inside it.

Run once from the repo root: python3 tools/a320_doors.py. It refuses to run twice.
"""
import math
import sys

OBJ = "fabric/src/main/resources/assets/mtr/models/a320_200/a320.obj"
MARK = "# a320_doors.py: doorways cut"
DOORS = ("a320_door_l1", "a320_door_r1", "a320_door_l2", "a320_door_r2")


def main():
    lines = open(OBJ).read().split("\n")
    if any(line.startswith(MARK) for line in lines):
        sys.exit("already cut")
    verts = [None]
    norms = [None]
    last_v = last_vn = 0
    group = None
    faces = []  # (line index, group, [(v, vn)])
    for i, line in enumerate(lines):
        if line.startswith("v "):
            verts.append([float(t) for t in line.split()[1:4]])
            last_v = i
        elif line.startswith("vn "):
            norms.append([float(t) for t in line.split()[1:4]])
            last_vn = i
        elif line.startswith("g "):
            group = line.split()[1]
        elif line.startswith("f "):
            faces.append((i, group, [tuple(int(p) for p in t.split("//")) for t in line.split()[1:]]))

    doors = []
    for name in DOORS:
        ps = [verts[v] for _, g, f in faces if g == name for v, _ in f]
        side = 1 if sum(p[0] for p in ps) > 0 else -1
        doors.append((side, min(p[1] for p in ps), max(p[1] for p in ps), min(p[2] for p in ps), max(p[2] for p in ps)))

    new_v = []
    new_vn = []
    replace = {}

    def add_vertex(p, n):
        new_v.append(p)
        length = math.sqrt(sum(c * c for c in n)) or 1
        new_vn.append([c / length for c in n])
        return len(verts) - 1 + len(new_v), len(norms) - 1 + len(new_vn)

    for i, g, f in faces:
        if g != "a320_exterior" or len(f) != 3:
            continue
        ps = [verts[v] for v, _ in f]
        for side, y0, y1, z0, z1 in doors:
            if min(p[0] * side for p in ps) < 0.5:
                continue
            if max(p[1] for p in ps) <= y0 or min(p[1] for p in ps) >= y1 or max(p[2] for p in ps) <= z0 or min(p[2] for p in ps) >= z1:
                continue
            pieces = cut(ps, [norms[n] for _, n in f], y0, y1, z0, z1)
            if pieces is None:
                continue
            out = []
            for poly in pieces:
                ids = [add_vertex(p, n) for p, n in poly]
                for k in range(1, len(ids) - 1):
                    out.append("f " + " ".join(f"{v}//{n}" for v, n in (ids[0], ids[k], ids[k + 1])))
            replace[i] = "\n".join(out)
            break

    # cabin vertices that reach the hull: pull them back in, a few centimetres short of the skin at that height
    hull = [verts[v] for _, g, f in faces if g == "a320_exterior" for v, _ in f]
    used = {}
    for _, g, f in faces:
        for v, _ in f:
            used.setdefault(v, set()).add(g)
    moved = 0
    for v, groups in used.items():
        if groups != {"a320_interior"}:
            continue
        p = verts[v]
        near = [abs(h[0]) for h in hull if h[0] * p[0] > 0 and abs(h[2] - p[2]) < 0.6 and abs(h[1] - p[1]) < 0.2]
        if not near:
            continue
        skin = max(near)
        if abs(p[0]) > skin - 0.04:
            p[0] = math.copysign(skin - 0.06, p[0])
            lines[v_line(lines, v)] = "v %.6f %.6f %.6f" % tuple(p)
            moved += 1

    for i, text in replace.items():
        lines[i] = text
    vn_text = "\n".join("vn %.6f %.6f %.6f" % tuple(n) for n in new_vn)
    v_text = "\n".join("v %.6f %.6f %.6f" % tuple(p) for p in new_v)
    if new_vn:
        lines[last_vn] += "\n" + vn_text
    if new_v:
        lines[last_v] += "\n" + v_text
    lines.insert(0, MARK)
    open(OBJ, "w").write("\n".join(lines))
    print(f"hull triangles cut: {len(replace)}, new vertices: {len(new_v)}, cabin vertices pulled in: {moved}")


_v_lines = None


def v_line(lines, v):
    global _v_lines
    if _v_lines is None:
        _v_lines = [i for i, line in enumerate(lines) if line.startswith("v ")]
    return _v_lines[v - 1]


def cut(ps, ns, y0, y1, z0, z1):
    """The triangle minus the rectangle, as convex polygons of (point, normal); None when the triangle is not side-facing."""
    a, b, c = ps
    nx, ny, nz = cross(sub(b, a), sub(c, a))
    if abs(nx) < 0.3 * math.sqrt(nx * nx + ny * ny + nz * nz):
        return None
    tri = [(p[1], p[2]) for p in ps]
    regions = [
        [(lambda q: z0 - q[1])],
        [(lambda q: q[1] - z1)],
        [(lambda q: q[1] - z0), (lambda q: z1 - q[1]), (lambda q: y0 - q[0])],
        [(lambda q: q[1] - z0), (lambda q: z1 - q[1]), (lambda q: q[0] - y1)],
    ]
    out = []
    for planes in regions:
        poly = tri
        for keep in planes:
            poly = clip(poly, keep)
            if len(poly) < 3:
                break
        if len(poly) >= 3 and area(poly) > 1e-6:
            out.append([lift(q, ps, ns) for q in poly])
    return out


def clip(poly, keep):
    """Sutherland-Hodgman against one half-plane: keep(q) >= 0 is inside."""
    out = []
    for k in range(len(poly)):
        p, q = poly[k], poly[(k + 1) % len(poly)]
        dp, dq = keep(p), keep(q)
        if dp >= 0:
            out.append(p)
        if (dp >= 0) != (dq >= 0):
            t = dp / (dp - dq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out


def lift(q, ps, ns):
    """Back to 3D on the triangle's plane, with the normal blended by barycentric weights."""
    (y1, z1), (y2, z2), (y3, z3) = [(p[1], p[2]) for p in ps]
    det = (z2 - z3) * (y1 - y3) + (y3 - y2) * (z1 - z3)
    w1 = ((z2 - z3) * (q[0] - y3) + (y3 - y2) * (q[1] - z3)) / det
    w2 = ((z3 - z1) * (q[0] - y3) + (y1 - y3) * (q[1] - z3)) / det
    w3 = 1 - w1 - w2
    point = [w1 * ps[0][i] + w2 * ps[1][i] + w3 * ps[2][i] for i in range(3)]
    point[1], point[2] = q
    normal = [w1 * ns[0][i] + w2 * ns[1][i] + w3 * ns[2][i] for i in range(3)]
    return point, normal


def area(poly):
    return abs(sum(poly[k][0] * poly[(k + 1) % len(poly)][1] - poly[(k + 1) % len(poly)][0] * poly[k][1] for k in range(len(poly)))) / 2


def sub(p, q):
    return [p[i] - q[i] for i in range(3)]


def cross(u, w):
    return u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]


if __name__ == "__main__":
    main()
