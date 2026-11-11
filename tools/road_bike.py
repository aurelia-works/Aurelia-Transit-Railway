#!/usr/bin/env python3
"""City hire bike for bike share, as Blockbench cubes.

    python3 tools/road_bike.py

Writes the model the game loads and its texture:

    fabric/src/main/resources/assets/mtr/models/road_vehicle/bicycle.bbmodel
    fabric/src/main/resources/assets/mtr/textures/entity/road_vehicle/bicycle.png

A generic step-through city bike, the kind hire schemes use: 26 inch wheels, full
mudguards, a chain case, a front carrier with a basket and a hub lamp, no maker
mark. 16 Blockbench pixels are one metre. Groups follow BlockbenchMesh: body
takes the scheme colour from the panel, wheel_f steers and rolls, wheel_r rolls,
body_steer (the painted fork) and steer (bars, carrier, basket) turn with the
front wheel. +z is the front, +x the rider's left.

Render the four views with tools/road_car_render.py under Blender
(-- build/road-bike bicycle).
"""
import io
import math
import os

from PIL import Image, ImageDraw

import road_car as rc

MODEL_PATH = "fabric/src/main/resources/assets/mtr/models/road_vehicle/bicycle.bbmodel"
TEXTURE_PATH = "fabric/src/main/resources/assets/mtr/textures/entity/road_vehicle/bicycle.png"

PX = rc.PX
TEX = 64

REGIONS = {
	"paint": (0, 0, 16, 16),
	"paint_dark": (16, 0, 16, 16),
	"black": (32, 0, 8, 8),
	"rubber": (40, 0, 16, 16),
	"chrome": (0, 16, 16, 16),
	"alloy": (16, 16, 16, 16),
	"saddle": (32, 16, 16, 8),
	"lamp_front": (48, 16, 8, 8),
	"lamp_tail": (56, 16, 8, 8),
	"plate": (0, 32, 24, 12),
	"basket": (24, 32, 24, 16),
	"grip": (48, 32, 8, 8),
	"spoke": (56, 32, 8, 8),
	"saddle_top": (48, 40, 8, 8),
	"wire": (56, 40, 8, 8),
}

WHEEL_R = 0.340 * PX        # 26 inch tyre, outside
RIM_R = 0.300 * PX
TYRE_W = 0.062 * PX
AXLE_Y = WHEEL_R
AXLE_F = 0.550 * PX
AXLE_R = -0.550 * PX
TUBE = 0.034 * PX           # frame tube, square section
STAY = 0.022 * PX
HALF_STAYS = 0.060 * PX     # chain and seat stays either side of the wheel


def m(v):
	return v * PX


def tube(model, group, name, p0, p1, material, x0=-TUBE / 2, x1=TUBE / 2, thick=TUBE):
	"""A straight tube in the side plane from p0 to p1, each (y, z) in pixels, spanning x0..x1."""
	rc.slope_x(model, group, name, p0[0], p0[1], p1[0], p1[1], x0, x1, material, thick=thick)


def bend(model, group, name, points, material, **kw):
	"""Tubes through several points, so a curve reads as a few straight lengths."""
	for i in range(len(points) - 1):
		tube(model, group, "%s_%d" % (name, i), points[i], points[i + 1], material, **kw)


def wheel(model, suffix, cz):
	group = "wheel_" + suffix
	model.group(group, (0, AXLE_Y, cz))
	half = TYRE_W / 2
	rc.ring(model, group, "tyre", 0, AXLE_Y, cz, WHEEL_R, RIM_R + 0.05, -half, half, 32, "rubber", 0, 360)
	rc.ring(model, group, "rim", 0, AXLE_Y, cz, RIM_R + 0.06, RIM_R - 0.45, -half * 0.8, half * 0.8, 28, "alloy", 0, 360)
	# The rim's inner lip, lighter, so the rim reads as a channel and not a flat band.
	rc.ring(model, group, "rim_lip", 0, AXLE_Y, cz, RIM_R - 0.45, RIM_R - 0.6, -half * 0.55, half * 0.55, 28, "chrome", 0, 360)
	# Spokes: thin bars from the hub to the rim, laced alternately left and right of the hub flanges.
	for i in range(24):
		side = 1 if i % 2 == 0 else -1
		x = side * 0.18
		model.add(group, "spoke_%d" % i, (x - 0.08, AXLE_Y + 0.6, cz - 0.08), (x + 0.08, AXLE_Y + RIM_R - 0.4, cz + 0.08), "spoke",
				rotation=(i * 15.0, 0, 0), origin=(0, AXLE_Y, cz))
	# Hub: a dynamo or gear hub is fat on a city bike, with the axle nuts outside the dropouts.
	rc.ring(model, group, "hub", 0, AXLE_Y, cz, 1.0, 0.0, -0.6, 0.6, 10, "paint_dark", 0, 360)
	rc.box(model, group, "axle", -1.3, AXLE_Y - 0.25, cz - 0.25, 1.3, AXLE_Y + 0.25, cz + 0.25, "chrome")


def mudguard(model, cz, ang0, ang1):
	# The inner face in the darker paint, so it does not glow from below.
	rc.ring(model, "body", "guard", 0, AXLE_Y, cz, WHEEL_R + 0.32, WHEEL_R + 0.22, -0.35, 0.35, 16, "paint", ang0, ang1,
		face_material={"up": "paint_dark"})
	rc.ring(model, "body", "guard", 0, AXLE_Y, cz, WHEEL_R + 0.22, WHEEL_R + 0.12, -0.35, 0.35, 16, "paint", ang0 + 4, ang1 - 4,
		face_material={"down": "paint_dark"})
	# A thin stay from each end of the guard back to the axle, either side of the tyre.
	for side in (-1, 1):
		x = side * 0.5
		for ang in (ang0 + 6, ang1 - 6):
			rad = math.radians(ang)
			tip = (AXLE_Y + (WHEEL_R + 0.2) * math.cos(rad), cz + (WHEEL_R + 0.2) * math.sin(rad))
			tube(model, "trim", "guard_stay", (AXLE_Y, cz), tip, "black", x0=x - 0.05, x1=x + 0.05, thick=0.1)


# Frame points, (y, z) in metres.
BB = (0.290, -0.070)
SEAT_TOP = (0.840, -0.270)
HEAD_TOP = (0.980, 0.340)
HEAD_LOW = (0.790, 0.362)
HEAD_BASE = (0.745, 0.409)  # the head tube runs down to the fork crown
CROWN = (0.740, 0.410)
STEER_TOP = (1.060, 0.320)


def pt(p):
	return (m(p[0]), m(p[1]))


def frame(model):
	model.group("body", (0, 0, 0))
	model.group("trim", (0, 0, 0))
	# Step-through: one low swept tube from the head to the bottom bracket, and a short reinforcing bridge.
	bend(model, "body", "down", [pt(HEAD_LOW), pt((0.540, 0.250)), pt(BB)], "paint", thick=TUBE * 1.4, x0=-TUBE * 0.7, x1=TUBE * 0.7)
	# The second, higher step-through tube, into the seat tube 0.2 m over the bottom bracket.
	tube(model, "body", "low", pt((0.900, 0.355)), pt((0.490, -0.143)), "paint", thick=TUBE * 1.3, x0=-TUBE * 0.65, x1=TUBE * 0.65)
	tube(model, "body", "seat_tube", pt(BB), pt(SEAT_TOP), "paint", x0=-TUBE * 0.6, x1=TUBE * 0.6, thick=TUBE * 1.2)
	tube(model, "body", "head", pt(HEAD_BASE), pt(HEAD_TOP), "paint", x0=-TUBE * 0.75, x1=TUBE * 0.75, thick=TUBE * 1.5)
	for side in (-1, 1):
		x0, x1 = side * HALF_STAYS - STAY / 2, side * HALF_STAYS + STAY / 2
		tube(model, "body", "chain_stay", pt(BB), (AXLE_Y, AXLE_R), "paint", x0=x0, x1=x1, thick=STAY)
		tube(model, "body", "seat_stay", pt((SEAT_TOP[0] - 0.040, SEAT_TOP[1] + 0.010)), (AXLE_Y, AXLE_R), "paint", x0=x0, x1=x1, thick=STAY)
		# Rear dropout plate.
		rc.box(model, "trim", "dropout", x0 - 0.05, AXLE_Y - 0.5, AXLE_R - 0.6, x1 + 0.05, AXLE_Y + 0.5, AXLE_R + 0.6, "paint_dark")
	# Bottom bracket shell.
	rc.box(model, "body", "bb", -0.95, m(BB[0]) - 0.45, m(BB[1]) - 0.45, 0.95, m(BB[0]) + 0.45, m(BB[1]) + 0.45, "paint_dark")
	# Full chain case: the city-bike tell. Painted, outboard of the right chain stay (-x is the rider's right), round the chainring.
	cx = -(HALF_STAYS + 0.55)
	rc.ring(model, "body", "case_ring", cx, m(BB[0]), m(BB[1]), 2.1, 0.0, cx - 0.3, cx + 0.3, 14, "paint", 0, 360)
	rc.slope_x(model, "body", "case_top", m(BB[0]) + 1.95, m(BB[1]), AXLE_Y + 1.4, AXLE_R, cx - 0.3, cx + 0.3, "paint", thick=0.3)
	rc.slope_x(model, "body", "case_edge", m(BB[0]) + 1.95, m(BB[1]), AXLE_Y + 1.4, AXLE_R, cx - 0.4, cx - 0.3, "paint_dark", thick=0.12)
	rc.slope_x(model, "body", "case_low", m(BB[0]) - 1.6, m(BB[1]) - 0.8, AXLE_Y - 1.1, AXLE_R, cx - 0.3, cx + 0.3, "paint", thick=0.3)
	# The cover between them, following the chain line, and a darker inspection flap on it.
	rc.slope_x(model, "body", "case_side", m(BB[0]), m(BB[1]) - 1.0, AXLE_Y, AXLE_R, cx - 0.05, cx + 0.2, "paint", thick=2.4,
		skip=("east",))
	flap_y = (m(BB[0]) + AXLE_Y) / 2
	flap_z = (m(BB[1]) + AXLE_R) / 2
	rc.box(model, "body", "case_flap", cx - 0.2, flap_y - 0.5, flap_z - 1.0, cx - 0.05, flap_y + 0.5, flap_z + 1.0, "paint_dark")
	for dz in (-0.75, 0.75):
		rc.box(model, "trim", "case_screw", cx - 0.26, flap_y - 0.1, flap_z + dz - 0.1, cx - 0.2, flap_y + 0.1, flap_z + dz + 0.1, "chrome")
	rc.ring(model, "body", "case_end", cx, AXLE_Y, AXLE_R, 1.2, 0.0, cx - 0.3, cx + 0.3, 10, "paint", 0, 360)
	# A darker rim round the chainring end, so the case reads as a cover and not a shadow.
	rc.ring(model, "body", "case_rim", cx, m(BB[0]), m(BB[1]), 2.2, 1.85, cx - 0.4, cx - 0.25, 14, "paint_dark", 0, 360)
	mudguard(model, AXLE_R, -85, 115)
	mudguard(model, AXLE_F, -55, 115)


def seat(model):
	# Seat post, a quick release, and a sprung saddle.
	tube(model, "trim", "post", pt(SEAT_TOP), pt((1.030, -0.339)), "chrome", x0=-0.24, x1=0.24, thick=0.45)
	rc.box(model, "body", "collar", -0.4, m(SEAT_TOP[0]) - 0.2, m(SEAT_TOP[1]) - 0.4, 0.4, m(SEAT_TOP[0]) + 0.45, m(SEAT_TOP[1]) + 0.4, "paint_dark")
	y0 = m(1.035)
	z_nose, z_back = m(-0.190), m(-0.450)
	rc.box(model, "trim", "rails", -0.55, y0 - 0.2, z_back + 0.1, 0.55, y0, z_nose, "chrome")
	for side in (-1, 1):
		rc.box(model, "trim", "spring", side * 0.45 - 0.18, y0 - 1.0, z_back + 0.6, side * 0.45 + 0.18, y0, z_back + 1.0, "chrome")
	# Wide and tall at the back, dipping to a narrow, lower nose.
	rc.box(model, "trim", "saddle_tail", -1.75, y0, z_back, 1.75, y0 + 1.0, z_back + 0.6, "saddle")
	rc.box(model, "trim", "saddle_back", -1.75, y0, z_back + 0.6, 1.75, y0 + 1.3, z_back + 1.9, "saddle")
	rc.box(model, "trim", "saddle_mid", -1.15, y0 + 0.1, z_back + 1.9, 1.15, y0 + 0.9, z_back + 3.0, "saddle", face_material={"up": "saddle_top"})
	rc.box(model, "trim", "saddle_nose", -0.35, y0 + 0.15, z_back + 3.0, 0.35, y0 + 0.95, z_nose, "saddle", face_material={"up": "saddle_top"})


def drivetrain(model):
	# Cranks, pedals: the right crank forward and the left back, as a parked bike stands.
	for side, ang in ((1, 110.0), (-1, -70.0)):
		x0 = side * (HALF_STAYS + 0.75)
		rad = math.radians(ang)
		length = m(0.170)
		tip = (m(BB[0]) + length * math.cos(rad), m(BB[1]) + length * math.sin(rad))
		tube(model, "trim", "crank", (m(BB[0]), m(BB[1])), tip, "chrome", x0=x0 - 0.2, x1=x0 + 0.2, thick=0.6)
		bx = x0 + side * 0.2
		rc.ring(model, "trim", "crank_bolt", bx, m(BB[0]), m(BB[1]), 0.4, 0.0, min(bx, bx + side * 0.12), max(bx, bx + side * 0.12), 8, "chrome", 0, 360)
		px0 = x0 + side * 0.2
		px1 = x0 + side * 1.2
		rc.box(model, "trim", "pedal", min(px0, px1), tip[0] - 0.22, tip[1] - 0.75, max(px0, px1), tip[0] + 0.22, tip[1] + 0.75, "black")
		rc.box(model, "trim", "reflector", min(px0, px1) + 0.25, tip[0] - 0.1, tip[1] - 0.78, max(px0, px1) - 0.25, tip[0] + 0.1, tip[1] - 0.74,
			"lamp_tail")


def steering(model):
	"""Fork, stem, bars and the front carrier: one group turned with the front wheel about the front axle's upright."""
	model.group("body_steer", (0, AXLE_Y, AXLE_F))
	model.group("steer", (0, AXLE_Y, AXLE_F))
	# Fork blades from the crown to the axle, either side of the tyre, in the frame's paint.
	for side in (-1, 1):
		x = side * 0.78
		tube(model, "body_steer", "blade", pt(CROWN), (AXLE_Y, AXLE_F), "paint", x0=x - 0.2, x1=x + 0.2, thick=0.5)
	# The crown's top face meets the head tube's bottom, so head, crown and blades read as one fork.
	rc.box(model, "body_steer", "crown", -0.9, m(CROWN[0]) - 0.3, m(CROWN[1]) - 0.3, 0.9, m(0.760), m(CROWN[1]) + 0.3, "paint")
	# Steerer and an upright city stem, then swept-back bars.
	tube(model, "steer", "stem", pt(HEAD_TOP), pt(STEER_TOP), "chrome", x0=-0.35, x1=0.35, thick=0.7)
	bar_y = m(STEER_TOP[0]) + 0.1
	bar_z = m(STEER_TOP[1])
	rc.box(model, "steer", "bar_mid", -1.8, bar_y - 0.22, bar_z - 0.22, 1.8, bar_y + 0.22, bar_z + 0.22, "chrome")
	rc.box(model, "steer", "bar_clamp", -0.3, bar_y - 0.2, bar_z - 0.3, 0.3, bar_y + 0.2, bar_z + 0.3, "chrome")
	sweep = 45.0
	for side in (-1, 1):
		# Each half sweeps back toward the rider: a bar along x turned about the end of the middle piece, then the grip on it.
		x_in = side * 1.8
		turn = (0, side * sweep, 0)

		def along(name, a, b, material, half):
			x0, x1 = (x_in + a, x_in + b) if side > 0 else (x_in - b, x_in - a)
			rc.box(model, "steer", name, x0, bar_y - half, bar_z - half, x1, bar_y + half, bar_z + half, material, rotation=turn, origin=(x_in, bar_y, bar_z))

		along("bar_sweep", 0, 2.3, "chrome", 0.22)
		along("grip", 2.3, 3.6, "grip", 0.2)
		# The brake lever hangs in front of the grip.
		reach = 2.6
		lx = x_in + side * reach * math.cos(math.radians(sweep))
		lz = bar_z - reach * math.sin(math.radians(sweep)) + 0.45
		rc.box(model, "steer", "lever", lx - 0.3, bar_y - 0.45, lz - 0.1, lx + 0.3, bar_y - 0.15, lz + 0.1, "black", rotation=turn, origin=(lx, bar_y, lz))
	# Front carrier on the head tube, a wire basket on it, the scheme's number board on the front.
	deck_y = m(0.860)
	z0, z1 = m(0.370), m(0.650)
	half = 2.0
	rc.box(model, "steer", "deck", -half, deck_y - 0.2, z0, half, deck_y, z1, "black")
	# Two stays from the deck's front corners down to the fork ends, splayed into an A seen from the front.
	for side in (-1, 1):
		tube(model, "steer", "carrier_stay", (deck_y - 0.2, z1 - 0.1), (AXLE_Y + 0.3, AXLE_F + 0.1), "black",
			x0=side * (half - 0.1) - 0.12, x1=side * (half - 0.1) + 0.12, thick=0.25)
	# Bolted to the head tube under the deck's back edge.
	rc.box(model, "steer", "carrier_head", -0.4, deck_y - 1.2, z0 - 0.12, 0.4, deck_y - 0.2, z0 + 0.13, "black")
	wall = 0.12
	top = deck_y + m(0.130)
	rc.box(model, "steer", "basket_l", -half, deck_y, z0, -half + wall, top, z1, "basket")
	rc.box(model, "steer", "basket_r", half - wall, deck_y, z0, half, top, z1, "basket")
	rc.box(model, "steer", "basket_b", -half + wall, deck_y, z0, half - wall, top, z0 + wall, "basket")
	rc.box(model, "steer", "basket_f", -half + wall, deck_y, z1 - wall, half - wall, top, z1, "basket")
	rc.box(model, "steer", "basket_rim", -half - 0.05, top - 0.15, z0 - 0.05, half + 0.05, top + 0.05, z1 + 0.05, "wire", skip=("up", "down"))
	rc.box(model, "steer", "board", -1.3, deck_y + 0.2, z1, 1.3, deck_y + 0.2 + 0.6 * (top - deck_y), z1 + 0.12, "plate", skip=("north",), flip_u=True)
	# Hub-dynamo lamp under the front of the carrier, as hire bikes carry it.
	rc.box(model, "steer", "lamp", -0.45, deck_y - 0.8, z1 - 0.5, 0.45, deck_y - 0.2, z1, "black", face_material={"south": "lamp_front"})


def rear(model):
	# Rear carrier over the mudguard, the rear lamp on its tail.
	deck_y = m(0.715)
	z0, z1 = m(-0.760), m(-0.300)
	rc.box(model, "trim", "rack_deck", -2.0, deck_y - 0.35, z0, 2.0, deck_y, z1, "black")
	rc.box(model, "trim", "rack_lip", -2.0, deck_y, z0, 2.0, deck_y + 0.3, z0 + 0.2, "black")
	for side in (-1, 1):
		x = side * 1.9
		tube(model, "trim", "rack_stay", (deck_y - 0.18, z0 + 0.4), (AXLE_Y, AXLE_R), "black", x0=x - 0.12, x1=x + 0.12, thick=0.24)
	tube(model, "trim", "rack_bridge", (deck_y - 0.18, z1), pt((SEAT_TOP[0] - 0.080, SEAT_TOP[1] + 0.022)), "black", x0=-0.2, x1=0.2, thick=0.35)
	rc.box(model, "trim", "tail_lamp", -0.3, deck_y - 0.75, z0, 0.3, deck_y - 0.35, z0 + 0.4, "black", face_material={"north": "lamp_tail"})


def build():
	model = rc.Model(REGIONS, space="road_bike")
	frame(model)
	seat(model)
	drivetrain(model)
	rear(model)
	steering(model)
	wheel(model, "f", AXLE_F)
	wheel(model, "r", AXLE_R)
	return model


def texture():
	image = Image.new("RGB", (TEX, TEX), (20, 20, 20))
	draw = ImageDraw.Draw(image)

	def fill(region, colour):
		x, y, w, h = REGIONS[region]
		draw.rectangle([x, y, x + w - 1, y + h - 1], fill=colour)

	# The paint is light: the game multiplies it by the scheme colour from the panel.
	fill("paint", (244, 244, 246))
	x, y, w, h = REGIONS["paint"]
	draw.line([(x, y), (x + w - 1, y)], fill=(255, 255, 255))
	fill("paint_dark", (150, 152, 158))
	fill("black", (22, 22, 24))
	fill("rubber", (30, 30, 32))
	x, y, w, h = REGIONS["rubber"]
	draw.rectangle([x, y, x + 2, y + h - 1], fill=(60, 60, 62))  # reflective sidewall stripe
	x, y, w, h = REGIONS["chrome"]
	for row in range(h):
		c = 160 + int(70 * row / h)
		draw.line([(x, y + row), (x + w - 1, y + row)], fill=(c, c, c + 6))
	fill("alloy", (148, 152, 158))
	fill("saddle", (68, 58, 52))
	fill("saddle_top", (88, 76, 68))
	fill("wire", (80, 82, 86))
	x, y, w, h = REGIONS["saddle"]
	draw.line([(x, y + 1), (x + w - 1, y + 1)], fill=(60, 50, 44))
	fill("lamp_front", (250, 248, 226))
	x, y, w, h = REGIONS["lamp_front"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], outline=(90, 90, 94))
	fill("lamp_tail", (200, 26, 32))
	fill("grip", (40, 34, 30))
	fill("spoke", (210, 214, 220))
	# Number board: white, a bike symbol and a dock number.
	x, y, w, h = REGIONS["plate"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(240, 240, 236))
	draw.rectangle([x, y, x + w - 1, y + h - 1], outline=(30, 30, 32))
	draw.ellipse([x + 2, y + 5, x + 6, y + 9], outline=(30, 30, 32))
	draw.ellipse([x + 7, y + 5, x + 11, y + 9], outline=(30, 30, 32))
	draw.line([(x + 4, y + 7), (x + 7, y + 4), (x + 9, y + 7)], fill=(30, 30, 32))
	for i, digit_x in enumerate((13, 17)):
		draw.rectangle([x + digit_x, y + 3, x + digit_x + 2, y + 9], outline=(30, 30, 32))
	# Basket: a dark wire grid, so its walls read as mesh rather than plates.
	x, y, w, h = REGIONS["basket"]
	draw.rectangle([x, y, x + w - 1, y + h - 1], fill=(48, 50, 54))
	for i in range(0, w, 2):
		draw.line([(x + i, y), (x + i, y + h - 1)], fill=(20, 20, 22))
	for j in range(0, h, 2):
		draw.line([(x, y + j), (x + w - 1, y + j)], fill=(20, 20, 22))
	return image


def main():
	model = build()
	image = texture()
	buf = io.BytesIO()
	image.save(buf, "png")
	raw = buf.getvalue()
	tex_path = os.path.join(rc.ROOT, TEXTURE_PATH)
	os.makedirs(os.path.dirname(tex_path), exist_ok=True)
	with open(tex_path, "wb") as handle:
		handle.write(raw)
	model_path = rc.write_bbmodel(model, raw, MODEL_PATH, "bicycle", TEX)
	mins, maxs = rc.baked_bounds(model)
	size = [(maxs[i] - mins[i]) / PX for i in range(3)]
	print("cubes", len(model.elements))
	print("groups", ", ".join("%s:%d" % (name, len(group["children"])) for name, group in model.groups.items()))
	print("bbox m  y %.3f..%.3f  z %.3f..%.3f" % (mins[1] / PX, maxs[1] / PX, mins[2] / PX, maxs[2] / PX))
	print("size m  %.3f long, %.3f wide, %.3f high" % (size[2], size[0], size[1]))
	print("wrote", model_path)
	print("wrote", tex_path)


if __name__ == "__main__":
	main()
