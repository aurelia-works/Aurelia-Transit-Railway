#!/usr/bin/env python3
"""Compact hatchback for the road car, as Blockbench cubes.

    python3 tools/road_car.py

Writes the model the game loads and the item icon:

    fabric/src/main/resources/assets/mtr/models/road_vehicle/hatchback.bbmodel
    fabric/src/main/resources/assets/mtr/textures/entity/road_vehicle/hatchback.png
    fabric/src/main/resources/assets/mtr/textures/item/road_car.png

16 Blockbench pixels are one metre, and one metre is one block. The shell is a
generic five-door hatchback, 4.30 m long, 1.80 m wide and 1.45 m tall, with no
maker mark. Group names are the ones RoadVehicleRenderer / BlockbenchMesh use:
body is painted, glass is the transparent pass, and each wheel_* group rolls
about its own origin (the front pair also steer).

Render the four views with tools/road_car_render.py under Blender.
"""
import base64
import io
import json
import math
import os
import uuid

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_PATH = "fabric/src/main/resources/assets/mtr/models/road_vehicle/hatchback.bbmodel"
TEXTURE_PATH = "fabric/src/main/resources/assets/mtr/textures/entity/road_vehicle/hatchback.png"
ICON_PATH = "fabric/src/main/resources/assets/mtr/textures/item/road_car.png"

PX = 16.0  # pixels per metre
TEX = 256

# Brochure-class compact hatch, scaled so the body is 4.30 x 1.80 x 1.45.
HALF_L = 2.15 * PX          # nose +z, tail -z
WIDTH = 1.80 * PX
ARCH_X = 0.900 * PX         # outside of the arch lip
DOOR_X = 0.820 * PX         # outside of the doors, inboard of the wings
SKIN = 0.028 * PX
WHEEL_R = 0.315 * PX
WHEEL_Y = WHEEL_R
WHEELBASE = 2.64 * PX
FRONT_OH = 0.88 * PX
AXLE_F = HALF_L - FRONT_OH
AXLE_R = AXLE_F - WHEELBASE
WHEEL_X = 0.77 * PX         # half the track
TYRE_W = 0.205 * PX
RIM_R = 0.190 * PX
OPEN_R = 0.370 * PX         # arch opening, just clear of the tyre
LIP_T = 0.055 * PX
BELT_F = 0.830 * PX
BELT_R = 0.900 * PX
SILL = 0.360 * PX
ROCKER = 0.200 * PX
ROOF = 1.440 * PX
RAIL = 1.300 * PX
GLASS_BELT = 0.800 * PX
GLASS_RAIL = 0.690 * PX
CREASE = 0.640 * PX

# Shared corners, nose to tail. The side skin and the sheets use the same points.
LIP_Z, LIP_Y = 1.980 * PX, 0.700 * PX
COWL_Z, COWL_Y = 0.740 * PX, 0.960 * PX
HEADER_Z, HEADER_Y = 0.400 * PX, 1.340 * PX
ROOF_B_Z, ROOF_B_Y = -0.950 * PX, 1.420 * PX
HATCH_Z, HATCH_Y = -1.520 * PX, 1.020 * PX
TAIL_TOP_Z, TAIL_TOP_Y = -2.020 * PX, 0.660 * PX

# (z metres, value). z runs from the nose down to the tail.
SIL_M = (
	(2.15, 0.42), (1.98, 0.70), (1.40, 0.86), (0.74, 0.96),
	(0.40, 1.34), (-0.15, 1.40), (-0.95, 1.42), (-1.22, 1.08),
	(-1.52, 1.02), (-2.02, 0.66), (-2.15, 0.40),
)
PLAN_M = (
	(2.15, 0.80), (1.82, 0.86), (1.35, 0.90),
	(-1.35, 0.90), (-1.78, 0.86), (-2.15, 0.80),
)
SKIN_T = 0.10 * PX
UPPER_X = 0.780 * PX        # roof rail, narrower than the doors
STEP_M = 0.050

REGIONS = {
	"paint_side": (0, 0, 64, 128),
	"paint_top": (64, 0, 64, 64),
	"paint_low": (64, 64, 64, 32),
	"black": (128, 0, 32, 32),
	"rubber": (160, 0, 48, 48),
	"alloy": (208, 0, 48, 48),
	"glass": (0, 128, 48, 64),
	"glass_dark": (48, 128, 48, 64),
	"lamp_front": (96, 128, 80, 36),
	"lamp_tail": (176, 128, 80, 36),
	"amber": (128, 32, 32, 16),
	"plate": (0, 196, 72, 36),
	"chrome": (80, 196, 28, 28),
	"interior": (112, 176, 48, 48),
	"seat": (160, 176, 48, 48),
	"dash": (208, 176, 48, 36),
	"grille": (128, 52, 64, 28),
	"reverse": (192, 52, 28, 20),
}


def px_m(metres):
	return metres * PX


def uid(name):
	return str(uuid.uuid5(uuid.NAMESPACE_URL, "atr/road_car/" + name))


class Model:
	def __init__(self):
		self.elements = []
		self.groups = {}
		self._n = 0

	def group(self, name, origin):
		self.groups[name] = {"uuid": uid("group/" + name), "name": name, "origin": list(origin), "children": []}

	def add(self, group, name, frm, to, material, rotation=(0, 0, 0), origin=None, skip=(), flip_u=False, face_material=None):
		self._n += 1
		name = "%s_%d" % (name, self._n)
		frm = [round(float(v), 4) for v in frm]
		to = [round(float(v), 4) for v in to]
		# Keep from <= to on each axis. Swapping mirrors a rotated box, so callers pass min/max.
		for i in range(3):
			if frm[i] > to[i]:
				frm[i], to[i] = to[i], frm[i]
		if origin is None:
			origin = [(frm[i] + to[i]) / 2 for i in range(3)]
		origin = [round(float(v), 4) for v in origin]
		faces = {}
		for face in ("north", "south", "east", "west", "up", "down"):
			if face in skip:
				continue
			region = face_material.get(face, material) if face_material else material
			if region is None:
				continue
			x, y, w, h = REGIONS[region]
			uv = [x + w, y, x, y + h] if flip_u else [x, y, x + w, y + h]
			faces[face] = {"uv": uv, "texture": 0}
		element = {
			"name": name, "type": "cube", "uuid": uid(name),
			"from": frm, "to": to, "origin": origin, "rotation": [round(v, 4) for v in rotation],
			"faces": faces,
		}
		self.elements.append(element)
		self.groups[group]["children"].append(element["uuid"])
		return element


def box(model, group, name, x0, y0, z0, x1, y1, z1, material, **kw):
	if abs(x1 - x0) < 0.02 or abs(y1 - y0) < 0.02 or abs(z1 - z0) < 0.02:
		return
	model.add(group, name, (x0, y0, z0), (x1, y1, z1), material, **kw)


def slope_x(model, group, name, y0, z0, y1, z1, x0, x1, material, thick=0.30, **kw):
	"""A panel from (y0, z0) to (y1, z1), spanning x, tilted about X. Thickness is along the panel normal."""
	dy, dz = y1 - y0, z1 - z0
	length = math.hypot(dy, dz)
	if length < 0.05 or abs(x1 - x0) < 0.02:
		return
	theta = math.degrees(math.atan2(-dy, dz))
	model.add(group, name, (x0, y0 - thick / 2, z0), (x1, y0 + thick / 2, z0 + length), material,
			rotation=(theta, 0, 0), origin=((x0 + x1) / 2, y0, z0), **kw)


def slope_z(model, group, name, x0, y0, x1, y1, z0, z1, material, thick=0.28, **kw):
	"""A panel from (x0, y0) to (x1, y1), spanning z, tilted about Z."""
	dx, dy = x1 - x0, y1 - y0
	length = math.hypot(dx, dy)
	if length < 0.05 or abs(z1 - z0) < 0.02:
		return
	theta = math.degrees(math.atan2(-dx, dy))
	model.add(group, name, (x0 - thick / 2, y0, z0), (x0 + thick / 2, y0 + length, z1), material,
			rotation=(0, 0, theta), origin=(x0, y0, (z0 + z1) / 2), **kw)


def side_skin(model, group, name, side, x_outer, thick, y0, y1, z0, z1, material, **kw):
	if abs(y1 - y0) < 0.08 or abs(z1 - z0) < 0.08:
		return
	x_in = x_outer - side * thick
	box(model, group, name, x_in, y0, z0, x_outer, y1, z1, material,
		skip=("west",) if side > 0 else ("east",), **kw)


def ring(model, group, name, cx, cy, cz, r_out, r_in, x0, x1, n, material, ang0, ang1, **kw):
	"""Cubes whose outer corners sit on a circle about the X axis. ang 0 is up, positive swings toward +z."""
	if n <= 0 or abs(ang1 - ang0) < 0.1 or r_out - r_in < 0.05:
		return
	step = (ang1 - ang0) / n
	half = abs(step) / 2
	hr = math.radians(half)
	zh = r_out * math.sin(hr)
	for i in range(n):
		ang = ang0 + step * (i + 0.5)
		y_out = cy + r_out * math.cos(hr)
		y_in = cy + r_in * math.cos(hr)
		model.add(group, "%s_%d" % (name, i), (x0, y_in, cz - zh), (x1, y_out, cz + zh), material,
				rotation=(ang, 0, 0), origin=(cx, cy, cz), **kw)


def ring_z(model, group, name, cx, cy, cz, r_out, r_in, z0, z1, n, material):
	"""A ring in the XY plane (a steering wheel), spun about Z."""
	step = 360.0 / n
	half = abs(step) / 2
	hr = math.radians(half)
	xh = r_out * math.sin(hr)
	for i in range(n):
		ang = step * (i + 0.5)
		model.add(group, "%s_%d" % (name, i), (cx - xh, cy + r_in * math.cos(hr), z0), (cx + xh, cy + r_out * math.cos(hr), z1),
				material, rotation=(0, 0, ang), origin=(cx, cy, cz))


def wheel(model, suffix, cx, cz):
	"""One wheel. The group origin is the spindle, so the renderer can roll and steer it."""
	group = "wheel_" + suffix
	model.group(group, (cx, WHEEL_Y, cz))
	half = TYRE_W / 2
	x0, x1 = cx - half, cx + half
	ring(model, group, "tyre", cx, WHEEL_Y, cz, WHEEL_R, RIM_R + 0.15, x0, x1, 28, "rubber", 0, 360)
	# The alloy sits just inboard of the outer sidewall, so the tyre lip frames it.
	outer = cx + math.copysign(half - 0.28, cx if cx != 0 else 1)
	inner = cx + math.copysign(0.15, cx if cx != 0 else 1)
	if outer < inner:
		outer, inner = inner, outer
	xa, xb = min(inner, outer), max(inner, outer)
	ring(model, group, "rim", cx, WHEEL_Y, cz, RIM_R + 0.28, RIM_R - 0.15, xa, xb, 24, "alloy", 0, 360)
	for i in range(5):
		model.add(group, "spoke_%d" % i, (xa, WHEEL_Y + 0.55, cz - 0.22), (xb, WHEEL_Y + RIM_R - 0.35, cz + 0.22), "alloy",
				rotation=(i * 72.0, 0, 0), origin=(cx, WHEEL_Y, cz))
	# Hub, a short stack of small cubes rather than one square.
	ring(model, group, "hub", cx, WHEEL_Y, cz, 0.85, 0.15, xa, xb, 10, "alloy", 0, 360)
	# Brake disc, inboard of the spokes, dark.
	disc_o = cx - math.copysign(half * 0.15, cx if cx != 0 else 1)
	disc_i = cx - math.copysign(half * 0.55, cx if cx != 0 else 1)
	da, db = min(disc_o, disc_i), max(disc_o, disc_i)
	ring(model, group, "disc", cx, WHEEL_Y, cz, RIM_R - 0.05, 0.7, da, db, 16, "black", 0, 360)


def arch(model, side, axle, z_name):
	"""Body-coloured lip around the upper three-quarters of the wheel, plus the dark liner inside it."""
	cx = side * WHEEL_X
	r_out = OPEN_R + LIP_T
	x_out = side * ARCH_X
	x_in = side * (DOOR_X - 0.15)
	xa, xb = min(x_in, x_out), max(x_in, x_out)
	ring(model, "body", "lip_%s" % z_name, cx, WHEEL_Y, axle, r_out, OPEN_R, xa, xb, 18, "paint_side", -92, 92)
	# Liner, just inside the opening, so the hole reads as a wheel arch and not a cut in a flat wall.
	lx0, lx1 = min(cx - 0.4, cx + 0.4), max(cx - 0.4, cx + 0.4)
	ring(model, "trim", "liner_%s" % z_name, cx, WHEEL_Y, axle, OPEN_R - 0.05, OPEN_R - 0.55, lx0, lx1, 14, "black", -100, 100)


def fender_fill(model, side, axle, z0, z1, y_top, name):
	"""Side skin above the arch opening. The lip covers the steps."""
	step = 1.15
	z = z0
	i = 0
	while z < z1 - 0.01:
		z2 = min(z1, z + step)
		zm = (z + z2) / 2 - axle
		if abs(zm) >= OPEN_R - 0.2:
			y0 = SILL
		else:
			y0 = WHEEL_Y + math.sqrt(max(0.0, OPEN_R * OPEN_R - zm * zm)) - 0.15
		if y_top - y0 > 0.2:
			side_skin(model, "body", "%s_%d" % (name, i), side, side * ARCH_X, SKIN + 0.15, y0, y_top, z, z2, "paint_side")
		z = z2
		i += 1


def mirrors(model, side):
	"""Door mirror: painted head, black stalk, flat glass. The head is in body so it takes the paint."""
	x_door = side * DOOR_X
	z, y = 0.62 * PX, BELT_F + 0.35
	reach = side * 1.55
	box(model, "trim", "stalk", x_door - side * 0.1, y, z - 0.28, x_door + reach * 0.45, y + 0.55, z + 0.28, "black")
	hx0, hx1 = x_door + reach * 0.35, x_door + reach
	box(model, "body", "mirror_head", min(hx0, hx1), y - 0.15, z - 1.15, max(hx0, hx1), y + 1.55, z + 0.85, "paint_side")
	# The glass faces back and slightly in, on the inner face of the head.
	gx = hx0 if side > 0 else hx1
	box(model, "mirror", "mirror_glass", gx - side * 0.12, y + 0.15, z - 0.85, gx, y + 1.25, z + 0.45, "glass")


def lamps_front(model):
	"""Thin lamps set into the nose, with an amber outer end and a side repeater."""
	for side in (1, -1):
		x0 = side * 0.28 * PX
		x1 = side * 0.72 * PX
		y0, y1 = 0.470 * PX, 0.630 * PX
		z0, z1 = HALF_L - 0.20, HALF_L + 0.55
		box(model, "lamps", "head", min(x0, x1), y0, z0, max(x0, x1), y1, z1, "lamp_front",
			flip_u=(side < 0),
			face_material={"north": "black", "up": "black", "down": "black", "south": "lamp_front"})
		box(model, "lamps", "repeater", side * DOOR_X - side * 0.02, 0.72 * PX, AXLE_F + 0.15 * PX, side * DOOR_X + side * 0.22, 0.78 * PX, AXLE_F + 0.55 * PX, "amber")


def lamps_rear(model):
	for side in (1, -1):
		x0 = side * 0.22 * PX
		x1 = side * 0.78 * PX
		y0, y1 = 0.500 * PX, 0.660 * PX
		z0, z1 = -HALF_L - 0.55, -HALF_L + 0.20
		box(model, "lamps", "tail", min(x0, x1), y0, z0, max(x0, x1), y1, z1, "lamp_tail",
			flip_u=(side < 0),
			face_material={"south": "black", "up": "black", "down": "black", "north": "lamp_tail", "east": "lamp_tail", "west": "lamp_tail"})
		box(model, "lamps", "tail_side", min(side * 0.74 * PX, side * 0.82 * PX), y0 + 0.25, -HALF_L - 0.20, max(side * 0.74 * PX, side * 0.82 * PX), y1 - 0.20, -HALF_L + 1.4, "lamp_tail")
		# Pale reverse lamp inboard, and a small reflector below.
		rx0, rx1 = side * 0.08 * PX, side * 0.20 * PX
		box(model, "lamps", "reverse", min(rx0, rx1), y0 + 0.35, -HALF_L - 0.85, max(rx0, rx1), y1 - 0.35, -HALF_L - 0.15, "reverse")
	box(model, "lamps", "stop_high", -0.80, ROOF_B_Y + 0.05, ROOF_B_Z - 0.20, 0.80, ROOF_B_Y + 0.38, ROOF_B_Z + 1.10, "lamp_tail")


def plates(model):
	"""Recessed plain plates. The pocket is a dark cube set behind the bumper skin; the plate sits in it."""
	# Front, low on the bumper.
	box(model, "trim", "plate_pocket_f", -1.55 * PX / 2, 0.210 * PX, HALF_L - 0.40, 1.55 * PX / 2, 0.390 * PX, HALF_L + 0.02, "black")
	box(model, "plates", "plate_f", -1.35 * PX / 2, 0.225 * PX, HALF_L - 0.05, 1.35 * PX / 2, 0.375 * PX, HALF_L + 0.16, "plate",
		face_material={"south": "plate", "north": "black", "up": "black", "down": "black", "east": "black", "west": "black"})
	# Rear, on the hatch.
	box(model, "trim", "tail_garnish", -0.220 * PX, 0.470 * PX, -HALF_L - 0.70, 0.220 * PX, 0.690 * PX, -HALF_L + 0.20, "black")
	box(model, "trim", "plate_pocket_r", -1.55 * PX / 2, 0.230 * PX, -HALF_L - 0.20, 1.55 * PX / 2, 0.420 * PX, -HALF_L + 0.40, "black")
	box(model, "plates", "plate_r", -1.35 * PX / 2, 0.250 * PX, -HALF_L - 0.70, 1.35 * PX / 2, 0.400 * PX, -HALF_L - 0.05, "plate",
		face_material={"north": "plate", "south": "black", "up": "black", "down": "black", "east": "black", "west": "black"})


def seams(model):
	"""Door gaps: a dark strip set just inside the skin, with the doors themselves slightly inboard of the wings."""
	gap = 0.11
	pairs = (
		("a", AXLE_F - OPEN_R + 0.4, SILL, BELT_F),
		("b", 0.10 * PX, SILL, (BELT_F + BELT_R) / 2),
		("c", AXLE_R + OPEN_R - 0.2, SILL, BELT_R),
	)
	for side in (1, -1):
		x_out = side * DOOR_X
		for name, z, y0, y1 in pairs:
			box(model, "trim", "seam_%s" % name, x_out - side * 0.04, y0, z - gap / 2, x_out + side * 0.20, y1, z + gap / 2, "black")
		# The crease along the doors, a shallow groove proud of the paint.
		box(model, "trim", "crease", x_out - side * 0.04, CREASE - 0.09, AXLE_R + OPEN_R, x_out + side * 0.16, CREASE + 0.09, AXLE_F - OPEN_R, "black")
		for z in (0.55 * PX, -0.40 * PX):
			box(model, "body", "handle", x_out - side * 0.02, CREASE + 0.70, z, x_out + side * 0.28, CREASE + 1.15, z + 2.4, "paint_side")


def lerp_desc(points, z):
	"""points are (z, value) with z descending."""
	if z >= points[0][0]:
		return points[0][1]
	if z <= points[-1][0]:
		return points[-1][1]
	for (z0, v0), (z1, v1) in zip(points, points[1:]):
		if z1 <= z <= z0 and z0 != z1:
			return v0 + (v1 - v0) * ((z0 - z) / (z0 - z1))
	return points[-1][1]


def belt_m(z_m):
	t = max(0.0, min(1.0, (0.70 - z_m) / 2.00))
	return 0.800 + 0.070 * t


def glass_opening(z_m):
	"""Side-window hole as (bottom, top) in metres, or None on a pillar or a wing."""
	belt = belt_m(z_m)
	if 0.100 <= z_m <= 0.580:
		top = 1.000 + 0.260 * max(0.0, min(1.0, (0.580 - z_m) / 0.400))
		return (belt, top) if top - belt > 0.100 else None
	if -1.020 <= z_m <= -0.060:
		if z_m < -0.700:
			top = 1.260 + (1.000 - 1.260) * max(0.0, min(1.0, (-0.700 - z_m) / 0.320))
		else:
			top = 1.260
		return (belt, top) if top - belt > 0.100 else None
	return None


def wall(model, group, name, side, x_outer, thick, y0, y1, z0, z1, material):
	if y1 - y0 < 0.10 or abs(z1 - z0) < 0.10:
		return
	x_in = x_outer - side * thick
	box(model, group, name, x_in, y0, z0, x_outer, y1, z1, material)


def side_body(model):
	"""Solid side skin along the silhouette, with window holes and wheel-arch cutouts."""
	z = 2.150
	i = 0
	while z > -2.150 + 1e-6:
		z2 = max(-2.150, z - STEP_M)
		zm = (z + z2) / 2
		y_top = max(lerp_desc(SIL_M, z), lerp_desc(SIL_M, z2)) + 0.012
		y_floor = 0.180
		for axle_m in (AXLE_F / PX, AXLE_R / PX):
			dz = abs(zm - axle_m)
			opening = OPEN_R / PX
			if dz < opening:
				y_floor = max(y_floor, WHEEL_Y / PX + math.sqrt(opening * opening - dz * dz) + 0.010)
		hx = lerp_desc(PLAN_M, zm) * PX
		door = (AXLE_R + OPEN_R) / PX < zm < (AXLE_F - OPEN_R) / PX
		cabin = -1.150 < zm < 0.720
		hx_lo = DOOR_X if door else hx
		hx_hi = UPPER_X if cabin else hx
		opening_y = glass_opening(zm)
		spans = []
		if opening_y is not None and y_floor < opening_y[0] - 0.02:
			spans.append((y_floor, opening_y[0] + 0.015, hx_lo, True))
			if y_top > opening_y[1]:
				spans.append((opening_y[1] - 0.015, y_top, hx_hi, False))
		elif cabin and y_top > 0.980:
			spans.append((y_floor, 0.930, hx_lo, True))
			spans.append((0.900, y_top, hx_hi, False))
		else:
			spans.append((y_floor, y_top, hx_lo, True))
		for y0, y1, x_outer, may_split in spans:
			cuts = [y0]
			if may_split and y0 < 0.340 < y1 and door:
				cuts.append(0.340)
			if may_split and y0 < 0.620 < y1:
				cuts.append(0.620)
			if (zm > 1.550 or zm < -1.600) and y0 < 0.400 < y1:
				cuts.append(0.400)
			cuts.append(y1)
			cuts = sorted(set(cuts))
			for a, b in zip(cuts, cuts[1:]):
				if b - a < 0.015:
					continue
				if (zm > 1.550 or zm < -1.600) and b <= 0.400 + 1e-6:
					material = "black"
					group = "trim"
				elif a < 0.300 and door:
					material = "black"
					group = "trim"
				else:
					material = "paint_side"
					group = "body"
				wall(model, group, "skin_%d" % i, 1, x_outer, SKIN_T, a * PX, b * PX, z2 * PX - 0.18, z * PX + 0.18, material)
				wall(model, group, "skin_%d" % i, -1, -x_outer, SKIN_T, a * PX, b * PX, z2 * PX - 0.18, z * PX + 0.18, material)
				i += 1
		z = z2


def greenhouse(model):
	"""Windscreen, hatch glass and the side panes that fill the holes in the skin."""
	half = 0.700 * PX
	slope_x(model, "glass", "windscreen", COWL_Y, COWL_Z, HEADER_Y, HEADER_Z, -half, half, "glass", thick=0.22)
	slope_x(model, "glass", "rear_screen", ROOF_B_Y, ROOF_B_Z, HATCH_Y, HATCH_Z, -0.640 * PX, 0.640 * PX, "glass_dark", thick=0.22)
	z = 0.580
	i = 0
	while z > -1.020:
		z2 = max(-1.020, z - STEP_M)
		zm = (z + z2) / 2
		opening = glass_opening(zm)
		if opening is not None:
			y0, y1 = opening
			for side in (1, -1):
				x_outer = side * (DOOR_X - 0.040 * PX)
				wall(model, "glass", "win_%d" % i, side, x_outer, 0.28, y0 * PX + 0.12, y1 * PX - 0.12, z2 * PX, z * PX, "glass")
				i += 1
		z = z2
	# Frames, proud of the paint by a hair so the glass reads as a window.
	for side in (1, -1):
		x = side * (DOOR_X + 0.04)
		wall(model, "trim", "pillar_b", side, x, 0.55, BELT_F - 0.10, 1.260 * PX, -0.060 * PX, 0.100 * PX, "black")
		wall(model, "trim", "pillar_a", side, x, 0.50, BELT_F, 1.050 * PX, 0.560 * PX, 0.680 * PX, "black")
		wall(model, "trim", "pillar_c", side, x, 0.50, BELT_R - 0.10, 1.080 * PX, -1.080 * PX, -0.960 * PX, "black")
	box(model, "trim", "cowl", -0.760 * PX, COWL_Y - 0.10, COWL_Z - 0.15, 0.760 * PX, COWL_Y + 0.28, COWL_Z + 0.55, "black")
	slope_x(model, "trim", "wiper_d", COWL_Y + 0.40, COWL_Z - 0.20, HEADER_Y - 1.6, HEADER_Z + 2.2, 0.10 * PX, 0.22 * PX, "black", thick=0.12)
	slope_x(model, "trim", "wiper_p", COWL_Y + 0.40, COWL_Z - 0.20, HEADER_Y - 2.2, HEADER_Z + 3.0, -0.48 * PX, -0.34 * PX, "black", thick=0.12)
	slope_x(model, "trim", "wiper_rear", HATCH_Y + 0.6, HATCH_Z + 0.4, HATCH_Y + 2.4, HATCH_Z + 2.2, -0.10, 0.10, "black", thick=0.12)


def cabin(model):
	"""What shows through the glass: dash, wheel, two front seats and a rear bench."""
	box(model, "cabin", "floor", -0.78 * PX, 0.28 * PX, AXLE_R + 0.2 * PX, 0.78 * PX, 0.40 * PX, COWL_Z - 0.4, "interior")
	box(model, "cabin", "dash", -0.75 * PX, 0.55 * PX, 0.55 * PX, 0.75 * PX, COWL_Y - 0.15, COWL_Z - 0.15, "dash")
	# Steering wheel in front of the left seat. A ring of cubes, plus a hub.
	sx, sy, sz = 0.36 * PX, 0.82 * PX, 0.50 * PX
	ring_z(model, "cabin", "steer", sx, sy, sz, 0.95, 0.70, sz - 0.10, sz + 0.10, 14, "black")
	box(model, "cabin", "steer_hub", sx - 0.22, sy - 0.22, sz - 0.16, sx + 0.22, sy + 0.22, sz + 0.16, "black")
	for side, x in ((1, 0.36 * PX), (-1, -0.36 * PX)):
		box(model, "cabin", "cushion", x - 0.42 * PX, 0.40 * PX, -0.05 * PX, x + 0.42 * PX, 0.62 * PX, 0.55 * PX, "seat")
		box(model, "cabin", "back", x - 0.42 * PX, 0.55 * PX, -0.15 * PX, x + 0.42 * PX, 1.05 * PX, 0.05 * PX, "seat")
		box(model, "cabin", "head", x - 0.28 * PX, 1.00 * PX, -0.12 * PX, x + 0.28 * PX, 1.22 * PX, 0.08 * PX, "seat")
	box(model, "cabin", "rear_bench", -0.72 * PX, 0.42 * PX, -0.85 * PX, 0.72 * PX, 0.66 * PX, -0.35 * PX, "seat")
	box(model, "cabin", "rear_back", -0.72 * PX, 0.58 * PX, -1.00 * PX, 0.72 * PX, 1.10 * PX, -0.78 * PX, "seat")


def shell(model):
	"""Painted shell. The side skin carries the silhouette; the sheets close the top and the ends."""
	for side in (1, -1):
		arch(model, side, AXLE_F, "f" if side > 0 else "fr")
		arch(model, side, AXLE_R, "r" if side > 0 else "rr")
		mirrors(model, side)
		# Wheel well, so the arch is not a window into the cabin.
		cx = side * WHEEL_X
		well = cx - side * 0.55 * PX
		box(model, "trim", "well_f", min(cx, well), 0.12 * PX, AXLE_F - OPEN_R, max(cx, well), WHEEL_Y + OPEN_R, AXLE_F + OPEN_R, "black")
		box(model, "trim", "well_r", min(cx, well), 0.12 * PX, AXLE_R - OPEN_R, max(cx, well), WHEEL_Y + OPEN_R, AXLE_R + OPEN_R, "black")
	side_body(model)

	half_bonnet = 0.830 * PX
	slope_x(model, "body", "bonnet", LIP_Y, LIP_Z, COWL_Y, COWL_Z, -half_bonnet, half_bonnet, "paint_top", thick=0.70)
	slope_x(model, "body", "roof", HEADER_Y, HEADER_Z, ROOF_B_Y, ROOF_B_Z, -0.740 * PX, 0.740 * PX, "paint_top", thick=0.70)
	slope_x(model, "body", "tailgate", HATCH_Y, HATCH_Z, TAIL_TOP_Y, TAIL_TOP_Z, -0.760 * PX, 0.760 * PX, "paint_side", thick=0.70)
	box(model, "trim", "antenna", -0.14, ROOF_B_Y - 0.10, ROOF_B_Z + 2.2, 0.14, ROOF_B_Y + 0.70, ROOF_B_Z + 5.4, "black")

	# Nose and tail faces. Lamps and the plate sit just proud of these.
	box(model, "body", "nose_face", -0.760 * PX, 0.400 * PX, HALF_L - 0.22 * PX, 0.760 * PX, LIP_Y + 0.04, HALF_L - 0.02, "paint_side")
	box(model, "body", "tail_face", -0.760 * PX, 0.400 * PX, -HALF_L + 0.02, 0.760 * PX, TAIL_TOP_Y + 0.06, -HALF_L + 0.20 * PX, "paint_low")
	box(model, "trim", "front_bumper", -0.780 * PX, 0.160 * PX, HALF_L - 0.16 * PX, 0.780 * PX, 0.400 * PX, HALF_L + 0.04, "black")
	box(model, "trim", "rear_bumper", -0.760 * PX, 0.160 * PX, -HALF_L - 0.04, 0.760 * PX, 0.400 * PX, -HALF_L + 0.18 * PX, "black")
	# Grille, three slats in a dark pocket, no badge. A black bay behind it hides the cabin.
	box(model, "trim", "bay", -0.640 * PX, 0.300 * PX, 1.150 * PX, 0.640 * PX, 0.620 * PX, HALF_L - 0.28 * PX, "black")
	box(model, "trim", "grille_pocket", -0.280 * PX, 0.430 * PX, HALF_L - 0.36 * PX, 0.280 * PX, 0.600 * PX, HALF_L - 0.02, "black")
	for i, y in enumerate((0.445 * PX, 0.500 * PX, 0.555 * PX)):
		box(model, "trim", "slat_%d" % i, -0.250 * PX, y, HALF_L - 0.18 * PX, 0.250 * PX, y + 0.045 * PX, HALF_L - 0.02, "chrome")
	box(model, "trim", "floor_mid", -0.70 * PX, 0.12 * PX, AXLE_R + OPEN_R, 0.70 * PX, ROCKER, AXLE_F - OPEN_R, "black")
	box(model, "trim", "floor_front", -0.62 * PX, 0.14 * PX, AXLE_F + 0.15 * PX, 0.62 * PX, 0.30 * PX, HALF_L - 0.5 * PX, "black")
	box(model, "trim", "floor_rear", -0.62 * PX, 0.14 * PX, -HALF_L + 0.5 * PX, 0.62 * PX, 0.30 * PX, AXLE_R - 0.15 * PX, "black")
	box(model, "trim", "exhaust", -0.55 * PX, 0.20 * PX, -HALF_L - 0.10, -0.28 * PX, 0.36 * PX, -HALF_L + 0.40, "chrome")


def build():
	model = Model()
	for name in ("body", "trim", "lamps", "plates", "glass", "mirror", "cabin"):
		model.group(name, (0, 0, 0))
	shell(model)
	seams(model)
	greenhouse(model)
	lamps_front(model)
	lamps_rear(model)
	plates(model)
	cabin(model)
	wheel(model, "fl", WHEEL_X, AXLE_F)
	wheel(model, "fr", -WHEEL_X, AXLE_F)
	wheel(model, "rl", WHEEL_X, AXLE_R)
	wheel(model, "rr", -WHEEL_X, AXLE_R)
	return model


def paint_texture():
	image = Image.new("RGB", (TEX, TEX), (20, 20, 20))
	draw = ImageDraw.Draw(image)

	def fill_gradient(region, top, bottom):
		x, y, w, h = REGIONS[region]
		for row in range(h):
			t = row / max(1, h - 1)
			colour = tuple(int(top[c] + (bottom[c] - top[c]) * t) for c in range(3))
			draw.line([(x, y + row), (x + w - 1, y + row)], fill=colour)

	# Body maps are light, because the game multiplies them by the paint colour.
	fill_gradient("paint_side", (250, 250, 252), (168, 170, 176))
	fill_gradient("paint_top", (255, 255, 255), (230, 232, 236))
	fill_gradient("paint_low", (190, 192, 198), (140, 142, 148))
	x, y, w, h = REGIONS["paint_side"]
	# A soft horizontal shade so a big panel is not one flat tone.
	for col in range(w):
		shade = int(8 * math.sin(col / w * math.pi))
		for row in range(h):
			r, g, b = image.getpixel((x + col, y + row))
			image.putpixel((x + col, y + row), (min(255, r + shade), min(255, g + shade), min(255, b + shade)))

	draw.rectangle(REGIONS["black"][:2] + (REGIONS["black"][0] + REGIONS["black"][2] - 1, REGIONS["black"][1] + REGIONS["black"][3] - 1), fill=(18, 18, 20))
	# Tyre: dark tread with two grooves, and a slightly lighter sidewall tone at the edges.
	x, y, w, h = REGIONS["rubber"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(28, 28, 30))
	draw.rectangle([x, y + h // 3, x + w - 1, y + h // 3 + 2], fill=(18, 18, 18))
	draw.rectangle([x, y + 2 * h // 3, x + w - 1, y + 2 * h // 3 + 2], fill=(18, 18, 18))
	draw.rectangle([x, y, x + 3, y + h - 1], fill=(48, 48, 52))
	draw.rectangle([x + w - 4, y, x + w - 1, y + h - 1], fill=(48, 48, 52))

	x, y, w, h = REGIONS["alloy"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(176, 180, 186))
	cx, cy = x + w / 2, y + h / 2
	for ring_r, colour in ((w * 0.46, (150, 154, 160)), (w * 0.28, (196, 198, 204)), (w * 0.10, (120, 124, 130))):
		draw.ellipse([cx - ring_r, cy - ring_r, cx + ring_r, cy + ring_r], outline=colour)

	x, y, w, h = REGIONS["glass"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(186, 206, 214))
	draw.rectangle([x, y, x + w - 1, y + 6], fill=(220, 232, 236))
	x, y, w, h = REGIONS["glass_dark"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(120, 146, 158))

	x, y, w, h = REGIONS["lamp_front"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(40, 40, 44))
	draw.rounded_rectangle([x + 2, y + 3, x + w - 10, y + h - 4], radius=6, fill=(236, 240, 246))
	draw.rounded_rectangle([x + w - 16, y + 4, x + w - 3, y + h - 5], radius=3, fill=(232, 168, 46))
	draw.rectangle([x + 8, y + 8, x + w // 2, y + h - 9], fill=(255, 255, 255))

	x, y, w, h = REGIONS["lamp_tail"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(28, 8, 10))
	draw.rounded_rectangle([x + 2, y + 3, x + w - 4, y + h - 4], radius=5, fill=(168, 22, 30))
	draw.rectangle([x + 6, y + 7, x + w - 14, y + h - 8], fill=(210, 36, 44))
	draw.rectangle([x + w - 14, y + 8, x + w - 6, y + h - 9], fill=(230, 230, 226))

	draw.rectangle(REGIONS["amber"][:2] + (REGIONS["amber"][0] + REGIONS["amber"][2] - 1, REGIONS["amber"][1] + REGIONS["amber"][3] - 1), fill=(214, 140, 32))
	x, y, w, h = REGIONS["plate"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(214, 214, 208))
	draw.rectangle([x + 2, y + 2, x + w - 3, y + h - 3], outline=(120, 124, 128))
	x, y, w, h = REGIONS["chrome"]
	for row in range(h):
		c = 150 + int(70 * row / h)
		draw.line([(x, y + row), (x + w - 1, y + row)], fill=(c, c, c + 4))
	for name, colour in (("interior", (32, 34, 38)), ("seat", (42, 44, 50)), ("dash", (24, 26, 30)), ("grille", (16, 16, 18)), ("reverse", (230, 230, 226))):
		r = REGIONS[name]
		draw.rectangle([r[0], r[1], r[0] + r[2] - 1, r[1] + r[3] - 1], fill=colour)
	return image


def item_icon():
	"""16x16 side view, the same blue the panel starts on, for the hotbar."""
	image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
	draw = ImageDraw.Draw(image)
	body = (47, 93, 168, 255)
	glass = (176, 206, 214, 255)
	black = (20, 20, 22, 255)
	# Side silhouette, nose to the right.
	draw.polygon([(2, 10), (3, 8), (6, 7), (8, 5), (11, 5), (13, 7), (14, 8), (14, 11), (2, 11)], fill=body)
	draw.polygon([(7, 6), (8, 5), (11, 5), (10, 7), (7, 7)], fill=glass)
	draw.rectangle([2, 11, 14, 12], fill=black)
	for cx in (4, 12):
		draw.ellipse([cx - 1, 10, cx + 2, 13], fill=black)
		draw.point((cx, 11), fill=(180, 184, 190, 255))
	draw.point((14, 9), fill=(240, 240, 245, 255))
	draw.point((2, 9), fill=(180, 30, 36, 255))
	return image


def write_bbmodel(model, texture_png):
	encoded = base64.b64encode(texture_png).decode()
	outliner = []
	for name, group in model.groups.items():
		outliner.append({
			"name": name, "uuid": group["uuid"], "origin": [round(v, 4) for v in group["origin"]],
			"rotation": [0, 0, 0], "visibility": True, "export": True, "children": group["children"],
		})
	document = {
		"meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
		"name": "hatchback", "model_identifier": "", "visible_box": [1, 1, 0],
		"resolution": {"width": TEX, "height": TEX},
		"elements": model.elements,
		"groups": [],
		"outliner": outliner,
		"textures": [{
			"path": "", "name": "hatchback", "folder": "", "namespace": "", "id": "0",
			"width": TEX, "height": TEX, "uv_width": TEX, "uv_height": TEX,
			"particle": False, "use_as_default": True, "visible": True, "internal": True,
			"saved": False, "uuid": uid("texture"),
			"source": "data:image/png;base64," + encoded,
		}],
	}
	path = os.path.join(ROOT, MODEL_PATH)
	os.makedirs(os.path.dirname(path), exist_ok=True)
	with open(path, "w") as handle:
		json.dump(document, handle, separators=(",", ":"))
	return path


def baked_bounds(model):
	"""Axis-aligned bounds after each cube's own rotation, matching BlockbenchMesh.readCube."""
	mins = [1e9, 1e9, 1e9]
	maxs = [-1e9, -1e9, -1e9]

	def rotate(point, origin, rotation):
		x, y, z = point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]
		rx, ry, rz = [math.radians(a) for a in rotation]
		y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
		x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
		x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
		return (x + origin[0], y + origin[1], z + origin[2])

	for element in model.elements:
		frm, to, origin = element["from"], element["to"], element["origin"]
		for ix in (0, 1):
			for iy in (0, 1):
				for iz in (0, 1):
					point = rotate((frm[0] if ix == 0 else to[0], frm[1] if iy == 0 else to[1], frm[2] if iz == 0 else to[2]), origin, element["rotation"])
					for axis in range(3):
						mins[axis] = min(mins[axis], point[axis])
						maxs[axis] = max(maxs[axis], point[axis])
	return mins, maxs


def main():
	model = build()
	texture = paint_texture()
	buf = io.BytesIO()
	texture.save(buf, "png")
	raw = buf.getvalue()
	tex_path = os.path.join(ROOT, TEXTURE_PATH)
	os.makedirs(os.path.dirname(tex_path), exist_ok=True)
	with open(tex_path, "wb") as handle:
		handle.write(raw)
	icon_path = os.path.join(ROOT, ICON_PATH)
	os.makedirs(os.path.dirname(icon_path), exist_ok=True)
	item_icon().save(icon_path)
	model_path = write_bbmodel(model, raw)
	mins, maxs = baked_bounds(model)
	size = [(maxs[i] - mins[i]) / PX for i in range(3)]
	print("cubes", len(model.elements))
	print("groups", ", ".join("%s:%d" % (name, len(group["children"])) for name, group in model.groups.items()))
	print("bbox m  x %.3f  y %.3f..%.3f  z %.3f..%.3f" % (size[0], mins[1] / PX, maxs[1] / PX, mins[2] / PX, maxs[2] / PX))
	print("size m  %.3f long, %.3f wide, %.3f high" % (size[2], size[0], size[1]))
	print("wrote", model_path)
	print("wrote", tex_path)
	print("wrote", icon_path)


if __name__ == "__main__":
	main()
