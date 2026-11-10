#!/usr/bin/env python3
"""A320 nose and cockpit windows, as a Blockbench project.

    python3 tools/a320_nose.py          # writes tools/a320/a320_nose.bbmodel (kept out of the jar)
    python3 tools/a320_nose_splice.py   # puts the bbmodel's nose_* meshes into a320.obj

The nose is lofted from z 14.70 (just behind door L1, where it meets the fuselage ring) to the tip at z 19.32.
Profile from side photos of G-EUYF and HB-IHY (Commons) scaled to the 4.14 m fuselage: the tip sits 0.7 m
below the fuselage axis, the chin is nearly flat and the top line rises at about 30 degrees through the
windshield. Six cockpit panes: two windshields, two sliding side windows, two rear side windows.
The bbmodel is the source: edit it in Blockbench, then run the splice.
"""
import base64
import json
import math
import os
import struct
import subprocess
import uuid
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OBJ_PATH = "fabric/src/main/resources/assets/mtr/models/a320_200/a320.obj"
BASE_COMMIT = "4f1aa883a"  # the A320 OBJ before the nose was replaced
SEAM_Z = 14.70
TIP_Z = 19.32
LENGTH = TIP_Z - SEAM_Z
AROUND = 128
SCALE = 16  # Blockbench pixels per metre (one block)


def pchip(xs, ys):
	"""Monotone cubic through the points (Fritsch-Carlson), so the profile never overshoots."""
	n = len(xs)
	h = [xs[i + 1] - xs[i] for i in range(n - 1)]
	delta = [(ys[i + 1] - ys[i]) / h[i] for i in range(n - 1)]
	m = [0.0] * n
	m[0], m[-1] = delta[0], delta[-1]
	for i in range(1, n - 1):
		if delta[i - 1] * delta[i] <= 0:
			m[i] = 0.0
		else:
			w1, w2 = 2 * h[i] + h[i - 1], h[i] + 2 * h[i - 1]
			m[i] = (w1 + w2) / (w1 / delta[i - 1] + w2 / delta[i])

	def f(x):
		if x <= xs[0]:
			return ys[0]
		if x >= xs[-1]:
			return ys[-1]
		i = max(j for j in range(n - 1) if xs[j] <= x)
		t = (x - xs[i]) / h[i]
		t2, t3 = t * t, t * t * t
		return ((2 * t3 - 3 * t2 + 1) * ys[i] + (t3 - 2 * t2 + t) * h[i] * m[i]
				+ (-2 * t3 + 3 * t2) * ys[i + 1] + (t3 - t2) * h[i] * m[i + 1])
	return f


def base_obj():
	return subprocess.run(["git", "-C", ROOT, "show", BASE_COMMIT + ":" + OBJ_PATH], capture_output=True, text=True, check=True).stdout


def parse_obj(text):
	verts, normals, faces = [], [], []
	group = material = None
	for line in text.splitlines():
		p = line.split()
		if not p:
			continue
		if p[0] == "v":
			verts.append(tuple(map(float, p[1:4])))
		elif p[0] == "vn":
			normals.append(tuple(map(float, p[1:4])))
		elif p[0] == "g":
			group = p[1]
		elif p[0] == "usemtl":
			material = p[1]
		elif p[0] == "f":
			corners = []
			for c in p[1:]:
				parts = c.split("/")
				corners.append((int(parts[0]), int(parts[2]) if len(parts) > 2 and parts[2] else 0))
			faces.append((group, material, corners))
	return verts, normals, faces


def seam_ring(verts, faces):
	"""The fuselage's ring at SEAM_Z, ordered by angle from the top, going round the +x side first."""
	ring = {}
	for group, material, corners in faces:
		if group == "a320_exterior" and material == "body":
			for v, _ in corners:
				x, y, z = verts[v - 1]
				if abs(z - SEAM_Z) < 0.004:
					ring[(round(x, 4), round(y, 4))] = (x, y, z)
	pts = list(ring.values())
	ys = [p[1] for p in pts]
	centre = (max(ys) + min(ys)) / 2
	pts.sort(key=lambda p: (math.atan2(p[0], p[1] - centre)) % (2 * math.pi))
	return pts


# Profiles by distance back from the tip (metres); the last value of each is replaced by the fuselage ring's own.
# traced from the G-EUYF photo's outline: shallow radome, a knee at the windshield's foot, the windshield at about
# 50 degrees, then the cockpit roof
D_TOP = [0, 0.03, 0.07, 0.19, 0.36, 0.60, 1.02, 1.40, 1.85, 2.26, 2.67, 3.09, 3.50, 4.08, LENGTH]
Y_TOP = [3.27, 3.42, 3.50, 3.65, 3.80, 3.97, 4.17, 4.33, 4.86, 5.17, 5.38, 5.55, 5.70, 5.86, None]
D_BOT = [0, 0.03, 0.10, 0.20, 0.35, 0.65, 1.06, 1.47, 2.30, 3.13, 3.96, LENGTH]
Y_BOT = [3.27, 3.10, 2.98, 2.86, 2.74, 2.58, 2.42, 2.30, 2.10, 1.99, 1.92, None]
D_W = [0, 0.03, 0.10, 0.20, 0.35, 0.65, 1.06, 1.43, 1.90, 2.30, 3.13, 3.96, LENGTH]
W = [0, 0.20, 0.36, 0.50, 0.66, 0.92, 1.18, 1.37, 1.55, 1.67, 1.85, 1.93, None]
D_C = [0, 0.35, 0.65, 1.06, 1.47, 2.30, 3.13, LENGTH]
Y_C = [3.27, 3.40, 3.52, 3.64, 3.72, 3.83, 3.90, None]
# superellipse exponent of the upper half: 2 is round; lower round the cockpit, where the windshields and side
# windows sit on flatter, steeper sides than a round section has
D_N = [0, 1.0, 1.6, 2.5, 3.3, 4.1, LENGTH]
N_UP = [2.0, 1.9, 1.62, 1.62, 1.8, 2.0, 2.0]


class Nose:
	def __init__(self, ring):
		xs = [p[0] for p in ring]
		ys = [p[1] for p in ring]
		self.ring = ring
		top, bot, w = max(ys), min(ys), max(xs)
		yc = sum(p[1] for p in ring if p[0] > w - 0.02) / len([p for p in ring if p[0] > w - 0.02])
		self.top = pchip(D_TOP, Y_TOP[:-1] + [top])
		self.bot = pchip(D_BOT, Y_BOT[:-1] + [bot])
		self.w = pchip(D_W, W[:-1] + [w])
		self.yc = pchip(D_C, Y_C[:-1] + [yc])
		self.n = pchip(D_N, N_UP)

	def point(self, d, phi):
		"""phi: 0 on top, 90 at the widest point on +x, 180 underneath, negative on -x."""
		a = math.radians(phi)
		yc = self.yc(d)
		s, c = math.sin(a), math.cos(a)
		if c >= 0:
			e = 2.0 / self.n(d)
			return (self.w(d) * math.copysign(abs(s) ** e, s), yc + (self.top(d) - yc) * c ** e, TIP_Z - d)
		return (self.w(d) * s, yc + (yc - self.bot(d)) * c, TIP_Z - d)

	def normal(self, d, phi, eps=1e-3):
		p = self.point(d, phi)
		a = self.point(min(d + eps, LENGTH), phi)
		b = self.point(max(d - eps, 0), phi)
		c = self.point(d, phi + 0.05)
		e = self.point(d, phi - 0.05)
		u = [a[i] - b[i] for i in range(3)]
		v = [c[i] - e[i] for i in range(3)]
		n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
		# point outward: away from the axis at this station
		out = [p[0], p[1] - self.yc(d), 0.25]
		if sum(n[i] * out[i] for i in range(3)) < 0:
			n = [-x for x in n]
		ln = math.sqrt(sum(x * x for x in n)) or 1
		return [x / ln for x in n]

	def half_width(self, d, y):
		"""How far out the surface is at height y on the section at d (0 above the top or below the bottom)."""
		yc = self.yc(d)
		if y >= yc:
			c = (y - yc) / max(self.top(d) - yc, 1e-6)
			return 0.0 if c >= 1 else self.w(d) * (1 - c ** self.n(d)) ** (1 / self.n(d))
		c = (yc - y) / max(yc - self.bot(d), 1e-6)
		return 0.0 if c >= 1 else self.w(d) * math.sqrt(1 - c * c)

	def at(self, x, y):
		"""The surface point seen at (x, y) from straight ahead: the station where the section reaches x at height y."""
		lo, hi = 0.0, LENGTH
		for _ in range(60):
			mid = (lo + hi) / 2
			if self.half_width(mid, y) < abs(x):
				lo = mid
			else:
				hi = mid
		d = (lo + hi) / 2
		return d, self.phi_at(d, y, 1 if x >= 0 else -1)

	def phi_at(self, d, y, side=1):
		"""The angle where the section at d reaches height y on the +x (or -x) side."""
		yc = self.yc(d)
		if y >= yc:
			c = min(1.0, (y - yc) / max(self.top(d) - yc, 1e-6)) ** (self.n(d) / 2.0)
		else:
			c = (y - yc) / max(yc - self.bot(d), 1e-6)
		return side * math.degrees(math.acos(max(-1.0, min(1.0, c))))


def stations():
	"""Ring stations from the tip back to the seam, closest round the cockpit where the windshield facet sits."""
	out, d = [0.0], 0.0
	while d < LENGTH:
		step = 0.025 + 0.05 * min(1.0, d / 0.6) if d < 1.0 else (0.05 if d < 3.6 else 0.11)
		d = min(LENGTH, d + step)
		out.append(d)
	if LENGTH - out[-2] < 0.03:
		del out[-2]
	return out


def body_mesh(nose):
	verts, faces = [], []
	ds = stations()  # ascending from the tip
	tip = len(verts)
	verts.append((0.0, nose.top(0), TIP_Z))
	rings = []
	for d in ds[1:-1]:
		ring = []
		for k in range(AROUND):
			ring.append(len(verts))
			verts.append(nose.point(d, k * 360.0 / AROUND))
		rings.append(ring)
	# the fuselage's own ring, 32 vertices, joined to the 64-vertex rings by fans
	seam = []
	for p in nose.ring:
		seam.append(len(verts))
		verts.append(p)
	first = rings[0]
	for k in range(AROUND):
		faces.append([tip, first[(k + 1) % AROUND], first[k]])
	for r in range(len(rings) - 1):
		a, b = rings[r], rings[r + 1]
		for k in range(AROUND):
			k2 = (k + 1) % AROUND
			faces.append([a[k], a[k2], b[k2], b[k]])
	last = rings[-1]
	m = len(seam)
	step = AROUND // m
	middle = (step + 1) // 2
	for i in range(m):
		o0, o1 = seam[i], seam[(i + 1) % m]
		new = [last[(step * i + j) % AROUND] for j in range(step + 1)]
		for j in range(step):
			faces.append([new[j], new[j + 1], o0 if j < step / 2 else o1])
		faces.append([o0, new[middle], o1])
	return verts, faces


# Cockpit panes in (d, y) side-view terms for the +x side, mirrored to -x. The windshield's inner edge is
# given as an angle from the top centre line instead, because it runs along the top.
# measured on the G-EUYF photo (side) and D-AIQT (front)
# The windshields are flat panes, given as seen from straight ahead (x, y): inner-bottom, outer-bottom, outer-top,
# inner-top. Their bottom and top edges are near level, the outer edge leans in. The hull under them is flattened
# into the same plane, so each pane sits in a flat facet like the real frame.
WINDSHIELD = [(0.035, 4.30), (1.365, 4.27), (1.12, 4.70), (0.035, 4.75)]
FACET_FLAT = 0.04  # the facet runs this far past the pane, under its seal
FACET_BLEND = 0.30  # metres over which the hull then eases back to its round section where the facet cuts in
FACET_OUT = 0.04  # where it builds out: past the seal (BORDER) it eases back over this much, clear of the side window
POST_GAP = 0.07  # the post between the windshield and the sliding window, along the surface
# The side windows in side-view terms (d, y): bottom-front, top-front, top-rear, bottom-rear. The sliding window's
# front corners are placed POST_GAP behind the windshield's outer corners at these heights.
SLIDING = [(None, 4.21), (None, 4.66), (2.71, 4.65), (2.71, 4.11)]
REAR = [(2.89, 4.08), (2.89, 4.64), (3.33, 4.64), (3.40, 4.07)]
BORDER = 0.04
PITOTS = [(3.05, 3.80), (3.35, 3.55)]  # (d, y): two probes a side, ahead of door 1
SENSOR_PLATE = (1.92, 3.10, 0.43, 0.18)  # (d, y, outer, inner): the square flush plate under the windshield
RADOME = (0.82, 4.08, 0.25)  # the radome joint: at d 0.82 on the crown, this much further back per metre lower
LIFT_GLASS = 0.015
LIFT_MASK = 0.008
HIGHLIGHT = 0.62  # upper part of each pane shows sky


def windshield(nose, side):
	"""The windshield's plane (point, outward normal) and its four corners in 3D, on that plane."""
	corners = [nose.point(*nose.at(side * x, y)) for x, y in WINDSHIELD]
	centre = [sum(c[i] for c in corners) / 4 for i in range(3)]
	a = [corners[2][i] - corners[0][i] for i in range(3)]
	b = [corners[3][i] - corners[1][i] for i in range(3)]
	n = [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]
	if n[2] < 0:
		n = [-v for v in n]
	ln = math.sqrt(sum(v * v for v in n))
	n = [v / ln for v in n]
	# fit the plane to the hull along the pane's border, the outer edge most of all: the post between the windshield
	# and the sliding window then needs next to no build-out, so the facet stays clear of the sliding window
	samples = []
	for e, weight in ((0, 3.0), (1, 4.0), (2, 1.0), (3, 4.0)):
		(x0, y0), (x1, y1) = WINDSHIELD[e], WINDSHIELD[(e + 1) % 4]
		for i in range(9):
			t = i / 8
			samples.append((nose.point(*nose.at(side * (x0 + (x1 - x0) * t), y0 + (y1 - y0) * t)), weight))
	n, centre = fit_plane(samples, n)
	flat = [tuple(c[i] - n[i] * sum((c[k] - centre[k]) * n[k] for k in range(3)) for i in range(3)) for c in corners]
	return centre, n, flat


def fit_plane(samples, guess):
	"""Weighted least-squares plane through (point, weight) samples: (unit normal on the guess's side, centroid)."""
	total = sum(w for _, w in samples)
	c = [sum(p[i] * w for p, w in samples) / total for i in range(3)]
	m = [[sum(w * (p[i] - c[i]) * (p[j] - c[j]) for p, w in samples) for j in range(3)] for i in range(3)]
	# the smallest eigenvector, by inverse iteration from the guess
	n = list(guess)
	for _ in range(30):
		a = [[m[i][j] + (1e-9 if i == j else 0) for j in range(3)] for i in range(3)]
		det = (a[0][0] * (a[1][1] * a[2][2] - a[1][2] * a[2][1]) - a[0][1] * (a[1][0] * a[2][2] - a[1][2] * a[2][0])
				+ a[0][2] * (a[1][0] * a[2][1] - a[1][1] * a[2][0]))
		inv = [[(a[(j + 1) % 3][(i + 1) % 3] * a[(j + 2) % 3][(i + 2) % 3] - a[(j + 1) % 3][(i + 2) % 3] * a[(j + 2) % 3][(i + 1) % 3]) / det
				for j in range(3)] for i in range(3)]
		n = [sum(inv[i][j] * n[j] for j in range(3)) for i in range(3)]
		ln = math.sqrt(sum(v * v for v in n))
		n = [v / ln for v in n]
	if sum(n[i] * guess[i] for i in range(3)) < 0:
		n = [-v for v in n]
	return n, c


def plane_axes(n):
	u = [n[1] * 0 - n[2] * 1, n[2] * 0 - n[0] * 0, n[0] * 1 - n[1] * 0]  # n x up... any vector in the plane
	u = [-n[2], 0.0, n[0]]
	lu = math.sqrt(sum(v * v for v in u)) or 1
	u = [v / lu for v in u]
	v = [n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]]
	return u, v


def outside_distance(p2, poly):
	"""How far a 2D point is outside a convex polygon (0 when inside)."""
	worst = 0.0
	inside = True
	for i in range(len(poly)):
		a, b = poly[i], poly[(i + 1) % len(poly)]
		ex, ey = b[0] - a[0], b[1] - a[1]
		px, py = p2[0] - a[0], p2[1] - a[1]
		cross = ex * py - ey * px
		if cross < 0:
			inside = False
		t = max(0.0, min(1.0, (px * ex + py * ey) / (ex * ex + ey * ey)))
		dx, dy = p2[0] - (a[0] + ex * t), p2[1] - (a[1] + ey * t)
		worst = max(worst, 0.0) if inside else worst
		if cross < 0:
			worst = max(worst, math.hypot(dx, dy))
	return 0.0 if inside else worst


def facet(nose, verts, skip):
	"""Flatten the hull into each windshield's plane, easing back to the round section over FACET_BLEND."""
	shields = []
	for side in (1, -1):
		c, n, flat = windshield(nose, side)
		u, v = plane_axes(n)
		poly = [(sum((p[i] - c[i]) * u[i] for i in range(3)), sum((p[i] - c[i]) * v[i] for i in range(3))) for p in flat]
		# keep the polygon counter-clockwise for outside_distance
		area = sum(poly[i][0] * poly[(i + 1) % 4][1] - poly[(i + 1) % 4][0] * poly[i][1] for i in range(4))
		if area < 0:
			poly = poly[::-1]
		# how deep the hull sits under each stretch of the pane's edge, so the cut-in outside the pane never goes
		# deeper than at the edge: a clean break at the seal and then an even fade, no trough
		edge = []
		for e in range(4):
			a, b = flat[e], flat[(e + 1) % 4]
			for i in range(16):
				t = i / 16
				x = [a[k] + (b[k] - a[k]) * t for k in range(3)]
				hull = nose.point(*nose.at(x[0], x[1]))
				edge.append(((sum((x[k] - c[k]) * u[k] for k in range(3)), sum((x[k] - c[k]) * v[k] for k in range(3))),
						max(0.0, sum((hull[k] - c[k]) * n[k] for k in range(3)))))
		shields.append((side, c, n, u, v, poly, edge))
	out = []
	for k, p in enumerate(verts):
		if k in skip:
			out.append(p)
			continue
		for side, c, n, u, v, poly, edge in shields:
			if (p[0] >= 0) != (side > 0):
				continue
			s = sum((p[i] - c[i]) * n[i] for i in range(3))
			if abs(s) > 0.3:
				continue
			q = (sum((p[i] - c[i]) * u[i] for i in range(3)), sum((p[i] - c[i]) * v[i] for i in range(3)))
			# cut in gently, build out only right at the pane, so no bulge shows round its corners
			t = max(0.0, outside_distance(q, poly) - (FACET_FLAT if s > 0 else BORDER * 0.6)) / (FACET_BLEND if s > 0 else FACET_OUT)
			if t >= 1:
				continue
			# cutting in: one crease at the seal, then an even fade (1 - t)^2; building out: smootherstep
			w = (1 - t) ** 2 if s > 0 else 1 - t * t * t * (t * (6 * t - 15) + 10)
			if s > 0 and outside_distance(q, poly) > 0:
				# the edge depth nearby, weighted down with distance, so the limit runs smoothly round the corners
				s = min(s, max(depth * max(0.0, min(1.0, (0.35 - math.hypot(e[0] - q[0], e[1] - q[1])) / 0.2)) for e, depth in edge))
			p = tuple(p[i] - n[i] * s * w for i in range(3))
		out.append(p)
	return out


def pane_corners(nose, side):
	"""The side windows as four (d, phi) corners: bottom-front, top-front, top-rear, bottom-rear."""
	ob = nose.at(side * WINDSHIELD[1][0], WINDSHIELD[1][1])[0]
	ot = nose.at(side * WINDSHIELD[2][0], WINDSHIELD[2][1])[0]
	sliding = [(ob + POST_GAP if d is None and i == 0 else ot + POST_GAP if d is None else d, y) for i, (d, y) in enumerate(SLIDING)]
	return [[(d, nose.phi_at(d, y, side)) for d, y in quad] for quad in (sliding, REAR)]


def flat_pane(corners, n, lift, rows, cols, split=None, grow_by=0.0):
	"""A grid over a flat quad (bottom-front, bottom-inner... any order round the quad), lifted along n."""
	c = [sum(p[i] for p in corners) / 4 for i in range(3)]
	if grow_by:
		corners = [tuple(c[i] + (p[i] - c[i]) * (1 + grow_by / math.dist(p, c)) for i in range(3)) for p in corners]
	verts, lower, upper = [], [], []
	index = {}
	for i in range(cols + 1):
		for j in range(rows + 1):
			s, t = i / cols, j / rows
			b = [corners[0][k] + (corners[1][k] - corners[0][k]) * s for k in range(3)]
			top = [corners[3][k] + (corners[2][k] - corners[3][k]) * s for k in range(3)]
			index[(i, j)] = len(verts)
			verts.append(tuple(b[k] + (top[k] - b[k]) * t + n[k] * lift for k in range(3)))
	for i in range(cols):
		for j in range(rows):
			f = [index[(i, j)], index[(i + 1, j)], index[(i + 1, j + 1)], index[(i, j + 1)]]
			(upper if split is not None and (j + 0.5) / rows > split else lower).append(f)
	return verts, lower, upper


def bilinear(q, s, t):
	(d0, p0), (d1, p1), (d2, p2), (d3, p3) = q  # bottom-front, top-front, top-rear, bottom-rear
	bd = d0 + (d3 - d0) * s
	bp = p0 + (p3 - p0) * s
	td = d1 + (d2 - d1) * s
	tp = p1 + (p2 - p1) * s
	return bd + (td - bd) * t, bp + (tp - bp) * t


def grow(q, metres, nose):
	"""The quad pushed out by about `metres` along the surface, for the dark seal round each pane."""
	cd = sum(c[0] for c in q) / 4
	cp = sum(c[1] for c in q) / 4
	out = []
	for d, p in q:
		x0 = nose.point(d, p)
		x1 = nose.point(cd, cp)
		dist = math.dist(x0, x1) or 1
		f = 1 + metres / dist
		out.append((cd + (d - cd) * f, cp + (p - cp) * f))
	return out


def pane_mesh(nose, quad, lift, rows, cols, split=None):
	"""A grid over the quad lying on the (faceted) nose surface, lifted along the normal. Returns (verts, lower, upper)."""
	verts, lower, upper = [], [], []
	index = {}
	points, normals = [], []
	for i in range(cols + 1):
		for j in range(rows + 1):
			d, p = bilinear(quad, i / cols, j / rows)
			points.append(nose.point(d, p))
			normals.append(nose.normal(d, p))
	points = facet(nose, points, set())
	k = 0
	for i in range(cols + 1):
		for j in range(rows + 1):
			x, n = points[k], normals[k]
			k += 1
			index[(i, j)] = len(verts)
			verts.append(tuple(x[m] + n[m] * lift for m in range(3)))
	for i in range(cols):
		for j in range(rows):
			f = [index[(i, j)], index[(i + 1, j)], index[(i + 1, j + 1)], index[(i, j + 1)]]
			(upper if split is not None and (j + 0.5) / rows > split else lower).append(f)
	return verts, lower, upper


def orient(verts, faces, nose_out=True):
	"""Wind every face so its normal points away from the fuselage axis."""
	out = []
	for f in faces:
		a, b, c = verts[f[0]], verts[f[1]], verts[f[2]]
		u = [b[i] - a[i] for i in range(3)]
		v = [c[i] - a[i] for i in range(3)]
		n = [u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0]]
		centre = [sum(verts[k][i] for k in f) / len(f) for i in range(3)]
		radial = [centre[0], centre[1] - 3.9, max(0.0, centre[2] - 17.5)]
		if (sum(n[i] * radial[i] for i in range(3)) < 0) == nose_out:
			f = list(reversed(f))
		out.append(f)
	return out


def box(verts, faces, a, b, half):
	"""A square tube from a to b, `half` metres either side of the line, closed at both ends."""
	axis = [b[i] - a[i] for i in range(3)]
	la = math.sqrt(sum(v * v for v in axis))
	axis = [v / la for v in axis]
	ref = [0, 1, 0] if abs(axis[1]) < 0.9 else [1, 0, 0]
	u = [axis[1] * ref[2] - axis[2] * ref[1], axis[2] * ref[0] - axis[0] * ref[2], axis[0] * ref[1] - axis[1] * ref[0]]
	lu = math.sqrt(sum(v * v for v in u))
	u = [v / lu * half for v in u]
	v = [axis[1] * u[2] - axis[2] * u[1], axis[2] * u[0] - axis[0] * u[2], axis[0] * u[1] - axis[1] * u[0]]
	off = len(verts)
	for end in (a, b):
		for su, sv in ((-1, -1), (1, -1), (1, 1), (-1, 1)):
			verts.append(tuple(end[i] + u[i] * su + v[i] * sv for i in range(3)))
	for k in range(4):
		k2 = (k + 1) % 4
		faces.append([off + k, off + k2, off + 4 + k2, off + 4 + k])
	faces.append([off + 3, off + 2, off + 1, off])
	faces.append([off + 4, off + 5, off + 6, off + 7])


def tube(verts, faces, a, b, r0, r1, sides=8):
	"""A round tube (a `sides`-gon) from a to b, radius r0 at a tapering to r1 at b, closed at both ends."""
	axis = [b[i] - a[i] for i in range(3)]
	la = math.sqrt(sum(v * v for v in axis))
	axis = [v / la for v in axis]
	ref = [0, 1, 0] if abs(axis[1]) < 0.9 else [1, 0, 0]
	u = [axis[1] * ref[2] - axis[2] * ref[1], axis[2] * ref[0] - axis[0] * ref[2], axis[0] * ref[1] - axis[1] * ref[0]]
	lu = math.sqrt(sum(v * v for v in u))
	u = [v / lu for v in u]
	v = [axis[1] * u[2] - axis[2] * u[1], axis[2] * u[0] - axis[0] * u[2], axis[0] * u[1] - axis[1] * u[0]]
	off = len(verts)
	for end, r in ((a, r0), (b, r1)):
		for k in range(sides):
			ang = 2 * math.pi * k / sides
			verts.append(tuple(end[i] + (u[i] * math.cos(ang) + v[i] * math.sin(ang)) * r for i in range(3)))
	for k in range(sides):
		k2 = (k + 1) % sides
		faces.append([off + k, off + k2, off + sides + k2, off + sides + k])
	faces.append([off + k for k in reversed(range(sides))])
	faces.append([off + sides + k for k in range(sides)])


def nose_meshes():
	verts, normals, faces = parse_obj(base_obj())
	nose = Nose(seam_ring(verts, faces))
	meshes = {}
	bv, bf = body_mesh(nose)
	seam = set(range(len(bv) - len(nose.ring), len(bv)))
	bv = facet(nose, bv, seam)
	meshes["nose_body"] = ("body", bv, orient(bv, bf))
	glass_v, glass_lo, glass_hi, mask_v, mask_f = [], [], [], [], []
	for side in (1, -1):
		_, n, flat = windshield(nose, side)
		v, lo, hi = flat_pane(flat, n, LIFT_GLASS, 4, 6, HIGHLIGHT)
		off = len(glass_v)
		glass_v += v
		glass_lo += [[k + off for k in f] for f in lo]
		glass_hi += [[k + off for k in f] for f in hi]
		v, lo, _ = flat_pane(flat, n, LIFT_MASK, 1, 1, grow_by=BORDER)
		off = len(mask_v)
		mask_v += v
		mask_f += [[k + off for k in f] for f in lo]
		for q in pane_corners(nose, side):
			v, lo, hi = pane_mesh(nose, q, LIFT_GLASS, 8, 12, HIGHLIGHT)
			off = len(glass_v)
			glass_v += v
			glass_lo += [[k + off for k in f] for f in lo]
			glass_hi += [[k + off for k in f] for f in hi]
			v, lo, _ = pane_mesh(nose, grow(q, BORDER, nose), LIFT_MASK, 8, 12)
			off = len(mask_v)
			mask_v += v
			mask_f += [[k + off for k in f] for f in lo]
	meshes["nose_glass"] = ("glass", glass_v, orient(glass_v, glass_lo))
	meshes["nose_glass_hi"] = ("glass_hi", glass_v, orient(glass_v, glass_hi))
	# wipers, parked along the foot of each windshield
	for side in (1, -1):
		_, n, flat = windshield(nose, side)
		ib, ob = flat[0], flat[1]
		up = [flat[3][i] - flat[0][i] for i in range(3)]
		lu = math.sqrt(sum(v * v for v in up))
		up = [v / lu for v in up]
		a = [ib[i] + (ob[i] - ib[i]) * 0.12 + up[i] * 0.05 + n[i] * 0.025 for i in range(3)]
		b = [ib[i] + (ob[i] - ib[i]) * 0.80 + up[i] * 0.05 + n[i] * 0.025 for i in range(3)]
		off = len(mask_v)
		mask_v += [tuple(a), tuple(b), tuple(b[i] + up[i] * 0.045 for i in range(3)), tuple(a[i] + up[i] * 0.045 for i in range(3))]
		mask_f.append([off, off + 1, off + 2, off + 3])
		# the arm, from near the blade's outer end down to its pivot below the glass
		arm0 = [a[i] + (b[i] - a[i]) * 0.85 + up[i] * 0.02 for i in range(3)]
		arm1 = [ib[i] + (ob[i] - ib[i]) * 0.86 - up[i] * 0.05 + n[i] * 0.02 for i in range(3)]
		side_dir = [ob[i] - ib[i] for i in range(3)]
		ls = math.sqrt(sum(v * v for v in side_dir))
		side_dir = [v / ls * 0.012 for v in side_dir]
		off = len(mask_v)
		mask_v += [tuple(arm0[i] - side_dir[i] for i in range(3)), tuple(arm0[i] + side_dir[i] for i in range(3)),
				tuple(arm1[i] + side_dir[i] for i in range(3)), tuple(arm1[i] - side_dir[i] for i in range(3))]
		mask_f.append([off, off + 1, off + 2, off + 3])
	meshes["nose_mask"] = ("mask", mask_v, orient(mask_v, mask_f))
	# pitot probes under the side windows: a short mast out of the skin, then a tube pointing forward
	probe_v, probe_f = [], []
	frame_v, frame_f, body_v, body_f = [], [], [], []
	for side in (1, -1):
		for d, y in PITOTS:
			phi = nose.phi_at(d, y, side)
			base = nose.point(d, phi)
			n = nose.normal(d, phi)
			tip = [base[i] + n[i] * 0.12 for i in range(3)]
			tube(probe_v, probe_f, [base[i] - n[i] * 0.01 for i in range(3)], tip, 0.035, 0.022)
			tube(probe_v, probe_f, [tip[0], tip[1], tip[2] - 0.04], [tip[0], tip[1], tip[2] + 0.4], 0.028, 0.016)
			# a flush base plate, outlined like the other panels, so the probe sits on something
			box(frame_v, frame_f, [base[i] - n[i] * 0.01 for i in range(3)], [base[i] + n[i] * 0.004 for i in range(3)], 0.07)
	meshes["nose_probe"] = ("frame", probe_v, probe_f)
	# the flush square plate under the windshield: a light outline round a dark square, as on the photos
	d, y, outer, inner = SENSOR_PLATE
	for side in (1, -1):
		phi = nose.phi_at(d, y, side)
		base = nose.point(d, phi)
		n = nose.normal(d, phi)
		for half, lift, verts_, faces_ in ((outer / 2, 0.004, frame_v, frame_f), (outer / 2 - 0.015, 0.006, body_v, body_f), (inner / 2, 0.008, probe_v, probe_f)):  # grey centre, like the photo
			box(verts_, faces_, [base[i] - n[i] * 0.01 for i in range(3)], [base[i] + n[i] * lift for i in range(3)], half)
	meshes["nose_sensor_rim"] = ("panel_line", frame_v, frame_f)
	meshes["nose_sensor_face"] = ("body", body_v, body_f)
	# the radome joint: a thin dark band just ahead of the windshield
	band_v, band_f = [], []
	for k in range(AROUND):
		phi = k * 360.0 / AROUND
		d = RADOME[0]
		for _ in range(20):  # the joint leans back lower down: d = d0 + (y0 - y) * slope
			d = RADOME[0] + (RADOME[1] - nose.point(d, phi)[1]) * RADOME[2]
		for dd in (d, d + 0.015):
			band_v.append(tuple(a + b * 0.004 for a, b in zip(nose.point(dd, phi), nose.normal(dd, phi))))
	for k in range(AROUND):
		k2 = (k + 1) % AROUND
		band_f.append([2 * k, 2 * k2, 2 * k2 + 1, 2 * k + 1])
	meshes["nose_panel_line"] = ("panel_line", band_v, orient(band_v, band_f))
	return verts, faces, meshes


# ---- Blockbench file -------------------------------------------------------------------------------------------

COLOURS = {}


def mtl_colours():
	text = subprocess.run(["git", "-C", ROOT, "show", BASE_COMMIT + ":" + OBJ_PATH.replace(".obj", ".mtl")],
			capture_output=True, text=True, check=True).stdout
	name = None
	for line in text.splitlines():
		p = line.split()
		if p and p[0] == "newmtl":
			name = p[1]
		elif p and p[0] == "Kd" and name:
			COLOURS[name] = tuple(int(round(float(c) * 255)) for c in p[1:4])


def png(rgb):
	raw = b"".join(b"\x00" + bytes(rgb) * 16 for _ in range(16))

	def chunk(kind, data):
		return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)
	return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 16, 16, 8, 2, 0, 0, 0))
			+ chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def stable_uuid(name):
	return str(uuid.uuid5(uuid.NAMESPACE_URL, "atr/a320_nose/" + name))


def bb_mesh(name, material_index, verts, faces, locked=False, smooth=False):
	keys = ["v%d" % i for i in range(len(verts))]
	used = sorted({k for f in faces for k in f})
	return {
		"name": name, "color": 0, "origin": [0, 0, 0], "rotation": [0, 0, 0], "export": True, "visibility": True,
		"locked": locked, "render_order": "default", "allow_mirror_modeling": False, "shading": "smooth" if smooth else "flat",
		"vertices": {keys[i]: [round(verts[i][0] * SCALE, 4), round(verts[i][1] * SCALE, 4), round(verts[i][2] * SCALE, 4)] for i in used},
		"faces": {"f%d" % n: {"uv": {keys[k]: [0, 0] for k in f}, "vertices": [keys[k] for k in f], "texture": material_index}
				for n, f in enumerate(faces)},
		"type": "mesh", "uuid": stable_uuid(name),
	}


def write_bbmodel(path, obj_verts, obj_faces, meshes):
	mtl_colours()
	materials = sorted(COLOURS)
	textures = [{
		"path": "", "name": m, "folder": "", "namespace": "", "id": str(i), "width": 16, "height": 16, "uv_width": 16,
		"uv_height": 16, "particle": False, "use_as_default": False, "layers_enabled": False, "sync_to_project": "",
		"render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop", "frame_order": "",
		"frame_interpolate": False, "visible": True, "internal": True, "saved": False, "uuid": stable_uuid("tex/" + m),
		"source": "data:image/png;base64," + base64.b64encode(png(COLOURS[m])).decode(),
	} for i, m in enumerate(materials)]
	elements = []
	for name, (material, verts, faces) in meshes.items():
		elements.append(bb_mesh(name, materials.index(material), verts, faces, smooth=material == "body"))
	# the front fuselage around it, locked, so the nose is seen in place (the splice ignores ref_ meshes)
	by_material = {}
	for group, material, corners in obj_faces:
		if group == "a320_interior" or material not in COLOURS:
			continue
		zs = [obj_verts[v - 1][2] for v, _ in corners]
		if min(zs) < 9.0 or removed(group, material, zs):
			continue
		by_material.setdefault(material, []).append([v - 1 for v, _ in corners])
	for material, faces in sorted(by_material.items()):
		elements.append(bb_mesh("ref_" + material, materials.index(material), obj_verts, faces, locked=True, smooth=material == "body"))
	model = {
		"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
		"name": "a320_nose", "model_identifier": "", "visible_box": [1, 1, 0], "variable_placeholders": "",
		"variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
		"resolution": {"width": 16, "height": 16},
		"elements": elements,
		"outliner": [e["uuid"] for e in elements],
		"textures": textures,
	}
	with open(path, "w") as f:
		json.dump(model, f, separators=(",", ":"))


def removed(group, material, zs):
	"""The base OBJ's faces the new nose replaces: the old nose skin and the old cockpit panes."""
	if group == "a320_exterior" and material == "body" and min(zs) >= SEAM_Z - 0.002:
		return True
	return group == "a320_exterior_flat" and material in ("glass", "glass_hi", "mask") and min(zs) >= 15.9


if __name__ == "__main__":
	v, f, meshes = nose_meshes()
	out = os.path.join(ROOT, "tools/a320/a320_nose.bbmodel")
	write_bbmodel(out, v, f, meshes)
	print("wrote", out, {k: (len(m[1]), len(m[2])) for k, m in meshes.items()})
