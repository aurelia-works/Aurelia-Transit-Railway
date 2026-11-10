#!/usr/bin/env python3
"""Put the nose from tools/a320/a320_nose.bbmodel into the A320's OBJ.

    python3 tools/a320_nose_splice.py

Reads the bbmodel's nose_* meshes (ref_* meshes are the fuselage around it, for looking at only), drops the old nose
skin and cockpit panes from the base OBJ (the A320 as first committed) and writes a320.obj with the new nose:
the skin into a320_exterior with smooth normals, everything else into a320_exterior_flat with flat normals. Each
mesh face keeps the material of its Blockbench texture. The seam ring takes the fuselage's own normals, so the
join doesn't show.
"""
import json
import math
import os

import a320_nose as nose

BBMODEL = os.path.join(nose.ROOT, "tools/a320/a320_nose.bbmodel")
OUT = os.path.join(nose.ROOT, nose.OBJ_PATH)


def unit(v):
	length = math.sqrt(sum(c * c for c in v)) or 1.0
	return tuple(c / length for c in v)


def face_normal(points):
	n = [0.0, 0.0, 0.0]
	for i in range(len(points)):  # Newell's method, fine for any planar-ish polygon
		a, b = points[i], points[(i + 1) % len(points)]
		n[0] += (a[1] - b[1]) * (a[2] + b[2])
		n[1] += (a[2] - b[2]) * (a[0] + b[0])
		n[2] += (a[0] - b[0]) * (a[1] + b[1])
	return unit(n)


def key(p):
	return (round(p[0], 4), round(p[1], 4), round(p[2], 4))


def main():
	base_v, base_n, base_f = nose.parse_obj(nose.base_obj())
	model = json.load(open(BBMODEL))
	materials = [t["name"] for t in model["textures"]]

	verts, normals, groups = [], [], {}
	vindex, nindex = {}, {}

	def vid(p):
		k = key(p)
		if k not in vindex:
			vindex[k] = len(verts) + 1
			verts.append(k)
		return vindex[k]

	def nid(n):
		k = (round(n[0], 4), round(n[1], 4), round(n[2], 4))
		if k not in nindex:
			nindex[k] = len(normals) + 1
			normals.append(k)
		return nindex[k]

	def add(group, material, corners):
		groups.setdefault(group, {}).setdefault(material, [])
		for i in range(1, len(corners) - 1):  # MTR takes triangles
			groups[group][material].append((corners[0], corners[i], corners[i + 1]))

	# the base model, less what the new nose replaces
	seam_normals = {}
	for group, material, corners in base_f:
		points = [base_v[v - 1] for v, _ in corners]
		if nose.removed(group, material, [p[2] for p in points]):
			continue
		for (v, n), p in zip(corners, points):
			if abs(p[2] - nose.SEAM_Z) < 0.004 and group == "a320_exterior" and material == "body" and n:
				seam_normals.setdefault(key(p), []).append(base_n[n - 1])
		add(group, material, [(vid(p), nid(base_n[n - 1]) if n else nid(face_normal(points))) for (v, n), p in zip(corners, points)])

	# the nose meshes from Blockbench
	for element in model["elements"]:
		if element.get("type") != "mesh" or not element["name"].startswith("nose_"):
			continue
		pts = {k: tuple(c / nose.SCALE for c in v) for k, v in element["vertices"].items()}
		smooth = element["name"] == "nose_body"
		faces = [(f["vertices"], materials[f["texture"]] if f.get("texture") is not None else "body") for f in element["faces"].values()]
		vertex_normal = {}
		if smooth:
			acc = {}
			for keys, _ in faces:
				n = face_normal([pts[k] for k in keys])
				for k in keys:
					a = acc.setdefault(key(pts[k]), [0.0, 0.0, 0.0])
					for i in range(3):
						a[i] += n[i]
			for k, a in acc.items():
				shared = seam_normals.get(k)
				vertex_normal[k] = unit([sum(n[i] for n in shared) for i in range(3)]) if shared else unit(a)
		for keys, material in faces:
			points = [pts[k] for k in keys]
			flat = face_normal(points)
			group = "a320_exterior" if smooth else "a320_exterior_flat"
			add(group, material, [(vid(p), nid(vertex_normal[key(p)] if smooth else flat)) for p in points])

	order = ["a320_exterior", "a320_exterior_flat", "a320_door_l1", "a320_door_r1", "a320_door_l2", "a320_door_r2",
			"a320_lights", "a320_interior", "a320_cabin_light"]
	order += [g for g in groups if g not in order]
	with open(OUT, "w") as out:
		out.write("# Airbus A320-200 for MTR 4. Nose and cockpit windows from tools/a320/a320_nose.bbmodel (tools/a320_nose_splice.py)\n")
		out.write("mtllib a320.mtl\n")
		for v in verts:
			out.write("v %.4f %.4f %.4f\n" % v)
		for n in normals:
			out.write("vn %.4f %.4f %.4f\n" % n)
		triangles = 0
		for group in order:
			if group not in groups:
				continue
			out.write("g %s\n" % group)
			for material, tris in groups[group].items():
				out.write("usemtl %s\n" % material)
				for tri in tris:
					out.write("f " + " ".join("%d//%d" % c for c in tri) + "\n")
					triangles += 1
	print("wrote", OUT, len(verts), "vertices,", triangles, "triangles")


if __name__ == "__main__":
	main()
