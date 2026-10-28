# AureliaMap (BlueMap fork)

Forked 2026-10-28 from BlueMap `master` @ `afeddb83c4116d3800884745cdb10b9fb29bda0d` plus the BlueMapAPI submodule @ `61847b8` (now a plain `api/` folder). BlueMap and BlueMapAPI are MIT (Blue and contributors): `LICENSE` and `api/LICENSE` stay as they are, and every source file keeps its licence header. Third-party dependency notices are still to be audited (PLAN M6).

## What was cut
- Platform modules `implementations/{fabric,forge,neoforge,paper,spigot,sponge}`, `.github`, the Curseforge/Modrinth/Hangar/Ore publishing plugins and the git-derived version (fixed `0.0-aureliamap`).
- Kept: `core`, `common` (render manager, web server, `webapp`), `api`, `implementations/cli`.

## Build (JDK 25 for this folder only; Loom and ATR stay on 21/17)
```
JAVA_HOME=<jdk25> ./gradlew :cli:shadowJar -x test     # implementations/cli/build/libs/cli-0.0-aureliamap-shadow.jar
```

## Run
```
JAVA_HOME=<jdk25> aurelia/aureliamap.sh render <world-dir>   # nice -n 19, 1 render thread, -Xmx1g, Minecraft 1.20.1 resources
JAVA_HOME=<jdk25> aurelia/aureliamap.sh serve                # http://127.0.0.1:8100 (AURELIAMAP_PORT)
```
Output and config live in `run/` (git-ignored); config templates are in `aurelia/config-template/`. Env: `AURELIAMAP_FORCE=1` re-renders everything, `AURELIAMAP_MODS=<mods dir>` adds mod jars as resource packs (M2), `AURELIAMAP_MC` overrides the pinned version.

The web server is BlueMap's own (`-w`): it serves `*.gz` with `Content-Encoding: gzip` and answers a missing tile with 204. A plain static server does not (the viewer fails with "error trying to load this map").

Minecraft's `client.jar` is downloaded by BlueMap at first run (`accept-download: true`) into `run/data/`. Never commit or redistribute it.

## Modded blocks (M2, 2026-10-28)
`AURELIAMAP_MODS=<mods dir>` loads every `.jar` there as a resource pack (BlueMap's `scan-for-mod-resources`). No parser changes were needed.

Test: an 800 x 800 box around Aurelia Centraal (`AURELIAMAP_BOX=-400,-640,400,160`) in a copy of the Aurelia Metropolis v1 world, rendered with the pack's 57 mod jars plus the ATR jar (MTR's jar left out).

| | Wall time | Peak RSS | Tiles |
|---|---|---|---|
| No mods (before) | 104 s | 574 MB | 27 MB |
| 58 jars (after) | 134 s | 1.09 GB | 67 MB |

Screenshots: `docs/aureliamap-m2/` in the ATR folder (before = black voids where modded blocks are; after = the Centraal train shed, brick building and platforms).

What still does not render:
- MTR rails: they are not blocks, MTR draws them itself. The ATR layer (M3/M4) draws them from MTR's data instead.
- Block entities: PIDS, station name signs, route signs, railway signs, lift doors, Create contraptions. BlueMap only draws block models.
- OBJ/Blockbench vehicles and anything drawn only by a mod renderer.
- 3 models whose JSON has bad face names (`mishanguc:block/wall_light_hash_decoration_connection2` `#UP`, `mtr:block/route_sign_light` `EASST`, `createdeco:block/catwalk_bottom` `EAST:`). Vanilla 1.20.1 rejects these too ("Unknown facing"), so they are broken in game as well; the map matches.
