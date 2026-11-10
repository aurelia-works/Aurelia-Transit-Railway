#!/usr/bin/env python3
"""Render the hatchback from its Blockbench file. Run with Blender, not python:

    /Applications/Blender.app/Contents/MacOS/Blender -b --python tools/road_car_render.py -- build/road-car

Cubes are baked the same way BlockbenchMesh.readCube bakes them (X, then Y, then Z),
then stood up in Blender's Z-up space. Body groups are multiplied by the panel's
default paint, and glass uses the same alpha the game does, so the pictures match
the model the renderer draws.
"""
import json
import math
import os
import sys

import bpy
from mathutils import Vector

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL = os.path.join(ROOT, "fabric/src/main/resources/assets/mtr/models/road_vehicle/hatchback.bbmodel")
TEXTURE = os.path.join(ROOT, "fabric/src/main/resources/assets/mtr/textures/entity/road_vehicle/hatchback.png")
PAINT = (0x2F / 255.0, 0x5D / 255.0, 0xA8 / 255.0)
GLASS_ALPHA = 120 / 255.0
PAINT_REGIONS = {"paint_side", "paint_top", "paint_low"}

# Face corners as {from=0, to=1} picks, in the same order BlockbenchMesh uses.
CORNERS = {
	"north": ((1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)),
	"south": ((0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)),
	"east": ((1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)),
	"west": ((0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)),
	"up": ((0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)),
	"down": ((0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)),
}
NORMALS = {
	"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}


def rotate(point, origin, rotation):
	x, y, z = point[0] - origin[0], point[1] - origin[1], point[2] - origin[2]
	rx, ry, rz = [math.radians(a) for a in rotation]
	y, z = y * math.cos(rx) - z * math.sin(rx), y * math.sin(rx) + z * math.cos(rx)
	x, z = x * math.cos(ry) + z * math.sin(ry), -x * math.sin(ry) + z * math.cos(ry)
	x, y = x * math.cos(rz) - y * math.sin(rz), x * math.sin(rz) + y * math.cos(rz)
	return (x + origin[0], y + origin[1], z + origin[2])


def to_blender(point):
	# Blockbench is Y-up with the nose at +Z. Blender is Z-up with the nose at +Y.
	return (point[0], point[2], point[1])


def out_dir():
	argv = sys.argv
	if "--" in argv:
		return os.path.abspath(argv[argv.index("--") + 1])
	return os.path.join(ROOT, "build/road-car")


def load():
	with open(MODEL) as handle:
		return json.load(handle)


def buckets(document):
	"""Quads grouped by the texture region, so paint, glass and lamps can shade differently."""
	width = document["resolution"]["width"]
	height = document["resolution"]["height"]
	regions = {}
	# The generator writes one texture and names regions only in the script. Recover them by UV.
	groups = {}
	for node in document["outliner"]:
		for child in node["children"]:
			groups[child] = node["name"]
	out = {}
	for element in document["elements"]:
		if element.get("type", "cube") != "cube":
			continue
		frm, to = element["from"], element["to"]
		origin = element.get("origin", [0, 0, 0])
		rotation = element.get("rotation", [0, 0, 0])
		box = (frm, to)
		group = groups.get(element["uuid"], "")
		for face, spec in element["faces"].items():
			uv = spec["uv"]
			key = (group if group in ("glass", "mirror") else "body" if _paint(uv, width, height) and group == "body" else face_key(uv))
			quads = out.setdefault(key, [])
			corners = []
			uvs = ((uv[0] / width, uv[1] / height), (uv[2] / width, uv[1] / height), (uv[2] / width, uv[3] / height), (uv[0] / width, uv[3] / height))
			normal = rotate((NORMALS[face][0] + origin[0], NORMALS[face][1] + origin[1], NORMALS[face][2] + origin[2]), origin, rotation)
			normal = (normal[0] - origin[0], normal[1] - origin[1], normal[2] - origin[2])
			for corner, uv_point in zip(CORNERS[face], uvs):
				point = (box[corner[0]][0], box[corner[1]][1], box[corner[2]][2])
				corners.append(rotate(point, origin, rotation))
			quads.append((corners, uvs, normal))
	return out


def _paint(uv, width, height):
	# paint_* regions sit in the top-left 128x128 of the texture, left of the black swatch.
	return uv[0] < 128 and uv[1] < 128 and not (uv[0] >= 128)


def face_key(uv):
	# Split the non-paint atlas into coarse materials by where the UV rectangle starts.
	u, v = uv[0], uv[1]
	if 128 <= v < 192 and u < 96:
		return "glass"
	if 96 <= u < 176 and 128 <= v < 176:
		return "lamp"
	if u >= 176 and 128 <= v < 176:
		return "lamp_tail"
	if 160 <= u < 208 and v < 48:
		return "rubber"
	if u >= 208 and v < 64:
		return "alloy"
	return "trim"


def mesh_object(name, quads):
	verts, faces, uvs, normals = [], [], [], []
	for corners, uv, normal in quads:
		start = len(verts)
		for corner in corners:
			verts.append(to_blender(corner))
		faces.append((start, start + 1, start + 2, start + 3))
		# Blender's image v runs bottom-up; the atlas is top-down.
		uvs.append([(u, 1.0 - v) for u, v in uv])
		normals.append(to_blender(normal))
	mesh = bpy.data.meshes.new(name)
	mesh.from_pydata(verts, [], faces)
	mesh.update()
	uv_layer = mesh.uv_layers.new(name="uv")
	for poly, loop_uvs, normal in zip(mesh.polygons, uvs, normals):
		poly.use_smooth = False
		for loop, uv in zip(poly.loop_indices, loop_uvs):
			uv_layer.data[loop].uv = uv
	obj = bpy.data.objects.new(name, mesh)
	bpy.context.collection.objects.link(obj)
	return obj


def material(name, kind):
	mat = bpy.data.materials.new(name)
	mat.use_nodes = True
	nodes = mat.node_tree.nodes
	links = mat.node_tree.links
	nodes.clear()
	out = nodes.new("ShaderNodeOutputMaterial")
	bsdf = nodes.new("ShaderNodeBsdfPrincipled")
	tex = nodes.new("ShaderNodeTexImage")
	tex.image = bpy.data.images.load(TEXTURE)
	tex.interpolation = "Closest"
	if kind == "body":
		mix = nodes.new("ShaderNodeMixRGB")
		mix.blend_type = "MULTIPLY"
		mix.inputs["Fac"].default_value = 1.0
		mix.inputs["Color2"].default_value = (*PAINT, 1)
		links.new(tex.outputs["Color"], mix.inputs["Color1"])
		links.new(mix.outputs["Color"], bsdf.inputs["Base Color"])
		bsdf.inputs["Roughness"].default_value = 0.32
		bsdf.inputs["Specular IOR Level"].default_value = 0.5
	elif kind == "glass":
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		bsdf.inputs["Alpha"].default_value = GLASS_ALPHA
		bsdf.inputs["Roughness"].default_value = 0.08
		mat.blend_method = "BLEND"
		mat.show_transparent_back = True
	elif kind == "lamp":
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		links.new(tex.outputs["Color"], bsdf.inputs["Emission Color"])
		bsdf.inputs["Emission Strength"].default_value = 1.4
		bsdf.inputs["Roughness"].default_value = 0.2
	elif kind == "lamp_tail":
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		links.new(tex.outputs["Color"], bsdf.inputs["Emission Color"])
		bsdf.inputs["Emission Strength"].default_value = 0.8
		bsdf.inputs["Roughness"].default_value = 0.25
	elif kind == "rubber":
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		bsdf.inputs["Roughness"].default_value = 0.85
	elif kind == "alloy":
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		bsdf.inputs["Metallic"].default_value = 0.85
		bsdf.inputs["Roughness"].default_value = 0.28
	else:
		links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
		bsdf.inputs["Roughness"].default_value = 0.55
	links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
	out.location = (400, 0)
	return mat


def bounds():
	xs, ys, zs = [], [], []
	for obj in bpy.context.scene.objects:
		if obj.type != "MESH":
			continue
		for corner in obj.bound_box:
			world = obj.matrix_world @ Vector(corner)
			xs.append(world.x)
			ys.append(world.y)
			zs.append(world.z)
	return (min(xs), max(xs)), (min(ys), max(ys)), (min(zs), max(zs))


def camera(name, location, ortho, scale):
	cam = bpy.data.cameras.new(name)
	cam.type = "ORTHO" if ortho else "PERSP"
	if ortho:
		cam.ortho_scale = scale
	else:
		cam.lens = 50
	obj = bpy.data.objects.new(name, cam)
	obj.location = location
	bpy.context.collection.objects.link(obj)
	return obj


def look_at(obj, target):
	direction = Vector(target) - obj.location
	obj.rotation_euler = direction.to_track_quat("-Z", "Y").to_euler()


def render_view(scene, obj, path):
	scene.camera = obj
	scene.render.filepath = path
	bpy.ops.render.render(write_still=True)
	print("rendered", path)


def main():
	folder = out_dir()
	os.makedirs(folder, exist_ok=True)
	bpy.ops.wm.read_factory_settings(use_empty=True)
	document = load()
	quads = buckets(document)
	kinds = {
		"body": "body", "glass": "glass", "mirror": "glass", "lamp": "lamp", "lamp_tail": "lamp_tail",
		"rubber": "rubber", "alloy": "alloy", "trim": "trim",
	}
	for key, faces in quads.items():
		obj = mesh_object(key, faces)
		kind = kinds.get(key, "trim")
		obj.data.materials.append(material(key, kind))
		print("mesh", key, "quads", len(faces))

	(xmin, xmax), (ymin, ymax), (zmin, zmax) = bounds()
	cx, cy, cz = (xmin + xmax) / 2, (ymin + ymax) / 2, (zmin + zmax) / 2
	span_x, span_y, span_z = xmax - xmin, ymax - ymin, zmax - zmin
	print("blender size x,y,z", round(span_x, 2), round(span_y, 2), round(span_z, 2))

	ground = bpy.data.meshes.new("ground")
	s = max(span_x, span_y) * 1.6
	ground.from_pydata([(-s, -s, 0), (s, -s, 0), (s, s, 0), (-s, s, 0)], [], [(0, 1, 2, 3)])
	gobj = bpy.data.objects.new("ground", ground)
	bpy.context.collection.objects.link(gobj)
	gmat = bpy.data.materials.new("ground")
	gmat.use_nodes = True
	gmat.node_tree.nodes["Principled BSDF"].inputs["Base Color"].default_value = (0.72, 0.73, 0.74, 1)
	gmat.node_tree.nodes["Principled BSDF"].inputs["Roughness"].default_value = 0.9
	gobj.data.materials.append(gmat)

	world = bpy.data.worlds.new("studio")
	bpy.context.scene.world = world
	world.use_nodes = True
	bg = world.node_tree.nodes["Background"]
	bg.inputs["Color"].default_value = (0.78, 0.80, 0.82, 1)
	bg.inputs["Strength"].default_value = 0.55

	sun = bpy.data.lights.new("sun", "SUN")
	sun.energy = 3.2
	sun.angle = math.radians(2)
	sun_obj = bpy.data.objects.new("sun", sun)
	sun_obj.location = (40, -30, 50)
	sun_obj.rotation_euler = (math.radians(55), math.radians(10), math.radians(35))
	bpy.context.collection.objects.link(sun_obj)
	fill = bpy.data.lights.new("fill", "AREA")
	fill.energy = 250
	fill.size = 30
	fill_obj = bpy.data.objects.new("fill", fill)
	fill_obj.location = (-25, 20, 18)
	look_at(fill_obj, (cx, cy, cz))
	bpy.context.collection.objects.link(fill_obj)

	scene = bpy.context.scene
	for engine in ("BLENDER_EEVEE", "BLENDER_EEVEE_NEXT", "CYCLES"):
		try:
			scene.render.engine = engine
			print("engine", engine)
			break
		except TypeError:
			continue
	scene.render.resolution_x = 1600
	scene.render.resolution_y = 1000
	scene.render.film_transparent = False
	try:
		scene.view_settings.view_transform = "Standard"
	except TypeError:
		pass
	if scene.render.engine == "CYCLES":
		scene.cycles.samples = 32
		scene.cycles.use_denoising = True

	target = (cx, cy, zmin + span_z * 0.42)
	side_scale = max(span_y, span_z) * 1.28
	front_scale = max(span_x, span_z) * 1.55
	# Nose is +Y. Left side of the car (driver) is +X.
	views = {
		"side": camera("side", (cx + span_x + 40, cy, target[2]), True, side_scale),
		"front": camera("front", (cx, cy + span_y + 40, target[2]), True, front_scale),
		"rear": camera("rear", (cx, cy - span_y - 40, target[2]), True, front_scale),
		"three-quarter": camera("three", (cx + span_x, cy + span_y, zmin + span_z * 0.85), False, 0),
	}
	for obj in views.values():
		look_at(obj, target)
	three = views["three-quarter"]
	offset = Vector(three.location) - Vector(target)
	three.location = Vector(target) + offset.normalized() * max(span_y, span_x) * 1.65

	for name, obj in views.items():
		render_view(scene, obj, os.path.join(folder, name + ".png"))


if __name__ == "__main__":
	main()
