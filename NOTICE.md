# Third-party notices

ATR ships code derived from more than one project. This file says which, under what terms, and which parts are
deliberately not included.

## MTR (Minecraft Transit Railway)

ATR is a fork of MTR 4.0.5 and keeps its MIT licence. The original notice stays in `LICENSE` (next to a line for
ATR's changes) and in the source files that came from it, unchanged.

## Stratos (IndusGaming)

Parts of ATR's road building are derived from **Stratos** (`Bcollins004/indus-stratos`), Copyright (c) 2026
IndusGaming, **All Rights Reserved**.

Those parts are included with the copyright holder's permission, recorded by Jah on 2026-11-24. The permission is
limited to use inside ATR; it does not relicense the derived files, and ATR's own MIT grant does not extend to them.

Every file that is Stratos's own work carries this header:

```
Derived from Stratos, (c) 2026 IndusGaming. All Rights Reserved.
Used with permission — see NOTICE.md.
```

Files that are a port of MTR's MIT-licensed code do not: they keep MTR's notice, say so in their header, and MIT
governs them.

Included so far:
- `org.mtr.aurelia.road.geom` and `.geom.mtr` (PR #147): the road geometry, copied from Stratos with the package
  renamed. The `geom.mtr` files are a port of MTR's own MIT-licensed `RailMath` and friends, so those files stay MIT
  and carry MTR's notice; only the rest of the package is Stratos's work under the permission above.
- The rest of Stratos's engine (P3): `org.mtr.aurelia.road` and its sub-packages `bake`, `forest`, `journal`, `json`,
  `ops`, `preview`, `proto`, `road`, `schem`, `spi`, `terrain`, `tool`, `undo` and `world`, plus the road palettes and
  `core.properties` under `fabric/src/main/resources/org/mtr/aurelia/road/`. Copied from Stratos 0.1.0's core with
  the package renamed; all of it is Stratos's own work under the permission above.
- Stratos's server side (P4): `org.mtr.aurelia.road.fabric` and its sub-packages `apply`, `net` and `palette`.
  Rewritten from Stratos 0.1.0's Fabric layer for ATR's Yarn names; permissions are op levels only, with no
  permissions library. Stratos's own work under the permission above.
- Stratos's road blocks (P5): `org.mtr.aurelia.road.fabric.block`, with their models, textures, item models, names
  and loot tables under `assets/stratos/` and `data/stratos/`. Rewritten for ATR's Yarn names; the block ids keep
  Stratos's `stratos:` namespace. Stratos's own work under the permission above.

### Deliberately not included

- The Clash Grotesk font. Its licence (ITF FFL 2.0) forbids redistribution by anyone but the foundry, so the
  permission above cannot cover it.
- Stratos's own `fabric.mod.json` id, its `stratos:` mod id, and its `.accesswidener`.
- Stratos's launcher, pack definitions and publishing tools; those are out of scope for ATR.

## Dependencies looked at but never shipped

- Distant Horizons and Iris are LGPL-3.0. If ATR links against them it is `compileOnly`, never bundled, and the
  library is a separate, unmodified jar at runtime.
- FerriteCore is MIT.
