# ATR 1.2.0 public beta: notes for testers

Thanks for trying Aurelia Transit Railway before it goes public. This is a beta: things will break, and that is what we want to hear about.

**English only for now.** The mod's text and its announcer voices are in English.

## Install
1. Minecraft **1.20.1** with **Fabric Loader** 0.20.1 or newer (the version this build was tested with) and **Fabric API** for 1.20.1.
2. **Back up your world first.** Better still, test in a copy of it.
3. Take any `mtr` jar (Minecraft Transit Railway or an older ATR) out of `mods`. Do not run both.
4. Put `ATR-fabric-4.0.5+atr.1.2.0-mc1.20.1.jar` in `mods`.

MAGIC, MTR Rail Works and MTR France Addon were checked against this build but not launched with it. Tell us if any MTR add-on does not work.

## What to try
Take the **ATR Wrench** from the creative tab. Press **V**, or sneak and scroll, to change its mode. The Aurelia panel has every switch; set its key under Controls, in the Aurelia Transit Railway section.
- **Station**: open Station Studio from the wrench, plan a station in your browser, press Build it, then undo it. Try a big one (up to 32 tracks), a small one, and one on a hillside.
- **Airport**: plan an airport, and walk from a gate through a jet bridge onto a parked plane.
- **Bridge**: click a rail's two nodes and build a viaduct, a truss, an arch viaduct, a suspension span or a tunnel portal.
- **Earthworks**: level a hill or cut a slope along a rail.
- **Roads**: open the road hub with **B** and build a street, a bus lane road and a highway, with a junction.
- **Undo after a restart**: build something, quit and reload the world, then undo it.
- **Trains**: run a route with passengers on the platforms, ride it, and open the dashboard and the map (**N**).
- **Announcer**: download a voice in the panel's Sound section and listen to a station call.
- **Old worlds**: load an MTR 4.0.5 world and check its trains, stations and signs.

## Not tested yet
- Two players on one server have not been tested together.

## How to report
Open an issue with the bug form: https://github.com/aurelia-works/Aurelia-Transit-Railway/issues/new?template=bug_report.yaml

Or send it to Jah directly. Either way, include:
- what you did and what you expected;
- a screenshot or short clip;
- `logs/latest.log`, and the crash report if the game crashed;
- your other mods and your shader pack, if any.

## Notes
- Station designs copy real stations so you can build them. Operator signs use generic marks, not real logos. Nothing here is endorsed by any operator.
- Based on Minecraft Transit Railway by jonafanho, MIT licensed.
