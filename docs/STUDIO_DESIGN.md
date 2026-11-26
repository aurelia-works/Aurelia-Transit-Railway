# Station Studio: design system and architecture notes

The browser UI served by `StudioServer` (`fabric/src/main/resources/studio/`). Structured as a precision architectural control desk: a navigation rail, a parametric & seed rail, a full-bleed 3D WebGL stage with a two-tier camera/environment bar, and an inspector column ending in a single-CTA build dock.

## Product intent & taste baseline

- **Who & what:** Minecraft transit builders and modders designing, validating, and placing multi-platform railway stations (`/`) and ICAO-proportioned airports (`/airport`) in-game or offline (`.schem` / command export).
- **Character:** An architectural CAD instrument and railway signalling control room — quiet, dense, exact, high-trust. One moving thing at a time. Dressed in a Minecraft finish so it reads as part of the game (see below).
- **Earned ornament:** The mechanical split-flap departure board (`#flaps`) flipping on **R** / dice roll, LED-dot viewport toggles, and a subtle steel-blue background shader (`silk.js`) behind opaque dark-slate chrome. Zero glassmorphic blur over scrolling text, zero competing neon CTAs, zero decorative bounce.

## Concept card

```
Idea:        a station generator you steer with a seed, the way a departure board steers a station
Metaphor:    the split-flap departure board + railway signalling desk
Signature:   the dice: the seed's six letters flip like a board's flaps while the new station builds in the view
Emotion:     a control room: quiet, dense, exact; one moving thing at a time
Motion verb: flip like a board (seed), settle (everything else: 120-180 ms, no bounce)
Screenshot:  a seed mid-flip beside a freshly rolled terminus, checks and layers visible without scrolling
```

## Seeds & locks

One seed, two jobs:

- **Settings seed** (the page). Situated at the very top of `#settings` on both `/` and `/airport` (*revised from buried `#more` fold after auditing `/` and `/airport` at `1440×900`: the signature departure board was hidden below 900 px of folds and absent on `/airport`*). The dice picks every setting that is not locked: Look (city, mix), Shape (type, tracks, length, platforms), Building (building and size, roof, stairs, stairwells, travelators, track bed). Typing a seed rolls the same settings again. Changing a control afterwards marks the seed as edited (`#seedNote`). When a roll sets hidden class, era, or mix overrides, `#rolledMeta` surfaces them immediately on the seed header and jumps straight to the matching advanced section on click.
- **Generator seed** (the planner). Sent as `seed k7f2ra`, so the wrench, `/ata_station plan`, and Studio all read the same token. It varies only what the words left open: canopy profile, single vs multi-arch barrel sheds, platform floor, roof material, starting platform colour, and first bench offset. No seed, or seed `0`, plans deterministically as before.

Press **R** (or `#roll`) to roll. **R** is suppressed while typing in inputs/textareas or walking in first-person mode.

### Locks

Three locks (`[data-lock]`), partitioning every visible attribute of a planned station (`tools/studio_locks.mjs`):

| Lock | Keeps |
|---|---|
| Look | city, mix, hall and platform style, era, and the generator seed (canopy profile, shed, floors, roof deck, colours) |
| Shape | station type, tracks, platform length, platform layout |
| Building | a building or none, its size and roof form, class, stairs, stairwells, travelators, track bed |

Locking **Building** resolves **Auto** roof or size to the plan's actual `resolved` values so re-rolling **Look** cannot mutate the building's form. Locking **Building** when `building == 0` prevents rolling a terminus or hub (which always carry a building). Locks persist in `localStorage`.

## Tokens & typography (revised from browser audit)

Every token below was tested against `/` and `/airport` across `1440×900`, `1200×900`, `768×1024`, `390×844`, and `200%` root text zoom:

| Role | Token | Value | Audit trigger & rationale |
|---|---|---|---|
| Ground | `--bg` | `#07090d` | Deep signalling-room slate; paired with ATA midnight-steel `silk.js` (`#05070B`..`#1C3D72`), replacing leftover Indus purple (`#3D2C8D`/`#916BBF`) seen around panel seams on `/`. |
| Chrome | `--chrome` | `#0a0d13` | Made 100% opaque (*was `rgba(10,11,16,.84)`, which bled scrolling rail controls through the sticky `.top` header on `/` at `390×844`*). |
| Panel / raised | `--panel` / `--panel-2` / `--panel-3` | `#0f131b` / `#151a24` / `#1c2330` | Cool neutral slate steps with crisp 1 px hairline separation (`--line: rgba(255,255,255,.085)`). |
| Ink / muted / faint | `--fg` / `--muted` / `--faint` | `#f4f5f7` / `#a3aab8` / `#7d8596` | Raised `--faint` from low-contrast zinc so unit counters, axis ticks, and hints pass WCAG AA on `--panel` (`/` `#words` char count & `#more` hints). |
| Accent (selection) | `--accent` / `--accent-text` | `#2b66f6` / `#7aa7ff` | Calibrated ATA signalling blue (`#2b66f6` achieves `4.85:1` AA contrast with `#ffffff` ink on active segment/view pills; `--accent-text` achieves `7.4:1` on dark cards). |
| Go (primary build CTA) | `--go` / `--go-hi` / `--go-edge` / `--go-ink` | `#3c8527` / `#347522` (hover) / `#1d4d13` / `#ffffff` | Reserved for the `Build it` button (`[data-send="build"]`). Minecraft's own menu green with a dark bottom edge, so the one button that changes the world looks like the game's. White ink is about `4.6:1` at rest and darker on hover, so it stays AA. |
| Live | `--live` | `#22c55e` | The in-game status dot in the sidebar, kept bright so a live connection still reads at a glance. |
| Corners | `--r-panel` / `--r` / `--r-sm` | `6px` / `4px` / `3px` | Blocky, like the game's menus (*were `12px` / `8px` / `6px`, which read as a generic web app*). Overlay pills over the 3D view stay round. |
| Bevel | `--bevel` / `--bevel-down` | inset dark bottom edge + faint top highlight | Every enabled button, preset chip, lock, layer toggle and city card sits on a 2 px dark bottom edge and drops 1 px when pressed, the way Minecraft's buttons do. |

### Minecraft finish

Added so the Studio feels like part of Minecraft rather than a generic dark web app, without giving up the control-room calm:

- **Blocky corners** (tokens above) on panels, buttons, chips and the seed's split-flap cells.
- **Bevelled buttons** (`--bevel`, on buttons, preset chips, locks, layer toggles and city cards; disabled buttons stay flat): a dark bottom edge and a faint top highlight; pressing moves the button down 1 px and thins the edge. `Build it` gets a thicker 4 px edge in Minecraft green.
- **Square slider thumbs and toggles**, like the game's own sliders.
- **Block bullets:** each section heading (`.label`) starts with a small ATA-blue square.
- **Plain wording:** `Keep when you roll` (was `Lock on roll`), `Quick start` (was `Quick start & prompt`), `How your words were read` (was `What the planner read`), `Raw plan data` (was `Blueprint data`).

No Mojang art, fonts or textures are copied: the finish is CSS only.

### `rem` type scale & 200% text zoom

- **Faces:** Clash Grotesk (600/500 UI, 600 + 200 italic wordmark) with system sans fallback; `ui-monospace` (`SFMono-Regular`, `JetBrains Mono`, `Menlo`) with `tabular-nums` for seeds, dimensions, timings, and block counts.
- **Scale (`rem`, never `px`):** `--t-2xs` (`0.6875rem`), `--t-xs` (`0.75rem`), `--t-sm` (`0.8125rem`), `--t-md` (`0.875rem`), `--t-base` (`0.9375rem`), `--t-lg` (`1rem`).
  - *Audit trigger:* Hardcoded `px` font sizes and `px` letter-spacing on `#seed` / `.flap` froze text at `14 px` and misaligned the input caret from the six split-flap cells when `document.documentElement.style.fontSize = '200%'` on `/` (`1440×900`). Scaling `#seed` (`letter-spacing: calc(1.75rem - 1ch)`) and `.flap` (`1.625rem`) in `rem` plus `syncZoomLayout()` (`html[data-zoom-stack]` when `rootPx > 20` and `innerWidth / rootPx < 64`) reflows 200% zoom cleanly with zero horizontal scroll (`scrollWidth <= innerWidth`).
- **Touch targets:** At `@media (pointer: coarse), (max-width: 56rem)`, every interactive button, segment, summary, input, lock, layer chip, and 3D platform badge (`44×44 px` hit box via `::after`) meets the `2.75rem` (`44 px`) minimum.

## Layout & responsive tiers

### Desktop (`> 80rem` / `1440×900`)

```
| .side (13.5rem)  | #settings (21.5rem)      | .stage-col (1fr)                  | #report (21.5rem)       |
| wordmark         | top bar: title · version · seed & station summary                                      |
| live/design card | SEED + flaps + locks     | [compass]            [orbit hint] | BUILD (2x2 facts)       |
| Station Gen      | QUICK START              |                                   | CHECKS (warns + fold)   |
| Airport Gen      |   Presets (32)           |           3D WebGL canvas         | LAYERS & CUTAWAY        |
|                  |   Describe in words      |                                   | MATERIALS (fold)        |
|                  |                          | [Planned in N ms · blocks]        | INSPECT (quiet folds)   |
|                  | STATION (4-col cities)   | [Views | Jumps ............. Fit] | ----------------------- |
| world link       | SHAPE & BUILDING         | [Lighting | Shaders Tex Num | M ] | [Plan in front][Build]  |
|                  | ADVANCED (#more)         |                                   | [Download .schem]       |
```

Key structural revisions from the `1440×900` audit:

1. **Rail hierarchy (`/` & `/airport`):** Promoted `.seed-group` to the top of `#settings`, restored the 32 built-in station presets (`#presets` inside `#presetsFold`, previously dead `display: none` despite `buildPresets()` running in `studio.js`), and grouped `#wordsAdvanced` (`Describe in words`) right beside it. City cards render in a 4-column swatch grid with zero name truncation (`Amsterdam`, `St Pancras`, `Hong Kong`, `Kuala Lumpur`, `Teddington` all read in full).
2. **Two-tier `.viewbar` (`/` & `/airport`):** Replaced the ragged 3–4 line flex wrap (`136–195 px` tall on `/airport`) with two predictable rows (`99 px` total, reclaiming up to `96 px` of vertical 3D canvas):
   - **Row 1 (`.vb-row.cam`):** Camera mode (`Overview`, `Passenger`, `Plan`, `Walk`), quick jumps (`Street`/`Head`, `Back`, plus `Terminal`, `Gates`, `Runway` on `/airport`), and `Fit` (`#reset`) anchored right.
   - **Row 2 (`.vb-row.env`):** Lighting (`Studio`, `Day`, `Sunset`, `Night`) on the left; LED-dot pill switches (`Shaders`, `Textures`, `Numbers`) and the `Mouse options` popover (`Escape` closes and restores focus to `summary`) on the right.
3. **Progressive-disclosure `#checks` & single-CTA `.dock` (`/` & `/airport`):**
   - `#checks` surfaces the verdict banner plus any `WARN`/`FAIL` items and their `Next:` fixes immediately, while tucking passed checks into `<details class="fold checks-fold">` (`Show N passed checks`). This brings `BUILD`, `CHECKS`, `LAYERS & CUTAWAY`, and `MATERIALS` all above the fold at `1440×900` (*previously 16 green rows pushed Layers and Cutaway completely off-screen*).
   - `.dock` pairs tonal `Plan in front` beside the single green primary `Build it` CTA, demotes `Download .schem` to a quiet secondary utility below, and hides `#filesRow` on `/airport` and whenever `/api/stations` returns `409` (no world station folder attached), cutting dock height from `265 px` to `144 px`.

### Laptop (`64rem–80rem` / `1200×900`)

- `.side` collapses to a `4.25rem` icon rail with distinct inline train (`#navStation`) and aircraft (`#navAirport`) vector icons while keeping full `.nav-copy` text accessible to screen readers (*fixing the old `first-letter` CSS hack that rendered two identical `"S"` / `"A"` badges and concatenated `"SStation Generator"` in the accessibility tree*).
- `.seed-row` stays `flex-wrap: nowrap` so `#seedBox`, `#roll`, and `#copySeed` remain on one horizontal row.

### Tablet (`44rem–64rem` / `768×1024`)

- `.side` becomes a compact top bar: `[mark] [Station Generator | Airport Generator]` on the left and `#profile` (`In-game Studio · CONNECTED` / `Design mode · OFFLINE`) on the right (*fixing the unreachable `/airport` navigation bug where `.nav-group` was `display: none` below `1180 px`*).
- `.stage-col` spans full width on top (`42vh`), with `#settings` and `#report` side by side in a balanced two-column desk below (`grid-template-columns: minmax(0, 1.04fr) minmax(0, 0.96fr)`), eliminating the cramped 28vh strip under the stage seen on `/` at `768×1024`.

### Phone (`<= 44rem` / `390×844`)

- Two-row header: `[mark] Station/Airport Generator` + connection badge on row 1, full-width 50/50 `[Station Generator | Airport Generator]` mode switcher (`44 px` tall) on row 2.
- Panoramic 3D stage (`44vh`) on top, full-width 4-column aligned `.viewbar` rows (`44 px` targets), single-column `#settings` and `#report`, and a compact sticky bottom `.dock` (`101 px` tall vs `265 px` before).

## Views, compass & word precedence

- **Fit (`#reset`)** projects all eight corners of the planned bounding box and frames them to 90% of the viewport for any aspect ratio.
- **Plan** renders an orthographic-like top-down architectural plan with street at the bottom (or north-up when an explicit `facing` is chosen).
- **Compass (`#compass`)** rotates live with camera azimuth to point toward the street entrance (plus world `N` and `#compassFacing` when `facing` is set); clicking it switches to **Plan** view.
- **Typed words vs controls:** Typed words in `#text` overrule conflicting rolled or pinned controls (`StudioApi.wordsWon`), surfaced live in `#wordsNote` inside `#wordsAdvanced`.

## Airport Generator (`/airport`)

Served from the same `index.html`, `studio.css`, and `studio.js` with `body[data-page="airport"]` toggling `.st-only` and `.ap-only`:

- **Controls:** Shared deterministic `#seedBox` + `#roll` + `#copySeed` at top of `#settings`, six starter layout chips (`#airports`), and the `#text` specification box (`ata-airport-studio` storage key).
- **Terminal entrance canopy (`TerminalBuilding` + `PlanAirport`):** One architectural concrete entrance canopy framed flush into the terminal's landside spandrel beam, carried by steel columns on the outer footway behind the kerb (clear of the carriageway and zebra crossing), linked by a steel header girder, cross-beams, and angled steel cantilever braces under the drop-off overhang (*fixing the duplicate floating forecourt canopy frame and road-blocking columns seen on `/airport` Terminal view*).

## Automated verification

- Java JUnit suite: `./gradlew setupFiles :fabric:ataTest --offline --max-workers=2 --tests 'com.aureliatransit.architecture.station.studio.*'` (`StudioTest`, `StudioKeysTest`, `StudioLocksTest`, `StudioWordsTest`, `AirportStudioTest`, `ConnectionsTest`, `SchematicExportTest`, `BlockModelsTest`).
- Playwright browser suites: `tools/studio_features.mjs`, `tools/studio_locks.mjs`, `tools/studio_stress.mjs`, `tools/studio_swaps.mjs`.
