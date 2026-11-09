"""NS red handrail: the handrail's models and blockstate with its posts and rails painted NS signal red (ref02/ref03)."""
import json, os, re
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "fabric", "src", "main", "resources")
A = os.path.join(ROOT, "assets", "aurelia_transit_architecture")
D = os.path.join(ROOT, "data", "aurelia_transit_architecture")
NS = "aurelia_transit_architecture"
RED = NS + ":block/ns_rail_red"

steel = Image.open(os.path.join(A, "textures/block/steel.png")).convert("RGBA")
red = Image.new("RGBA", steel.size)
for x in range(steel.width):
    for y in range(steel.height):
        r, g, b, a = steel.getpixel((x, y))
        lum = (r + g + b) / 3 / 255
        k = 0.75 + 0.35 * lum
        red.putpixel((x, y), (min(255, int(190 * k)), int(28 * k), int(36 * k), a))
red.save(os.path.join(A, "textures/block/ns_rail_red.png"))

state = open(os.path.join(A, "blockstates/handrail.json")).read()
models = sorted(set(re.findall(NS + r":block/(handrail_[a-z_]+)", state)))
for m in models:
    model = json.load(open(os.path.join(A, "models/block", m + ".json")))
    for key in ("post", "rail", "particle"):
        if key in model.get("textures", {}):
            model["textures"][key] = RED
    json.dump(model, open(os.path.join(A, "models/block", "ns_" + m + ".json"), "w"), indent=2)
open(os.path.join(A, "blockstates/ns_handrail.json"), "w").write(state.replace(NS + ":block/handrail_", NS + ":block/ns_handrail_"))
item = json.load(open(os.path.join(A, "models/item/handrail.json")))
for key in ("post", "rail", "particle"):
    item["textures"][key] = RED
json.dump(item, open(os.path.join(A, "models/item/ns_handrail.json"), "w"), indent=2)
loot = open(os.path.join(D, "loot_tables/blocks/handrail.json")).read().replace(NS + ":handrail", NS + ":ns_handrail")
open(os.path.join(D, "loot_tables/blocks/ns_handrail.json"), "w").write(loot)

lang_path = os.path.join(A, "lang/en_us.json")
lang = open(lang_path).read()
if "ns_handrail" not in lang:
    anchor = '  "block.aurelia_transit_architecture.ns_lift_post": "NS Lift Tower Post",'
    lang = lang.replace(anchor, anchor + '\n  "block.aurelia_transit_architecture.ns_handrail": "NS Red Handrail",', 1)
    open(lang_path, "w").write(lang)
tag_path = os.path.join(ROOT, "data/minecraft/tags/blocks/mineable/pickaxe.json")
tag = open(tag_path).read()
if "ns_handrail" not in tag:
    anchor = '\t\t"aurelia_transit_architecture:ns_lift_post",'
    tag = tag.replace(anchor, anchor + '\n\t\t"aurelia_transit_architecture:ns_handrail",', 1)
    open(tag_path, "w").write(tag)
print("models", len(models))
