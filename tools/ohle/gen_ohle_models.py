"""Writes the overhead line models and blockstates (contact_wire kinds, catenary_arm kinds). Run from the repo root.

Models are authored facing north: the wire runs along z; an arm points from its mast (south, +z) to the track (north, -z).
"""
import json
import os

NS = "aurelia_transit_architecture"
ROOT = "fabric/src/main/resources/assets/" + NS
MODELS = ROOT + "/models/block"
STATES = ROOT + "/blockstates"
MAX_LEVEL = 6
WIRE_HALF = 0.25       # contact and messenger wires: 0.5 px thick
DROPPER_HALF = 0.2     # droppers: 0.4 px, dark like the wires so they read against grass and sky
TUBE_HALF = 0.5        # cantilever tubes: 1 px


def faces(tex, uv=(7, 7, 9, 9)):
    return {d: {"uv": list(uv), "texture": tex} for d in ("north", "south", "east", "west", "up", "down")}


def box(a, b, tex, rotation=None, name=None):
    e = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": faces(tex)}
    if rotation:
        e["rotation"] = rotation
    if name:
        e["name"] = name
    return e


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def wire_model(level, dropper):
    top = 16 - level * 0.5
    els = [
        box([8 - WIRE_HALF, 8 - WIRE_HALF, 0], [8 + WIRE_HALF, 8 + WIRE_HALF, 16], "#wire", name="contact"),
        box([8 - WIRE_HALF, top - 2 * WIRE_HALF - 0.1, 0], [8 + WIRE_HALF, top, 16], "#wire", name="messenger"),
    ]
    if dropper:
        els.append(box([8 - DROPPER_HALF, 8 + WIRE_HALF, 8 - DROPPER_HALF], [8 + DROPPER_HALF, top - 0.6, 8 + DROPPER_HALF], "#dropper", name="dropper"))
        els.append(box([7.6, 7.5, 7.5], [8.4, 8.6, 8.5], "#dropper", name="clamp"))
    return {"parent": "block/block", "ambientocclusion": False,
            "textures": {"particle": NS + ":block/steel_dark", "wire": NS + ":block/steel_dark", "dropper": NS + ":block/steel_dark"},
            "elements": els}


def tube(y, z0, z1, tex="#tube"):
    return box([8 - TUBE_HALF, y - TUBE_HALF, z0], [8 + TUBE_HALF, y + TUBE_HALF, z1], tex)


STAY_Y = 13.5   # upper tube (the stay)
MAIN_Y = 4.5    # lower tube that carries the hanger
MAST_FACE = 21  # the mast's flange, seen from the root cell (the mast is 6 px wide in the next cell)


def arm_model(kind):
    els = []
    if kind == "root":
        els += [tube(STAY_Y, 0, MAST_FACE), tube(MAIN_Y, 0, MAST_FACE)]
        # 45 degree brace from the lower tube at the mast up to the stay
        length = (STAY_Y - MAIN_Y) * 2 ** 0.5
        cz, cy = MAST_FACE - 1 - (STAY_Y - MAIN_Y) / 2, (STAY_Y + MAIN_Y) / 2
        els.append(box([8 - TUBE_HALF, cy - TUBE_HALF, cz - length / 2], [8 + TUBE_HALF, cy + TUBE_HALF, cz + length / 2], "#tube",
                       rotation={"origin": [8, cy, cz], "axis": "x", "angle": 45}, name="brace"))
        # porcelain insulators next to the mast, and the bracket bolted to its flange
        els.append(box([6.75, STAY_Y - 1.25, 16.5], [9.25, STAY_Y + 1.25, 19.5], "#insulator", name="stay_insulator"))
        els.append(box([6.75, MAIN_Y - 1.25, 16.5], [9.25, MAIN_Y + 1.25, 19.5], "#insulator", name="main_insulator"))
        els.append(box([6.5, MAIN_Y - 1.5, 20], [9.5, STAY_Y + 1.5, MAST_FACE], "#clamp", name="bracket"))
    elif kind == "span":
        els += [tube(STAY_Y, 0, 16), tube(MAIN_Y, 0, 16)]
    else:  # tip: the tubes end over the wire, tied together, with the hanger down to the messenger below
        els += [tube(STAY_Y, 7.5, 16), tube(MAIN_Y, 7.5, 16)]
        els.append(box([7.6, MAIN_Y, 7.6], [8.4, STAY_Y, 8.4], "#tube", name="tie"))
        els.append(box([7.75, 0, 7.75], [8.25, MAIN_Y, 8.25], "#tube", name="hanger"))
        els.append(box([7.25, MAIN_Y - 1, 7.25], [8.75, MAIN_Y + 1, 8.75], "#clamp", name="clamp"))
    return {"parent": "block/block", "ambientocclusion": False,
            "textures": {"particle": NS + ":block/steel", "tube": NS + ":block/steel", "clamp": NS + ":block/steel_dark",
                         "insulator": NS + ":block/insulator"},
            "elements": els}


def facing_states(block, kinds):
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for kind in kinds:
            v = {"model": f"{NS}:block/{block}_{kind}"}
            if y:
                v["y"] = y
            variants[f"facing={facing},kind={kind}"] = v
    return {"variants": variants}


wire_kinds = []
for dropper in (False, True):
    for level in range(MAX_LEVEL + 1):
        kind = f"{'dropper' if dropper else 'level'}_{level}"
        wire_kinds.append(kind)
        write(f"{MODELS}/contact_wire_{kind}.json", wire_model(level, dropper))
write(f"{STATES}/contact_wire.json", facing_states("contact_wire", wire_kinds))
write(f"{MODELS}/contact_wire.json", {"parent": f"{NS}:block/contact_wire_dropper_0"})
for old in ("contact_wire_plain",):
    p = f"{MODELS}/{old}.json"
    if os.path.exists(p):
        os.remove(p)

for kind in ("root", "span", "tip"):
    write(f"{MODELS}/catenary_arm_{kind}.json", arm_model(kind))
write(f"{STATES}/catenary_arm.json", facing_states("catenary_arm", ("root", "span", "tip")))
print("wire kinds", len(wire_kinds), "arm kinds 3")
