# ATR 1.1.0 public beta: notes for testers

Thanks for trying Aurelia Transit Railway before it goes public. This is a beta: things will break, and that is what we want to hear about.

## Install
1. Minecraft **1.20.1** with **Fabric Loader** 0.20.1 or newer (the version this build was tested with) and **Fabric API** for 1.20.1.
2. **Back up your world first.** Better still, test in a copy of it.
3. Take any `mtr` jar (Minecraft Transit Railway or an older ATR) out of `mods`. Do not run both.
4. Put `ATR-fabric-4.0.5+atr.1.1.0-mc1.20.1.jar` in `mods`.

MTR add-ons (Joban Client Mod, MAGIC, Tianjin, London Underground) should keep working. Tell us if one does not.

## What to try
Take the **ATR Wrench** from the creative tab. Press **V**, or sneak and scroll, to change its mode. Press **M** for the ATR menu. The Aurelia panel has no key by default: set one under Controls, in the Aurelia Transit Railway section.
- **Station**: plan a station in front of you, look at it in Station Studio, build it, then undo it. Try a big one (up to 32 tracks) and a small one.
- **Airport**: plan an airport, and walk from a gate through a jet bridge onto a parked plane.
- **Bridge**: click a rail's two nodes and build a viaduct, a truss, an arch viaduct, a suspension span or a tunnel portal.
- **Earthworks**: level a hill or cut a slope along a rail.
- **Undo after a restart**: build something, quit and reload the world, then undo it.
- **Trains**: run a route with passengers on the platforms, ride it, and open the dashboard.
- **Old worlds**: load an MTR 4.0.5 world and check its trains, stations and signs.

## Not tested yet
- Drawing tracks and buildings by hand in the editor screen has never been tried in game.
- Two players on one server have not been tested together.

## How to report
Open an issue with the bug form: https://github.com/aurelia-works/Aurelia-Transit-Railway/issues/new?template=bug_report.yaml

Or send it to Jah directly. Either way, include:
- what you did and what you expected;
- a screenshot or short clip;
- `logs/latest.log`, and the crash report if the game crashed;
- your other mods and your shader pack, if any.

## Notes
- The jar includes real-world station names, operator looks and logos (for example NS, DB and the Underground roundel). They are there for building and are not endorsed by any operator.
- Based on Minecraft Transit Railway by jonafanho, MIT licensed.
