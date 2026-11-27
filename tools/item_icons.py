"""Draws ATR's shaded 32 px item icons in the style of MTR's driver key: soft gradients, a darker rim instead of a black
outline, and a light sweep that passes over the icon every few seconds (an animated strip plus its .mcmeta).

Run from the repo root:  python3 tools/item_icons.py
Writes the transit card and Station Codex icons into fabric/src/main/resources/assets.
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

SIZE = 32
SS = 16  # supersampling: shapes are drawn at 512 px and scaled down
BIG = SIZE * SS
SWEEP_FRAMES = 10
IDLE_TICKS = 90
FRAME_TICKS = 2

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "fabric", "src", "main", "resources", "assets")
ATA_ITEMS = os.path.join(ROOT, "aurelia_transit_architecture", "textures", "item")


def lerp(a, b, t):
	return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(len(a)))


def hexc(value):
	value = value.lstrip("#")
	return tuple(int(value[i:i + 2], 16) for i in (0, 2, 4))


def gradient(size, top_left, bottom_right, angle=45):
	"""A linear gradient across the whole canvas, angle in degrees from the x axis."""
	w, h = size
	rad = math.radians(angle)
	dx, dy = math.cos(rad), math.sin(rad)
	span = abs(w * dx) + abs(h * dy)
	strip = Image.new("RGB", (256, 1))
	for i in range(256):
		strip.putpixel((i, 0), lerp(top_left, bottom_right, i / 255))
	out = Image.new("RGB", size)
	px = out.load()
	lut = [strip.getpixel((i, 0)) for i in range(256)]
	for y in range(h):
		for x in range(w):
			t = ((x * dx + y * dy) - min(0, w * dx) - min(0, h * dy)) / span
			px[x, y] = lut[max(0, min(255, int(t * 255)))]
	return out


def mask_of(draw_fn):
	mask = Image.new("L", (BIG, BIG), 0)
	draw_fn(ImageDraw.Draw(mask))
	return mask


def paint(canvas, mask, fill):
	"""Paints a colour or an RGB image through a mask onto an RGBA canvas."""
	layer = Image.new("RGBA", canvas.size, fill if isinstance(fill, tuple) else (0, 0, 0, 0))
	if not isinstance(fill, tuple):
		layer = fill.convert("RGBA")
	canvas.paste(layer, (0, 0), mask)


def rim(mask, width):
	"""The inner band of a shape, for the darker edge the driver key uses instead of an outline."""
	inner = mask.filter(ImageFilter.MinFilter(width * 2 + 1))
	return ImageChops.subtract(mask, inner)


def shrink(big):
	"""512 px to 32 px: average colour, then a hard alpha edge like MTR's key (no half-transparent pixels)."""
	small = big.resize((SIZE, SIZE), Image.LANCZOS)
	px = small.load()
	for y in range(SIZE):
		for x in range(SIZE):
			r, g, b, a = px[x, y]
			px[x, y] = (r, g, b, 255) if a >= 110 else (0, 0, 0, 0)
	return small


def with_sweep(big, shape):
	"""The icon plus SWEEP_FRAMES frames of a soft light band crossing it corner to corner, stacked as a strip."""
	frames = [shrink(big)]
	band_width = BIG * 0.16
	for f in range(SWEEP_FRAMES):
		centre = -BIG * 0.3 + (BIG * 1.6) * (f + 1) / (SWEEP_FRAMES + 1)
		band = Image.new("L", (BIG, BIG), 0)
		d = ImageDraw.Draw(band)
		# the band runs from bottom-left to top-right; its position moves along the other diagonal
		for i in range(-3, 4):
			alpha = int(190 * (1 - abs(i) / 4))
			offset = centre + i * band_width / 7
			d.polygon([(offset - BIG, -BIG + 0), (offset + BIG, BIG + 0 + BIG), (offset + BIG + band_width / 7, BIG * 2),
					(offset - BIG + band_width / 7, -BIG)], fill=alpha)
		band = band.filter(ImageFilter.GaussianBlur(SS * 0.8))
		band = ImageChops.multiply(band, shape)
		lit = big.copy()
		white = Image.new("RGBA", (BIG, BIG), (255, 255, 255, 255))
		lit.paste(white, (0, 0), band)
		frames.append(shrink(lit))
	strip = Image.new("RGBA", (SIZE, SIZE * len(frames)))
	for i, frame in enumerate(frames):
		strip.paste(frame, (0, i * SIZE))
	return strip


def mcmeta():
	frames = [{"index": 0, "time": IDLE_TICKS}] + list(range(1, SWEEP_FRAMES + 1))
	return {"animation": {"frametime": FRAME_TICKS, "interpolate": False, "frames": frames}}


def rotated(mask, degrees):
	return mask.rotate(degrees, resample=Image.BICUBIC, center=(BIG / 2, BIG / 2))


def transit_card():
	"""A contactless card at a slight tilt: blue body, gold chip, the contactless waves, a printed stripe."""
	tilt = 16
	card_box = (36, 120, BIG - 36, BIG - 120)

	def shape(d):
		d.rounded_rectangle(card_box, radius=44, fill=255)

	body = mask_of(shape)
	canvas = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
	paint(canvas, body, gradient((BIG, BIG), hexc("4F93EA"), hexc("143F8F"), 35))
	# glossy upper half
	gloss = mask_of(lambda d: d.rounded_rectangle((card_box[0], card_box[1], card_box[2], (card_box[1] + card_box[3]) // 2 - 20), radius=44, fill=70))
	paint(canvas, ImageChops.multiply(gloss, body), (255, 255, 255, 255))
	# printed band near the top
	paint(canvas, mask_of(lambda d: d.rectangle((card_box[0], 168, card_box[2], 206), fill=255)), hexc("0F3274") + (255,))
	# chip
	chip = (96, 236, 186, 310)
	paint(canvas, mask_of(lambda d: d.rounded_rectangle(chip, radius=14, fill=255)), gradient((BIG, BIG), hexc("FFE9A6"), hexc("B5832A"), 40))
	lines = mask_of(lambda d: [d.line((chip[0] + 8, y, chip[2] - 8, y), fill=255, width=6) for y in (258, 288)]
			+ [d.line(((chip[0] + chip[2]) // 2, chip[1] + 8, (chip[0] + chip[2]) // 2, chip[3] - 8), fill=255, width=6)])
	paint(canvas, lines, hexc("8A611C") + (255,))
	# contactless waves
	waves = mask_of(lambda d: [d.arc((200 + i * 26 - r, 273 - r, 200 + i * 26 + r, 273 + r), start=-50, end=50, fill=255, width=13)
			for i, r in enumerate((26, 46, 66))])
	paint(canvas, waves, (238, 246, 255, 255))
	# card number, as light dashes
	paint(canvas, mask_of(lambda d: [d.rounded_rectangle((96 + i * 82, 340, 166 + i * 82, 358), radius=8, fill=255) for i in range(4)]),
			(196, 220, 255, 255))
	# darker rim instead of an outline
	paint(canvas, rim(body, SS * 1), hexc("0B2A63") + (255,))
	canvas = canvas.rotate(tilt, resample=Image.BICUBIC, center=(BIG / 2, BIG / 2))
	return canvas, rotated(body, tilt)


def station_codex():
	"""A leather-bound guide: maroon cover, gold rails on the front, gold corners, cream page edges, a red ribbon."""
	cover = (80, 36, 404, 452)
	pages = [(404, 60), (452, 100), (452, 484), (124, 484), (80, 452), (404, 452)]

	canvas = Image.new("RGBA", (BIG, BIG), (0, 0, 0, 0))
	page_mask = mask_of(lambda d: d.polygon(pages, fill=255))
	paint(canvas, page_mask, gradient((BIG, BIG), hexc("FFF8E8"), hexc("D8C9A4"), 60))
	page_lines = mask_of(lambda d: [d.line((410, y, 450, y + 40), fill=255, width=5) for y in range(110, 450, 32)]
			+ [d.line((x, 456, x + 40, 482), fill=255, width=5) for x in range(110, 420, 34)])
	paint(canvas, ImageChops.multiply(page_lines, page_mask), hexc("B9A985") + (255,))
	paint(canvas, rim(page_mask, SS), hexc("9C8B66") + (255,))

	cover_mask = mask_of(lambda d: d.rounded_rectangle(cover, radius=22, fill=255))
	paint(canvas, cover_mask, gradient((BIG, BIG), hexc("A8402C"), hexc("4A130D"), 55))
	# spine band and its highlight
	paint(canvas, mask_of(lambda d: d.rounded_rectangle((cover[0], cover[1], cover[0] + 58, cover[3]), radius=22, fill=255)),
			gradient((BIG, BIG), hexc("6E1F15"), hexc("3A0E09"), 0))
	paint(canvas, mask_of(lambda d: d.rectangle((cover[0] + 58, cover[1] + 6, cover[0] + 66, cover[3] - 6), fill=255)), hexc("C9654D") + (255,))
	paint(canvas, mask_of(lambda d: [d.rectangle((cover[0] + 4, y, cover[0] + 54, y + 14), fill=255) for y in (100, 380)]),
			hexc("D9A93E") + (255,))

	# gold rails emblem with a dark emboss shadow
	def rails(d, dx=0, dy=0):
		for x in (204, 324):
			d.rectangle((x + dx, 116 + dy, x + 26 + dx, 376 + dy), fill=255)
		for y in (150, 232, 314):
			d.rectangle((176 + dx, y + dy, 378 + dx, y + 24 + dy), fill=255)

	paint(canvas, mask_of(lambda d: rails(d, 8, 8)), hexc("2E0905") + (255,))
	paint(canvas, mask_of(rails), gradient((BIG, BIG), hexc("FFE493"), hexc("B07A22"), 50))

	# gold corner caps on the outer edge
	def corners(d):
		d.polygon([(cover[2], cover[1]), (cover[2] - 64, cover[1]), (cover[2], cover[1] + 64)], fill=255)
		d.polygon([(cover[2], cover[3]), (cover[2] - 64, cover[3]), (cover[2], cover[3] - 64)], fill=255)

	paint(canvas, ImageChops.multiply(mask_of(corners), cover_mask), gradient((BIG, BIG), hexc("FFE493"), hexc("A06C1C"), 50))
	paint(canvas, rim(cover_mask, SS), hexc("33090A") + (255,))
	# ribbon bookmark hanging below the pages
	paint(canvas, mask_of(lambda d: d.polygon([(292, 452), (324, 452), (324, 506), (308, 492), (292, 506)], fill=255)), hexc("A8261B") + (255,))
	shape = ImageChops.lighter(ImageChops.lighter(cover_mask, page_mask), mask_of(lambda d: d.rectangle((292, 452, 324, 506), fill=255)))
	return canvas, shape


def write(directory, name, image):
	os.makedirs(directory, exist_ok=True)
	path = os.path.join(directory, name + ".png")
	image.save(path)
	with open(path + ".mcmeta", "w") as f:
		json.dump(mcmeta(), f, indent="\t")
		f.write("\n")
	print("wrote", os.path.relpath(path))


def main():
	preview = "--preview" in sys.argv
	for name, draw in (("transit_card", transit_card), ("station_codex", station_codex)):
		big, shape = draw()
		strip = with_sweep(big, shape)
		if preview:
			strip.resize((SIZE * 8, SIZE * 8 * (SWEEP_FRAMES + 1)), Image.NEAREST).save(name + "_preview.png")
		else:
			write(ATA_ITEMS, name, strip)


if __name__ == "__main__":
	main()
