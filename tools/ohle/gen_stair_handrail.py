"""Sloped stair handrail (stair_handrail, ns_stair_handrail): models, blockstates, item models and loot tables.

Each model faces north and climbs toward it: one block up per block along, the rails 14 px above the step nosings at the
downhill (south) face, as the level handrail stands above a floor. kind: bottom (a newel post at its foot), middle, top (runs
on level into the landing's rail at its uphill face); edge: the rail on the middle of the block, or on its left (west) or
right (east) side, where an edge handrail stands. Run from the repo root: python3 tools/ohle/gen_stair_handrail.py
"""
import json, math, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "fabric", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "aurelia_transit_architecture")
D = os.path.join(ROOT, "data", "aurelia_transit_architecture")
NS = "aurelia_transit_architecture"
BLOCKS = {"stair_handrail": NS + ":block/steel", "ns_stair_handrail": NS + ":block/ns_rail_red"}
KINDS = ("bottom", "middle", "top")
EDGES = {"none": 0.0, "left": -6.5, "right": 6.5}
FACINGS = {"north": 0, "east": 90, "south": 180, "west": 270}
HALF = 8 * math.sqrt(2)  # half the rail's length along its slope: one block along, one up


def box(frm, to, rot=None, uv_len=16):
    """A cuboid with every face textured from the rail texture's middle strip."""
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        faces[face] = {"uv": [7, 0, 9, uv_len] if face in ("up", "down", "east", "west") else [7, 7, 9, 9], "texture": "#rail"}
    el = {"from": [round(c, 4) for c in frm], "to": [round(c, 4) for c in to], "faces": faces}
    if rot:
        el["rotation"] = rot
    return el


def sloped(dx, width, mid_y, thick):
    """A rail along the slope: its centre line from (z 16, mid_y - 8) to (z 0, mid_y + 8)."""
    x0 = 8 + dx - width / 2
    return box([x0, mid_y - thick / 2, 8 - HALF], [x0 + width, mid_y + thick / 2, 8 + HALF],
               {"origin": [8 + dx, mid_y, 8], "axis": "x", "angle": 45})


def model(kind, dx, texture):
    els = [
        # the handrail (centre 22 at mid-block: 14 at the downhill face) and the rail under it (7.75 at the face)
        sloped(dx, 2, 22, 2),
        sloped(dx, 1, 15.75, 1.5),
        # a post at mid-block up into the handrail
        box([7 + dx, 0, 7], [9 + dx, 21, 9]),
    ]
    if kind == "bottom":
        # the newel post at the foot of the flight, capped
        els.append(box([7 + dx, 0, 14], [9 + dx, 16, 16]))
        els.append(box([6.5 + dx, 15.5, 13.5], [9.5 + dx, 16, 16.5]))
    if kind == "top":
        # knuckles where the slope bends level at the landing (a hair fatter than the rails, so no faces meet flush)
        els.append(box([6.9 + dx, 28.9, -1], [9.1 + dx, 31.1, 1]))
        els.append(box([7.4 + dx, 22.9, -1], [8.6 + dx, 24.6, 1]))
        if dx == 0:
            # a middle rail on the landing has no arm toward the stair: run on level to its post (an edge rail runs the
            # whole side already)
            els.append(box([7, 29, -7], [9, 31, 0]))
            els.append(box([7.5, 23, -7], [8.5, 24.5, 0]))
    return {"parent": "block/block", "textures": {"particle": texture, "rail": texture}, "elements": els}


def dump(data, path):
    """JSON with one line per element or variant, so the files stay short to read and diff."""
    lines = []
    for key, value in data.items():
        if isinstance(value, list):
            inner = ",\n".join("    " + json.dumps(v) for v in value)
            lines.append(f'  {json.dumps(key)}: [\n{inner}\n  ]')
        elif isinstance(value, dict) and key == "variants":
            inner = ",\n".join(f"    {json.dumps(k)}: {json.dumps(v)}" for k, v in value.items())
            lines.append(f'  {json.dumps(key)}: {{\n{inner}\n  }}')
        else:
            lines.append(f"  {json.dumps(key)}: {json.dumps(value)}")
    open(path, "w").write("{\n" + ",\n".join(lines) + "\n}\n")


for block, texture in BLOCKS.items():
    variants = {}
    for kind in KINDS:
        for edge, dx in EDGES.items():
            name = f"{block}_{kind}" + ("" if edge == "none" else f"_{edge}")
            steel = f"stair_handrail_{kind}" + ("" if edge == "none" else f"_{edge}")
            # the NS rails are the steel ones painted red
            out = model(kind, dx, texture) if name == steel else {"parent": f"{NS}:block/{steel}", "textures": {"particle": texture, "rail": texture}}
            dump(out, os.path.join(A, "models/block", name + ".json"))
            for facing, y in FACINGS.items():
                variant = {"model": f"{NS}:block/{name}"}
                if y:
                    variant["y"] = y
                variants[f"edge={edge},facing={facing},kind={kind}"] = variant
    dump({"variants": variants}, os.path.join(A, "blockstates", block + ".json"))
    item = {"parent": f"{NS}:block/{block}_middle",
            "display": {"gui": {"rotation": [30, 135, 0], "translation": [0, -3, 0], "scale": [0.5, 0.5, 0.5]}}}
    dump(item, os.path.join(A, "models/item", block + ".json"))
    loot = {"type": "minecraft:block", "pools": [{"rolls": 1, "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": f"{NS}:{block}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]}
    dump(loot, os.path.join(D, "loot_tables/blocks", block + ".json"))
