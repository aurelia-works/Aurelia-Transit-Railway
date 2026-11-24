# Third-party notices

ATR ships code derived from more than one project. This file says which, under what terms, and which parts are
deliberately not included.

## MTR (Minecraft Transit Railway)

ATR is a fork of MTR 4.0.5 and keeps its MIT licence. The original notice stays in `LICENSE-MTR.txt` and in the
source files that came from it, unchanged.

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

Included so far: _(filled in as each import phase lands — see `PLAN-stratos.md`)_.

### Deliberately not included

- The Clash Grotesk font. Its licence (ITF FFL 2.0) forbids redistribution by anyone but the foundry, so the
  permission above cannot cover it.
- Stratos's own `fabric.mod.json` id, its `stratos:` mod id, and its `.accesswidener`.
- Stratos's launcher, pack definitions and publishing tools; those are out of scope for ATR.

## Dependencies looked at but never shipped

- Distant Horizons and Iris are LGPL-3.0. If ATR links against them it is `compileOnly`, never bundled, and the
  library is a separate, unmodified jar at runtime.
- FerriteCore is MIT.
