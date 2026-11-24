# Third-party notices

ATR ships code and assets from more than one project. This file says which, under what terms, and which parts are
deliberately not included.

In short: ATR's own code and everything from MTR is MIT. The road building derived from Stratos is not: it is All
Rights Reserved and included with its owner's permission. Fonts keep their own licences.

## MTR (Minecraft Transit Railway)

ATR is a fork of MTR 4.0.5 by Jonathan Ho and keeps its MIT licence. ATR also ships MTR's simulation engine,
Transport-Simulation-Core (MIT), built from the fork `aurelia-works/Transport-Simulation-Core`. Both original notices
are in `LICENSE-MTR.txt` and in the source files that came from them, unchanged.

ATR's own changes are MIT too, under `LICENSE`.

Some vehicle and model features come from Nemo's Transit Expansion (NTE), MIT licensed, as they were ported into
MTR 4.0. Its assets keep the `mtrsteamloco` namespace.

## Stratos (IndusGaming)

ATR's road building is derived from **Stratos** (`Bcollins004/indus-stratos`), Copyright (c) 2026 IndusGaming,
**All Rights Reserved**.

Those parts are included with the copyright holder's permission, recorded by Jah on 2026-11-24. The permission is
limited to use inside ATR. It does not relicense the derived files, and ATR's MIT grant does not extend to them: you
may not copy, modify or redistribute them outside ATR without IndusGaming's written permission.

Every file that is Stratos's own work carries this header:

```
Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
Used with permission — see NOTICE.md.
```

Files that are a port of MTR's MIT-licensed code do not: they keep MTR's notice, say so in their header, and MIT
governs them.

What is included:
- **Road geometry** (PR #147): `org.mtr.aurelia.road.geom` and `.geom.mtr`, copied from Stratos with the package
  renamed. In `geom.mtr`, `Angle`, `Position`, `Rail`, `RailMath`, `Utilities` and `Vector` are a literal port of
  Transport-Simulation-Core's MIT code: they keep its notice and MIT governs them. `MtrRails` sits in the same package
  but is Stratos's own code, not a port, so it is under the permission above, like the rest of `geom`.
- **Road engine** (PR #162): `org.mtr.aurelia.road` and its sub-packages `bake`, `forest`, `journal`, `json`, `ops`,
  `preview`, `proto`, `road`, `schem`, `spi`, `terrain`, `tool`, `undo` and `world`, plus the road palettes and
  `core.properties` under `fabric/src/main/resources/org/mtr/aurelia/road/`. Copied from Stratos 0.1.0's core with the
  package renamed.
- **Server side** (PR #166): `org.mtr.aurelia.road.fabric` and its sub-packages `apply`, `net` and `palette`.
  Rewritten from Stratos 0.1.0's Fabric layer for ATR's Yarn names. Permissions are op levels only, with no
  permissions library.
- **Road blocks** (PR #174, merged through #176): `org.mtr.aurelia.road.fabric.block` and `.fabric.paint`, with their models, textures,
  item models, names and loot tables under `assets/stratos/` and `data/stratos/`. Rewritten for ATR's Yarn names. The
  block ids keep Stratos's `stratos:` namespace.
- **Road tools, preview and HUD** (PR #176): `org.mtr.aurelia.road.fabric.client` and its sub-packages `compat`,
  `editor`, `nodes`, `preview`, `tool` and `ui`, plus `org.mtr.aurelia.road.fabric.bake`, and the GUI textures and
  names under `assets/stratos/`. Rewritten for ATR's names and drawn with ATR's UI.
- **Road hub** (PR #179): `org.mtr.aurelia.road.fabric.client.hub`, with its small-screen layout (PR #192).
- **Hidden track under roads** (PR #180, merged through #190): `org.mtr.aurelia.road.fabric.track`, plus
  `org.mtr.atr.mixin.UnloadedChunkLightMixin`, which keeps far train boxes lit in unloaded chunks.
- **Traffic side and road graph** (PRs #188 and #189): `org.mtr.aurelia.road.road.graph` and the changes to the road
  engine that let roads drive on either side.
- **Junctions** (PR #191): `org.mtr.aurelia.road.road.junction`.
- **Road details and Distant Horizons** (PR #193): `org.mtr.aurelia.road.fabric.compat.dh`. Distant Horizons stays
  optional: ATR reaches it only by reflection when it is installed, and never ships or links it.
- **Driving-side switch** (PR #195): the operator override that converts every player's roads, in
  `org.mtr.aurelia.road.road` and `.road.junction`.
- **Stratos's own tests** (PRs #147, #162, #189 and #197): the tests under `fabric/src/test/java/org/mtr/aurelia/road/`
  and `fabric/src/ataTest/java/org/mtr/aurelia/road/`, ported from Stratos and adapted to ATR.

All of the above, apart from the `geom.mtr` port files named in the first item and their tests, is Stratos's own work under the
permission above.

### Deliberately not included

- The Clash Grotesk font. Its licence (ITF FFL 2.0) forbids redistribution by anyone but the foundry, so the
  permission above cannot cover it.
- Stratos's own `fabric.mod.json`, its `stratos` mod id and its `.accesswidener`.
- Stratos's launcher, pack definitions and publishing tools. Those are out of scope for ATR.

## Fonts

| Font | Files | Licence |
|---|---|---|
| Roboto 2.137, Copyright 2011 Google Inc. | `assets/aurelia_transit_architecture/font/roboto_medium.ttf`, `roboto_bold.ttf` | Apache-2.0 (`ROBOTO_LICENSE.txt` beside them) |
| Barlow SemiBold, Copyright 2017 The Barlow Project Authors | `assets/aurelia_transit_architecture/font/barlow_semibold.ttf` | SIL OFL 1.1 (`barlow_semibold.LICENSE.txt`) |
| Hind SemiBold, Copyright (c) 2014 Indian Type Foundry | `assets/aurelia_transit_architecture/font/hind_semibold.ttf` | SIL OFL 1.1 (`hind_semibold.LICENSE.txt`) |
| Noto Sans and Noto Serif CJK TC (from MTR) | `assets/mtr/font/` | SIL OFL 1.1 |

## AureliaMap

AureliaMap, ATR's web map, is a fork of BlueMap and BlueMapAPI by Blue (Lukas Rieger) and contributors, MIT licensed.
Its own notices and bundled libraries are listed in `aureliamap/LICENSE` and `aureliamap/THIRD_PARTY_NOTICES.md`.

## Announcer voices (downloaded, not shipped)

The Sound tab's announcer voices are downloaded on the player's request into `config/aurelia/voice/`. None of these is
in ATR's jar:
- sherpa-onnx (the speech engine) and the Kokoro voice: Apache-2.0.
- Piper voices Ronnie, Thorsten and Davefx: CC0.
- The SIWIS voice: CC BY 4.0, credited in the voice button's tooltip.
- espeak-ng, which both engines use: GPL-3.0. It arrives inside the download as a separate program.

## Dependencies looked at but never shipped

- Distant Horizons and Iris are LGPL-3.0. If ATR links against them it is `compileOnly`, never bundled, and the
  library is a separate, unmodified jar at runtime.
- FerriteCore is MIT.
