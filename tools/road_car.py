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


def cabin(model):
	"""What shows through the glass: dash, wheel, two front seats and a rear bench. The cushions are low, because a
	seated player is 1.2 m from seat to crown and has to fit under a 1.44 m roof (RoadVehicleKind seats)."""
	box(model, "cabin", "floor", -0.78 * PX, 0.14 * PX, AXLE_R + 0.2 * PX, 0.78 * PX, 0.18 * PX, COWL_Z - 0.4, "interior")
	box(model, "cabin", "dash", -0.75 * PX, 0.55 * PX, 0.55 * PX, 0.75 * PX, COWL_Y - 0.15, COWL_Z - 0.15, "dash")
	# Steering wheel in front of the left seat. A ring of cubes, plus a hub.
	sx, sy, sz = 0.36 * PX, 0.74 * PX, 0.50 * PX
	ring_z(model, "cabin", "steer", sx, sy, sz, 0.95, 0.70, sz - 0.10, sz + 0.10, 14, "black")
	box(model, "cabin", "steer_hub", sx - 0.22, sy - 0.22, sz - 0.16, sx + 0.22, sy + 0.22, sz + 0.16, "black")
	for side, x in ((1, 0.36 * PX), (-1, -0.36 * PX)):
		box(model, "cabin", "cushion", x - 0.42 * PX, 0.16 * PX, -0.05 * PX, x + 0.42 * PX, 0.30 * PX, 0.55 * PX, "seat")
		box(model, "cabin", "back", x - 0.42 * PX, 0.28 * PX, -0.15 * PX, x + 0.42 * PX, 0.86 * PX, 0.05 * PX, "seat")
		box(model, "cabin", "head", x - 0.28 * PX, 0.84 * PX - 2, -0.12 * PX, x + 0.28 * PX, 0.98 * PX - 2, 0.08 * PX, "seat")
	box(model, "cabin", "rear_bench", -0.72 * PX, 0.16 * PX, -0.85 * PX, 0.72 * PX, 0.32 * PX, -0.35 * PX, "seat")
	box(model, "cabin", "rear_back", -0.72 * PX, 0.30 * PX, -1.00 * PX, 0.72 * PX, 0.88 * PX, -0.78 * PX, "seat")


# ---- the shell: few large cubes and tilted plates, so the surfaces read as panels and not as slices ----------------

W = WIDTH / 2               # half width at the doors and wings
GH = 0.765 * PX             # half width of the glasshouse, tucked in from the doors (the shoulder)
BELT = 0.880 * PX           # window sill line
ROOF_Y = 1.430 * PX
SILL_Y = 0.260 * PX         # underside of the doors
ARCH_TOP = WHEEL_Y + OPEN_R
NOSE_Y = 0.770 * PX         # top of the nose, where the bonnet starts
BONNET_NOSE = (NOSE_Y - 0.6, HALF_L - 1.6)  # the bonnet's front edge, dipping towards the lamps
# glasshouse corners (y, z): cowl -> header, then the flat roof, then the tailgate down to the rear shelf
COWL = (COWL_Y - 0.4, COWL_Z + 1.4)
HEADER = (ROOF_Y - 0.5, 0.20 * PX)
ROOF_END = (ROOF_Y - 0.5, -1.55 * PX)
SHELF = (BELT + 1.0, -1.99 * PX)
GLASS_END = -1.02 * PX     # rear edge of the rear door glass; the C-pillar starts here
TAIL_Z = -HALF_L + 0.3
BUMPER_TOP = SILL_Y + 3.3    # top of the rear bumper, which stands proud of the tailgate


def line_z(p0, p1, y):
	"""z on the line p0 -> p1 (each y, z) at height y."""
	return p0[1] + (p1[1] - p0[1]) * (y - p0[0]) / (p1[0] - p0[0])


def line_z_inv(p0, p1, z):
	"""y on the line p0 -> p1 (each y, z) at depth z."""
	return p0[0] + (p1[0] - p0[0]) * (z - p0[1]) / (p1[1] - p0[1])


def band(model, group, name, p0, p1, xa, xb, width, material):
	"""A flat band in a side plane with one long edge on the line p0 -> p1 (each y, z); the band lies below that line."""
	dy, dz = p1[0] - p0[0], p1[1] - p0[1]
	length = math.hypot(dy, dz)
	ny, nz = dz / length, -dy / length
	if ny > 0:
		ny, nz = -ny, -nz
	oy, oz = ny * width / 2, nz * width / 2
	slope_x(model, group, name, p0[0] + oy, p0[1] + oz, p1[0] + oy, p1[1] + oz, xa, xb, material, thick=width)


def lower_body(model):
	"""Wings, doors and quarters up to the belt, with round arches cut by building round them."""
	arch_f = (AXLE_F - OPEN_R, AXLE_F + OPEN_R)
	arch_r = (AXLE_R - OPEN_R, AXLE_R + OPEN_R)
	for side in (1, -1):
		def skin(name, y0, y1, z0, z1, material="paint_side", group="body", out=0.0):
			xa, xb = sorted((side * (W - 1.4), side * (W + out)))
			box(model, group, name, xa, y0, z0, xb, y1, z1, material)

		skin("doors", SILL_Y, BELT, arch_r[1], arch_f[0])
		skin("over_arch_r", ARCH_TOP, BELT, arch_r[0], arch_r[1])
		skin("quarter", SILL_Y + 0.5, BELT, TAIL_Z + 2.5, arch_r[0])
		skin("over_arch_f", ARCH_TOP, NOSE_Y + 0.6, arch_f[0], arch_f[1])
		skin("wing_nose", SILL_Y + 0.5, BONNET_NOSE[0] - 1.0, arch_f[1], HALF_L - 3.2)
		skin("wing_cowl", BONNET_NOSE[0] - 1.0, BELT, arch_f[0] - 6.5, arch_f[0] + 1.5)
		# the wing's top edge rises with the bonnet, a hair above the plate's centre so its edge doesn't show
		wa, wb = sorted((side * (W - 1.4), side * W))
		start = BONNET_NOSE[1] - 2.0
		lift = (line_z_inv(BONNET_NOSE, COWL, start) + 0.3, start), (COWL[0] + 0.3, COWL[1])
		band(model, "body", "wing_top", lift[0], lift[1], wa, wb, 3.0, "paint_side")
		skin("rocker", SILL_Y - 0.9, SILL_Y, arch_r[1], arch_f[0], "black", "trim", -0.5)
		skin("skirt", SILL_Y, SILL_Y + 0.9, arch_r[1] + 0.3, arch_f[0] - 0.3, "paint_low", "body", 0.05)
		for name, axle in (("f", AXLE_F), ("r", AXLE_R)):
			cx = side * WHEEL_X
			xa, xb = sorted((side * (W - 1.4), side * (W + 0.18)))
			ring(model, "body", "lip_" + name, cx, WHEEL_Y, axle, OPEN_R + 0.9, OPEN_R - 0.1, xa, xb, 14, "paint_top", -90, 90)
			# the corners between the round opening and the square panels round it
			fa, fb = sorted((side * (W - 1.4), side * (W - 0.03)))
			ring(model, "body", "arch_fill_" + name, cx, WHEEL_Y, axle, OPEN_R + 2.6, OPEN_R, fa, fb, 7, "paint_side", -88, 88)
			# the well behind the wheel, so the arch is not a window into the car
			wa, wb = sorted((side * (W - 1.4), side * (WHEEL_X - 0.62 * PX)))
			box(model, "trim", "well_" + name, wa, 0.14 * PX, axle - OPEN_R - 1.5, wb, ARCH_TOP + 0.6, axle + OPEN_R + 1.5, "black")
		# shoulder: the step in from the doors to the glass
		xa, xb = sorted((side * (GH - 0.6), side * W))
		box(model, "body", "shoulder", xa, BELT - 0.5, SHELF[1] - 2.0, xb, BELT, COWL[1], "paint_top")
		# shut lines and handles
		for name, z, y0 in (("front", COWL[1] - 0.6, SILL_Y), ("mid", -0.37 * PX, SILL_Y), ("rear", arch_r[1] + 0.5, ARCH_TOP - 2.0)):
			skin("seam_" + name, y0, BELT - 0.5, z - 0.07, z + 0.07, "black", "trim", 0.05)
		for name, z in (("front", 0.02 * PX), ("rear", -0.90 * PX)):
			skin("handle_" + name, BELT - 2.6, BELT - 2.1, z, z + 2.8, "black", "trim", 0.22)
		skin("sill_line", SILL_Y + 1.6, SILL_Y + 1.9, arch_r[1] + 0.6, arch_f[0] - 0.6, "paint_top", "body", 0.22)
		skin("shoulder_line", BELT - 1.0, BELT - 0.75, arch_r[0], arch_f[1] - 2.5, "paint_top", "body", 0.18)
		skin("crease", BELT - 3.6, BELT - 3.2, arch_r[1] + 1.0, arch_f[0] - 1.0, "paint_top", "body", 0.25)
		# door mirror on the front corner of the door glass
		xa, xb = sorted((side * (GH - 0.2), side * (W + 0.2)))
		box(model, "trim", "mirror_stalk", xa, BELT - 0.3, COWL[1] - 3.5, xb, BELT + 0.9, COWL[1] - 2.8, "black")
		xa, xb = sorted((side * (W + 0.2), side * (W + 1.2)))
		box(model, "body", "mirror", xa, BELT + 0.4, COWL[1] - 3.8, xb, BELT + 1.6, COWL[1] - 2.6, "paint_side")
	# ends: the nose and tail close the box, rounded in plan by one narrower step
	box(model, "body", "nose", -W + 1.3, SILL_Y + 0.8, HALF_L - 3.2, W - 1.3, BONNET_NOSE[0], HALF_L - 0.9, "paint_side")
	box(model, "trim", "chin", -W + 3.0, SILL_Y + 0.3, HALF_L - 1.4, W - 3.0, SILL_Y + 0.8, HALF_L - 0.45, "black")
	box(model, "body", "nose_tip", -W + 2.6, SILL_Y + 0.8, HALF_L - 0.9, W - 2.6, NOSE_Y - 3.8, HALF_L, "paint_side")
	box(model, "body", "tail", -W + 1.3, SILL_Y + 1.2, TAIL_Z + 0.8, W - 1.3, SHELF[0], TAIL_Z + 2.5, "paint_side")
	box(model, "body", "tail_tip", -W + 2.6, SILL_Y + 1.2, TAIL_Z, W - 2.6, BUMPER_TOP, TAIL_Z + 0.8, "paint_side")
	box(model, "trim", "diffuser", -W + 3.0, SILL_Y + 0.3, TAIL_Z + 0.5, W - 3.0, SILL_Y + 1.2, TAIL_Z + 1.0, "black")
	box(model, "body", "tail_face", -W + 2.6, BUMPER_TOP, TAIL_Z + 0.8, W - 2.6, SHELF[0] - 0.3, TAIL_Z + 1.0, "paint_side")
	box(model, "body", "rear_deck", -W + 1.4, BELT - 0.5, TAIL_Z + 0.8, W - 1.4, SHELF[0], SHELF[1] + 1.0, "paint_top")
	slope_x(model, "body", "bonnet", BONNET_NOSE[0], BONNET_NOSE[1], COWL[0], COWL[1], -W + 1.3, W - 1.3, "paint_top", thick=0.70)
	slope_x(model, "body", "bonnet_cap", NOSE_Y - 1.4, HALF_L - 0.85, BONNET_NOSE[0] + 0.3, BONNET_NOSE[1] - 0.3, -W + 1.4, W - 1.4, "paint_top", thick=0.7)
	# what is behind the skin: engine bay, boot and floor, all dark
	box(model, "trim", "bay", -W + 1.4, 0.14 * PX, COWL[1], W - 1.4, NOSE_Y - 0.2, HALF_L - 3.2, "black")
	box(model, "trim", "bay_cowl", -W + 1.4, NOSE_Y - 0.2, COWL[1], W - 1.4, BELT - 1.2, COWL[1] + 7.0, "black")
	box(model, "trim", "boot", -W + 1.4, 0.14 * PX, TAIL_Z + 2.5, W - 1.4, BELT - 0.6, AXLE_R + OPEN_R, "black")
	box(model, "trim", "floor", -W + 1.4, 0.14 * PX, AXLE_R + OPEN_R, W - 1.4, SILL_Y + 0.4, COWL[1], "black")
	# bumpers are body colour; a dark lip runs under them from arch to arch, with a chamfer at each corner
	for name, z_end, z_arch in (("front", HALF_L + 0.1, AXLE_F + OPEN_R), ("rear", TAIL_Z - 0.1, AXLE_R - OPEN_R)):
		sign = 1 if name == "front" else -1
		za, zb = sorted((z_end - sign * 2.6, z_end))
		box(model, "trim", "lip_" + name, -W + 3.6, SILL_Y - 0.6, za, W - 2.6, SILL_Y + 0.3, zb, "black")
		for side in (1, -1):
			xa, xb = sorted((side * (W - 1.5), side * (W - 0.6)))
			sa, sb = sorted((z_arch, z_end - sign * 2.2))
			box(model, "trim", "lip_side_" + name, xa, SILL_Y - 0.6, sa, xb, SILL_Y + 0.3, sb, "black")
			cx, cz = side * (W - 1.5), z_end - sign * 1.7
			model.add("trim", "lip_corner_" + name, (cx - 1.05, SILL_Y - 0.6, cz - 1.05), (cx + 1.05, SILL_Y + 0.3, cz + 1.05), "black",
					rotation=(0, 45, 0), origin=(cx, SILL_Y, cz))
			model.add("body", "corner_" + name, (cx - 0.85, SILL_Y + 0.3, cz - 0.85), (cx + 0.85, (NOSE_Y if name == "front" else BELT) - 3.7, cz + 0.85), "paint_side",
					rotation=(0, 45, 0), origin=(cx, SILL_Y, cz))


TUMBLE = 15.0              # the side glass leans in by this many degrees, so the roof is narrower than the doors
SIDE_TOP = ROOF_Y - 1.2


def half_at(y, inset=0.0):
	"""Half width of the glasshouse at height y, with the tumblehome."""
	return GH - max(0.0, y - BELT) * math.tan(math.radians(TUMBLE)) - inset


def roll(elements, side):
	"""Lean side elements in about the belt line at x = side * GH. Each cube keeps its own origin, so the move that
	rolling about the belt would add is applied as a shift."""
	a = math.radians(side * TUMBLE)
	c, s = math.cos(a), math.sin(a)
	for e in elements:
		ox, oy, oz = e["origin"]
		vx, vy = side * GH - ox, BELT - oy
		dx = vx - (vx * c - vy * s)
		dy = vy - (vx * s + vy * c)
		for key in ("from", "to", "origin"):
			e[key][0] = round(e[key][0] + dx, 4)
			e[key][1] = round(e[key][1] + dy, 4)
		e["rotation"][2] = round(side * TUMBLE, 4)


def tapered(model, group, name, p0, p1, inset, material, thick, n=6):
	"""A sloped plate (p0 -> p1, each y, z) cut in n strips whose width follows the tumblehome."""
	for i in range(n):
		a = (p0[0] + (p1[0] - p0[0]) * i / n, p0[1] + (p1[1] - p0[1]) * i / n)
		b = (p0[0] + (p1[0] - p0[0]) * (i + 1) / n, p0[1] + (p1[1] - p0[1]) * (i + 1) / n)
		half = half_at((a[0] + b[0]) / 2, inset)
		slope_x(model, group, name, a[0], a[1], b[0], b[1], -half, half, material, thick=thick)


def glasshouse(model):
	"""Windscreen, side glass, roof and tailgate. The sides lean in (TUMBLE); pillars cover the stepped glass edges."""
	gt = half_at(ROOF_Y, 0.45)  # the roof sits between the leaning roof sides
	box(model, "body", "roof", -gt, ROOF_Y - 1.2, ROOF_END[1], gt, ROOF_Y - 0.4, HEADER[1], "paint_top")
	box(model, "body", "roof_top", -gt + 0.5, ROOF_Y - 0.4, ROOF_END[1], gt - 0.5, ROOF_Y, HEADER[1] - 0.3, "paint_top")
	box(model, "body", "spoiler", -gt + 0.3, ROOF_Y - 0.7, ROOF_END[1] - 0.8, gt - 0.3, ROOF_Y, ROOF_END[1] + 1.0, "paint_top")
	box(model, "trim", "cowl", -GH, COWL[0] - 0.7, COWL[1] - 0.8, GH, COWL[0] + 0.1, COWL[1] + 1.2, "black")
	# one screen at its top width; the A-pillar faces fill out to the leaning sides, widest at the foot
	ws = half_at(HEADER[0], 0.9)
	slope_x(model, "glass", "windscreen", COWL[0], COWL[1], HEADER[0], HEADER[1], -ws, ws, "glass", thick=0.30)
	for side in (1, -1):
		fa, fb = sorted((side * (ws - 0.45), side * (ws + 0.2)))
		slope_x(model, "trim", "screen_frit", COWL[0], COWL[1], HEADER[0], HEADER[1], fa, fb, "black", thick=0.36)
	n = 14
	for i in range(n):
		a = (COWL[0] + (HEADER[0] - COWL[0]) * i / n, COWL[1] + (HEADER[1] - COWL[1]) * i / n)
		b = (COWL[0] + (HEADER[0] - COWL[0]) * (i + 1) / n, COWL[1] + (HEADER[1] - COWL[1]) * (i + 1) / n)
		out = half_at(max(a[0], b[0]), 0.1)
		ends = tuple(f for f, keep in (("north", i > 0), ("south", i < n - 1)) if keep)
		for side in (1, -1):
			xa, xb = sorted((side * (ws - 0.15), side * out))
			slope_x(model, "body", "a_face", a[0], a[1], b[0], b[1], xa, xb, "paint_side", thick=0.45, skip=ends)
	tapered(model, "body", "tailgate", SHELF, ROOF_END, 0.2, "paint_side", 0.50)
	gy0, gy1 = SHELF[0] + 1.0, ROOF_END[0] - 1.2
	rf = half_at(gy1 + 0.4, 1.0)
	slope_x(model, "trim", "rear_frame", gy0 - 0.6, line_z(SHELF, ROOF_END, gy0 - 0.6) - 0.12, gy1 + 0.4, line_z(SHELF, ROOF_END, gy1 + 0.4) - 0.12,
			-rf, rf, "black", thick=0.40)
	box(model, "trim", "rear_wiper", -0.4, gy0 - 0.2, line_z(SHELF, ROOF_END, gy0) - 0.42, 0.4, gy0 + 0.5, line_z(SHELF, ROOF_END, gy0) - 0.22, "black")
	wy0, wy1 = COWL[0] + 0.9, COWL[0] + 1.25
	slope_x(model, "trim", "wiper", wy0, line_z(COWL, HEADER, wy0) + 0.25, wy1, line_z(COWL, HEADER, wy1) + 0.25, -GH + 2.5, 1.0, "black", thick=0.10)
	rs = half_at(gy1, 1.6)
	slope_x(model, "glass", "rear_screen", gy0, line_z(SHELF, ROOF_END, gy0) - 0.2, gy1, line_z(SHELF, ROOF_END, gy1) - 0.2,
			-rs, rs, "glass_dark", thick=0.50)
	for side in (1, -1):
		first = len(model.elements)
		xa, xb = sorted((side * (GH - 0.5), side * GH))
		box(model, "glass", "side_glass", xa, BELT, GLASS_END, xb, SIDE_TOP, HEADER[1], "glass")
		box(model, "body", "c_pillar_top", xa, BELT, ROOF_END[1], xb, SIDE_TOP, GLASS_END, "paint_side")
		# the side plane runs on up to the roof top, so the roof edge is a leaning face and not a slab on top
		box(model, "body", "roof_side", xa, SIDE_TOP - 0.05, ROOF_END[1] - 0.8, xb, ROOF_Y - 0.4, HEADER[1], "paint_top")
		pa, pb = sorted((side * (GH - 0.6), side * (GH + 0.08)))
		box(model, "trim", "b_pillar", pa, BELT, -0.44 * PX, pb, SIDE_TOP, -0.30 * PX, "black")
		# the corner in front of the door glass: steps under the screen's line, which the A-pillar covers
		ga, gb = sorted((side * (GH - 0.48), side * (GH - 0.02)))
		steps = 8
		for i in range(steps):
			y0 = BELT + (SIDE_TOP - BELT) * i / steps
			y1 = BELT + (SIDE_TOP - BELT) * (i + 1) / steps
			inner = tuple(f for f, keep in (("down", i > 0), ("up", i < steps - 1)) if keep)
			# a fixed quarter light: tinted and opaque, so the stacked steps show no seams
			box(model, "trim", "front_quarter", ga, y0, HEADER[1] + 0.35, gb, y1, line_z(COWL, HEADER, y1), "glass_dark", skip=inner)
		fa, fb = sorted((side * (GH - 0.6), side * (GH + 0.06)))
		box(model, "trim", "quarter_frame", fa, BELT, HEADER[1] - 0.05, fb, SIDE_TOP, HEADER[1] + 0.35, "black")
		front = ((BELT, line_z(COWL, HEADER, BELT)), (SIDE_TOP, line_z(COWL, HEADER, SIDE_TOP)))
		rear = ((BELT + 0.3, line_z(SHELF, ROOF_END, BELT)), (SIDE_TOP + 0.3, line_z(SHELF, ROOF_END, SIDE_TOP)))  # lifted over the tailgate plate's edge
		lifted = ((front[0][0], front[0][1] + 0.4), (front[1][0], front[1][1] + 0.4))  # over the screen plate's edge
		band(model, "body", "a_pillar", lifted[0], lifted[1], pa, pb, 1.7, "paint_side")
		ia, ib = sorted((side * (GH - 0.5), side * (GH - 0.03)))
		band(model, "body", "c_pillar", rear[0], rear[1], ia, ib, 6.0, "paint_side")
		# window frame along the belt
		box(model, "trim", "belt_strip", pa, BELT - 0.05, GLASS_END, pb, BELT + 0.4, HEADER[1] + 6.0, "black")
		roll(model.elements[first:], side)


def nose_and_tail(model):
	"""Lamps that wrap the corners, grille and lower intake, plates in their recesses. No maker's mark."""
	zf, zr = HALF_L - 0.9, TAIL_Z + 0.8  # zr: the tailgate face above the bumper
	for side in (1, -1):
		xa, xb = sorted((side * 0.38 * PX, side * (W - 2.4)))
		box(model, "trim", "head_lens", xa, NOSE_Y - 3.4, zf - 0.8, xb, NOSE_Y - 1.0, zf + 0.15, "glass_dark")
		ia, ib = sorted((side * 0.41 * PX, side * (W - 3.2)))
		box(model, "lamps", "head", ia, NOSE_Y - 3.0, zf - 0.2, ib, NOSE_Y - 1.4, zf + 0.25, "lamp_front", flip_u=(side < 0),
			face_material={"north": "black", "up": "black", "down": "black", "south": "lamp_front", "east": "black", "west": "black"})
		box(model, "trim", "head_chrome", xa, NOSE_Y - 3.75, zf - 0.2, xb, NOSE_Y - 3.45, zf + 0.12, "chrome")
		wa, wb = sorted((side * (W - 2.7), side * (W - 1.25)))
		box(model, "lamps", "head_wrap", wa, NOSE_Y - 3.0, zf - 1.9, wb, NOSE_Y - 1.6, zf - 0.7, "amber")
		ta, tb = sorted((side * 0.50 * PX, side * (W - 2.4)))
		box(model, "lamps", "tail", ta, BELT - 3.0, zr - 0.05, tb, BELT + 0.2, zr + 0.8, "lamp_tail", flip_u=(side < 0))
		box(model, "body", "tail_border", ta - side * 0.2, BELT - 3.2, zr - 0.02, tb, BELT + 0.35, zr + 0.8, "paint_side")
		aa, ab = sorted((side * 0.50 * PX, side * (0.50 * PX + 1.4)))
		box(model, "lamps", "tail_turn", aa, BELT - 2.6, zr - 0.1, ab, BELT - 0.2, zr + 0.05, "amber")
		ra, rb = sorted((side * (W - 2.7), side * (W - 1.3)))
		box(model, "lamps", "tail_wrap", ra, BELT - 3.0, zr - 0.05, rb, BELT + 0.2, TAIL_Z + 2.5, "lamp_tail")
		qa, qb = sorted((side * (W - 0.3), side * (W + 0.02)))
		box(model, "lamps", "tail_quarter", qa, BELT - 3.0, TAIL_Z + 2.5, qb, BELT + 0.2, TAIL_Z + 4.3, "lamp_tail")
	box(model, "body", "tail_crease", -W + 2.6, BUMPER_TOP - 0.3, TAIL_Z - 0.1, W - 2.6, BUMPER_TOP, TAIL_Z + 0.9, "paint_top")
	band(model, "trim", "screen_foot", (SHELF[0] + 1.3, SHELF[1] - 0.15), (SHELF[0] + 1.3 + 0.01, SHELF[1] - 0.15 - 0.01 * (SHELF[1] - ROOF_END[1]) / (SHELF[0] - ROOF_END[0])), -GH + 1.6, GH - 1.6, 0.8, "black")
	box(model, "body", "tail_shelf", -W + 2.6, BELT + 0.5, zr - 0.05, W - 2.6, BELT + 1.0, zr + 1.0, "paint_top")
	box(model, "body", "bumper_crease", -W + 2.6, SILL_Y + 2.7, HALF_L - 0.2, W - 2.6, SILL_Y + 2.95, HALF_L + 0.12, "paint_top")
	box(model, "body", "grille_frame", -0.36 * PX - 0.5, NOSE_Y - 3.6, zf - 0.3, 0.36 * PX + 0.5, NOSE_Y - 0.9, zf + 0.05, "paint_side")
	box(model, "trim", "grille", -0.36 * PX, NOSE_Y - 3.2, zf - 0.5, 0.36 * PX, NOSE_Y - 1.3, zf + 0.1, "grille")
	box(model, "trim", "grille_strip", -0.36 * PX, NOSE_Y - 1.5, zf, 0.36 * PX, NOSE_Y - 1.25, zf + 0.18, "chrome")
	for x0, x1 in ((-0.36 * PX, -0.3), (0.3, 0.36 * PX)):  # lower intake, two slots
		box(model, "trim", "intake", x0, SILL_Y + 3.3, HALF_L - 0.4, x1, SILL_Y + 4.4, HALF_L + 0.02, "black")
	box(model, "plates", "plate_f", -0.26 * PX, SILL_Y + 0.9, HALF_L + 0.05, 0.26 * PX, SILL_Y + 2.5, HALF_L + 0.15, "plate",
		face_material={"south": "plate", "north": "black", "up": "black", "down": "black", "east": "black", "west": "black"})
	box(model, "trim", "plate_pocket_r", -0.29 * PX, BELT - 6.6, zr - 0.1, 0.29 * PX, BELT - 4.2, zr + 0.5, "black")
	box(model, "plates", "plate_r", -0.26 * PX, BELT - 6.3, zr - 0.2, 0.26 * PX, BELT - 4.5, zr - 0.1, "plate",
		face_material={"north": "plate", "south": "black", "up": "black", "down": "black", "east": "black", "west": "black"})


def build():
	model = Model()
	for name in ("body", "trim", "lamps", "plates", "glass", "mirror", "cabin"):
		model.group(name, (0, 0, 0))
	lower_body(model)
	glasshouse(model)
	nose_and_tail(model)
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
	fill_gradient("paint_side", (240, 240, 243), (228, 229, 233))  # nearly flat: one region covers faces of every size
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
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(150, 172, 186))
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
