"""Paints the NS platform textures (dark paver, edge with coping and dashed line, guidance lines) and writes the two new
blocks' models, blockstates, item models, loot tables and lang. Run from the repo root. Textures are north-authored: the
track is on the north (top) side; the platform runs along x."""
import json
import random
from PIL import Image

NS = "aurelia_transit_architecture"
A = f"fabric/src/main/resources/assets/{NS}"
D = f"fabric/src/main/resources/data/{NS}"
rnd = random.Random(7)


def jitter(c, n=6):
    d = rnd.randint(-n, n)
    return tuple(max(0, min(255, v + d)) for v in c)


PAVER = (84, 76, 72)     # Leidsche Rijn's dark brown-grey concrete tiles
JOINT = (62, 56, 53)
COPING = (178, 175, 168)
COPING_LIP = (150, 147, 140)
WHITE = (226, 226, 220)


def paver(img, y0=0):
    for y in range(y0, 16):
        for x in range(16):
            joint = x % 8 == 7 or (y % 8 == 7)
            img.putpixel((x, y), jitter(JOINT if joint else PAVER, 3 if joint else 6))


def save(img, name):
    img.save(f"{A}/textures/block/{name}.png")


img = Image.new("RGB", (16, 16))
paver(img)
save(img, "nlbe_paver")

edge = Image.new("RGB", (16, 16))
paver(edge)
for y in range(0, 3):
    for x in range(16):
        edge.putpixel((x, y), jitter(COPING_LIP if y == 0 else COPING, 4))
for y in (6, 7):
    for x in range(1, 10):
        edge.putpixel((x, y), jitter(WHITE, 4))
save(edge, "ns_platform_edge_top")

guide = Image.new("RGB", (16, 16))
paver(guide)
for y in (5, 8, 11):
    for x in range(16):
        guide.putpixel((x, y), jitter(WHITE, 5))
        if x % 2 == 0:
            guide.putpixel((x, y), jitter((200, 200, 194), 3))
save(guide, "ns_platform_guidance_top")

side = Image.new("RGB", (16, 16))
for y in range(16):
    for x in range(16):
        side.putpixel((x, y), jitter((150, 146, 140), 5))
save(side, "ns_platform_side")


def write(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


edge_model = json.load(open(f"{A}/models/block/platform_edge.json"))
edge_model["textures"] = {"particle": f"{NS}:block/ns_platform_edge_top", "top": f"{NS}:block/ns_platform_edge_top",
                          "coping": f"{NS}:block/ns_platform_side", "face": f"{NS}:block/ns_platform_side", "side": f"{NS}:block/ns_platform_side"}
write(f"{A}/models/block/ns_platform_edge.json", edge_model)
write(f"{A}/models/block/ns_platform_guidance.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
    "top": f"{NS}:block/ns_platform_guidance_top", "bottom": f"{NS}:block/ns_platform_side", "side": f"{NS}:block/ns_platform_side",
    "particle": f"{NS}:block/ns_platform_guidance_top"}})
for block in ("ns_platform_edge", "ns_platform_guidance"):
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"{NS}:block/{block}"}
        if y:
            v["y"] = y
        variants[f"facing={facing}"] = v
    write(f"{A}/blockstates/{block}.json", {"variants": variants})
    write(f"{A}/models/item/{block}.json", {"parent": f"{NS}:block/{block}"})
    loot = json.load(open(f"{D}/loot_tables/blocks/platform_edge.json"))
    loot["pools"][0]["entries"][0]["name"] = f"{NS}:{block}"
    write(f"{D}/loot_tables/blocks/{block}.json", loot)

p = f"{A}/lang/en_us.json"
s = open(p).read()
anchor = f'  "block.{NS}.leidsche_rijn_concrete_platform_paver"'
i = s.index(anchor)
s = s[:i] + f'  "block.{NS}.ns_platform_edge": "NS Platform Edge",\n  "block.{NS}.ns_platform_guidance": "NS Platform Guidance Line",\n' + s[i:]
open(p, "w").write(s)
json.loads(s)
p = "fabric/src/main/resources/data/minecraft/tags/blocks/mineable/pickaxe.json"
s = open(p).read()
s = s.replace(f'\t\t"{NS}:catenary_arm",', f'\t\t"{NS}:catenary_arm",\n\t\t"{NS}:ns_platform_edge",\n\t\t"{NS}:ns_platform_guidance",', 1)
open(p, "w").write(s)
json.loads(s)
print("ok")
