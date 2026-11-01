# Aurelia Transit Railway (ATR): phase plan

Status: **approved by Jah 2026-10-28 ("That's good")**. Nothing is built yet. Phase 0 begins by forking into aurelia-works (public): confirm with Jah before the fork.

## What ATR is (from the interview, 2026-10-28)
- A fork of Minecraft Transit Railway 4.x for **Minecraft 1.20.1, Fabric only**. MTR left 1.20.1 (4.1 is 1.21-only), and ATR keeps it maintained there.
- **Drop-in replacement**: the mod id stays `mtr`, so JCM, MAGIC, Tianjin, the London Underground addon and train packs keep working. Worlds saved with MTR open unchanged. MTR's look and controls stay the same in 1.0.
- **ATU and ATA get folded in.** ATU's hub becomes ATR's dev panel: every visible feature gets toggles, sliders and colours there.
- Goal: recognition and downloads. Published on Modrinth, CurseForge and GitHub Releases.
- License: MTR and Transport-Simulation-Core are MIT. Keep their copyright and license notice and credit "based on MTR by jonafanho". Telling upstream is optional (Jah's call).
- Repos: forks of both repos under the **aurelia-works** org.
- Pace: one phase per session, then stop, hand off and wait for Jah.

## Facts found while planning
- The MTR repo has **no tags or GitHub releases**. Branches: `master`, `stonecutter`, `4.0.6` (head 240524a1, 2026-07-16 "Fix vehicle snapping"). Core has no tags either. Phase 0 has to pin the base commit.
- The ATU 1.4.0 perf fixes are mixins against MTR 4.0.5. In ATR they become direct code changes.
- Modpack (Aurelia Metropolis): the MTR add-ons (JCM, MAGIC, Tianjin, LU) all top out at 1.20.4, and Create Fabric is 1.20.1 only. That backs staying on 1.20.1.

## Phases
Estimates are rough: tokens = files read + edits + build output, based on ATU sessions of a similar size. Agent time is my own working time, not human hours.

| # | Phase | Est. tokens | Est. agent time | Actual | Model | Status |
|---|---|---|---|---|---|---|
| 0 | Fork and build unchanged | 150-300k | 1-2 h | ~25 min | Sonnet 5.5, medium | done, awaiting Jah |
| 1 | Rebrand, drop-in check | 150-250k | 1-2 h | | Sonnet 5.5, medium | done 2026-10-28: Metropolis pack booted with ATR (master 69c76644) in place of MTR (copy of the Prism instance); ATA, JCM, MAGIC, Tianjin, LU loaded, world opened, trains running (docs/dropin-2026-10-28) |
| 1a | Core fork swap (build core/, use our jar) | 100-200k | 1-2 h | | Sonnet 5.5, medium | dropped (Jah) |
| 1b | Rain inside train cars | 100-250k | 1-2 h | not measured | Sonnet 5.5, high | merged 2026-10-28 as #5 (`29abcf17`); Jah's check-in: looks good |
| 1c | MTR 4.1 web map research (read only) | 60-120k | 30-60 min | not measured | Haiku/Sonnet | done (`docs/mtr-4.1-web-map.md`) |
| 1d.1 | Web map: build pipeline + map canvas fixes | 250-450k | 1-1.5 h | ~60 min | Sonnet 5.5, high | merged: core #1 (69f4c8d, `core/website`) + mod #3 (96c1c481, override servlet via `ATR_WEBMAP_DIR`); :8888 serves it |
| 1d.2 | Web map: station panel + menu | 150-300k | 1-1.5 h | ~20 min | Sonnet 5.5, high | merged (core #1 69f4c8d) |
| 1d.3 M0 | AureliaMap: BlueMap check (read, build, run) | 100-200k | 30-45 min | tokens not measured; about 35 min agent time incl. waits for Jah to run 2 commands (JDK 25 build, render); go | Sonnet 5.5, medium | done 2026-10-28, note in docs/aureliamap-bluemap-check.md |
| 1d.3 M1 | AureliaMap: BlueMap fork in the ATR repo, low-priority renderer | 250-450k | 1-1.5 h | | Sonnet 5.5, high | done 2026-10-28 (about 40 min agent time) |
| 1d.3 M2 | AureliaMap: modded blocks from mod jars | 200-400k | 1-2 h | | Sonnet 5.5, high | done 2026-10-28 (unattended Opus run), feat/aureliamap 74c323f7 |
| 1d.3 M3 | AureliaMap: MTR data for the map (stations, lines, live trains) | 150-300k | 45-75 min | | Sonnet 5.5, high | done 2026-10-28, feat/aureliamap 7bebe265 |
| 1d.3 M4 | AureliaMap: ATR layer in the 3D viewer | 300-500k | 1.5-2 h | | Sonnet 5.5, high | done 2026-10-28, feat/aureliamap f3a14240 |
| 1d.3 M5 | AureliaMap: settings, flat view, phone, black theme | 150-300k | 1 h | | Sonnet 5.5, medium | done 2026-10-28, feat/aureliamap f9f8cfa9 |
| 1d.3 M6 | AureliaMap: packaging, CI, licence notices, numbers | 150-250k | 45-60 min | | Sonnet 5.5, medium | merged 2026-10-28 as #4 (`69c76644`) with review fixes 6125432c |
| 1d.4 | In-game map (Xaero replacement) | not estimated | not estimated | | | own plan after M6 |
| A | Aurelia core: design only (no code) | 80-150k | 1-2 h | | Opus 5.5, high | design written 2026-10-28 (`docs/phase-A-core-design.md`): core inside ATR, ATR provides/breaks ATA and ATU, one panel; Jah took all 5 recommendations (2026-10-28). Next: 2b, 3, 4, 1d.4 |
| 2a | Fold in ATU perf fixes | 400-700k | 3-5 h | not measured | Opus 5.5, high | merged 2026-10-28 as #6 (`1449c7fe`): draws = ATU on all 14 bench steps, timing vs ATU in docs/perfbench-2026-10-28.md; config screen scrolls to the last row (checked in the drop-in copy with the #6 jar); add-on hook regression from 2a fixed in PR #7 (`DropInSignaturesTest`) |
| 2b | Fold in ATU tools and panel | 300-500k | 2-4 h | about 1.5 h | Opus 5.5 | done 2026-10-28: de0f0833 on feat/2b-tools-panel (not pushed); Jah: in-game check full pass in Metropolis |
| 3 | Fold in ATA blocks | 300-500k | 2-4 h | | Sonnet 5.5, medium | merged 2026-10-29 as #10 (`d33f904b`); Station Studio and wrenches from the panel not tested in game |
| 4 | 1.0 bug fixes | 300-600k | 3-5 h | | Opus 5.5, high | shortlist merged as #34 (`07efa5a84`); results in docs/phase-4-bugs.md; item 10 (lift rows) was #15 |
| 5 | Release 1.0 | 100-200k | 1-2 h | | Sonnet 5.5, medium | v1.0.0 tagged 2026-10-29 on `ec8bdd0ff`; v1.0.2 merged as #37 (`0ee679f`), tag v1.0.2 with the jar; #38 and #39 landed after the tag so they are not in that jar; Modrinth/CurseForge waiting for Jah's test |
| 6 | Depot controls (1.1) | 300-500k | 2-4 h | | Sonnet 5.5, high | merged as #35 (`0193a14f5`) |
| 7 | Schedules and cancellations (1.2) | 400-700k | 3-5 h | | Opus 5.5, high | merged as #38 (`992ba49`); weekday/weekend timetables and cancelled runs; not in the v1.0.2 jar |
| 8 | Sound (1.3) | 300-500k | 2-4 h | | Sonnet 5.5, high | merged as #36 (`9b8435c0d`) |
| 9a | Passengers: simulation in core | 400-700k | 3-5 h | | Opus 5.5, high | merged as #39 (`cd25a9e`); not in the v1.0.2 jar |
| 9b | Passengers: drawing and boarding | 400-700k | 3-5 h | | Opus 5.5, high | merged as #40 (`129e53d`); drawing of waiting and riding passengers |
| P1 | Moving pantographs (looks only) | 250-450k | 2-3 h | | Sonnet 5.5, high | merged 2026-10-29 as #16, 16 unit tests pass, not seen in game; `HANDOFF-2026-10-29-PANTOGRAPH.md` |

Total, roughly: 3.5-6.5M tokens and 25-45 h of agent time across about 12 sessions. Phases 0-5 (through the 1.0 release) are about 10-20 h.

### 0. Fork and build unchanged
Goal: our own copies of both repos building the same jar MTR ships.
- Fork MTR and Transport-Simulation-Core into aurelia-works. Clone them into `mod/` and `core/` in this folder.
- Pin the base: find the commit matching the Modrinth 4.0.5 jar (compare the class list and `fabric.mod.json`), or use the `4.0.6` branch head if it is 4.0.5 plus fixes. Jah picks.
- Build Fabric 1.20.1 only. Turn off the Forge and other targets.
- **Done when:** `./gradlew build` gives a Fabric jar whose class list matches the Modrinth 4.0.5 jar (the diff is pasted), and the core builds from our fork.
- **Model:** Sonnet. Gradle and git work, no hard logic.
- **Needs:** Jah's yes to fork into aurelia-works.

### 1. Rebrand, drop-in check
Goal: a jar called Aurelia Transit Railway that loads in place of MTR.
- Display name, Mod Menu entry, Aurelia logo, credits and license notice, README. The mod id stays `mtr`.
- Version: report `4.0.5+atr.1.0.0`, so add-ons that pin `mtr >=4.0.5 <4.1.0` (ATU, maybe JCM and others) still load.
- **Done when:** Jah boots the Aurelia Metropolis pack with ATR in place of MTR. ATA, JCM, MAGIC, Tianjin and LU load, and an existing MTR world opens with its trains running. Game boot needs Jah's OK.
- **Model:** Sonnet. Metadata and assets.
- **Needs:** 0.

### 2a. Fold in ATU perf fixes
Goal: ATU 1.4.0's rendering and model-loading fixes are part of ATR, with the same switches.
- Turn each mixin into a direct change (merged train parts, shader-pack fast path, cheap shadows, LOD, OBJ reader, background loading, door checks, freeing unused models, F3 counters).
- **Done when:** the unit tests pass and runPerfbench numbers match or beat ATU 1.4.0 on the same scene (`tools/perfbench`).
- **Model:** Opus. Rendering code where subtle mistakes cost frames.
- **Needs:** 1.

### 2b. Fold in ATU tools and panel
Goal: the ATU hub becomes ATR's dev panel with every tool, the Announcer and Platform calls.
- Each switch from ATU keeps its saved setting (migrate the ATU config).
- **Done when:** every hub tool opens and saves in game (screenshots), and the ATU unit tests pass in the ATR repo.
- **Model:** Sonnet. Mostly moving working code.
- **Needs:** 1. Works alongside 2a.

### 3. Fold in ATA blocks
Goal: ATA's blocks ship inside ATR.
- Open decision: keep a plain-MTR ATA build for a while, so ATA users on MTR are not dropped.
- **Done when:** an ATA world opens in ATR with every block present (screenshot) and the ATA tests pass.
- **Model:** Sonnet.
- **Needs:** 1.

### 4. 1.0 bug fixes
Goal: the most-hit MTR 4.0.5 bugs are fixed.
- I propose a shortlist from `Aurelia Transit Utilities/docs/MTR_ISSUES_REVIEW.md` (for example, depot path generation saying "success" when sidings cannot connect), and Jah cuts it.
- Each fix starts with a failing test.
- **Done when:** each chosen bug has a test that failed before the fix and passes now.
- **Model:** Opus. Core simulation logic.
- **Needs:** 0. Best after 2a and 2b so the fixes land on the final code.

### 5. Release 1.0
Goal: ATR 1.0 public.
- Modrinth, CurseForge and GitHub Releases pages in the Aurelia branding. CI builds the release jar.
- The pages credit MTR and say "drop-in for MTR 4.0.5".
- The pages carry real-world operator looks from ATA (allowed since 2026-10-19). Flagged once here.
- **Done when:** the three pages are live and a clean install of the Modrinth jar boots. Publishing is Jah's go.
- **Model:** Sonnet.
- **Needs:** 1-4.

### 6. Depot controls (1.1)
Instant-deploy and Clear vehicles buttons, "path last refreshed" time and date, per-depot "always open all doors" and "disable all announcements". All of them go on the dev panel.
- **Done when:** each button and toggle works on a test server in game, with tests for the depot logic.
- **Model:** Sonnet. Server-side, but straightforward.
- **Needs:** 5.

### 7. Schedules and cancellations (1.2)
Cron-style timetables per depot and cancelling single runs. MAGIC has a version to learn from.
- **Done when:** a depot runs a weekday/weekend timetable in game, and a cancelled run shows on the PIDS.
- **Model:** Opus. Timing logic in the simulation.
- **Needs:** 5.

### 8. Sound (1.3)
Rail grinding on tight, slow curves; a pitch range past vanilla's 0.5-2; smoother speed-based motor sounds and door chimes per train. Announcements already come from ATU (2b).
- **Done when:** each sound can be heard in game, with volume, threshold and on/off on the panel.
- **Model:** Sonnet.
- **Needs:** 5.

### 9a. Passengers: simulation in core
Passengers are lightweight records in the core simulation, not vanilla mobs (mobs are what make Minecraft lag). They spawn at stations, wait on platforms, board when doors open, ride to a stop and leave. Everyone sees the same ones. Caps live on the panel.
- **Done when:** core tests show passengers boarding and leaving on a test network, and the server tick cost is measured.
- **Model:** Opus.
- **Needs:** 5.

### 9b. Passengers: drawing and boarding
Batched models with LOD, drawn on platforms and in cars, plus count and quality sliders.
- **Done when:** a busy station shows passengers boarding in game, with frame time measured against no passengers.
- **Model:** Opus.
- **Needs:** 9a.

### P1. Moving pantographs, looks only (approved by Jah 2026-10-29)
From the SCR ideas list (option 1 of 3; reaching the wire and electrification are later, separate asks).
Goal: pantographs rise smoothly when a train leaves the depot and lower when it returns, instead of the hard swap packs fake today with `PartCondition.AT_DEPOT` / `ON_ROUTE_*`.
- New optional model-part fields: arm parts rotate about a pivot up to a max angle, head parts rise up to a height; all driven by one eased 0-1 raise value per car. Per-part end choice (front / rear / both). Parts without the fields render exactly as today.
- Fields editable in the Resource Pack Creator. Dev panel: on/off, raise time, default end, speed above which it stays up.
- No built-in train has a pantograph (checked all 43 bbmodels 2026-10-29), so step 1 adds one to a built-in model (suggested Class 802) as the test case.
- **Done when:** unit tests for the raise value and part transforms pass, an old pack (JCM or LU) loads unchanged, and screenshots show the 802 raising its pantograph after leaving a depot.
- **Model:** Sonnet 5.5, high. **Needs:** master `b6e3e902` or later.

## Revised plan, 2026-10-28 (after Phase 1 was merged; awaiting Jah's go)
Jah's asks: (1) the dashboards become ONE: the ATU hub is the single dev panel (option c), plus a better web map in the spirit of MTR 4.1's; (2) bug: rain falls inside train cars; (3) Opus orchestrates, workers are Sonnet or Haiku in their own worktrees.

How to run it (Jah's rules still apply: at most 3 workers at once, Sonnet or Haiku only, no Opus workers; Opus does not edit code itself):
- Orchestrator (Opus): writes each worker's brief, reviews diffs, runs verify-pr, merges. One worktree per worker: `git worktree add "../ATR-<slug>" -b <type>/<slug> origin/master`. Delete the worktree after merge.
- Workers return: diff summary, the command output that proves "done when", what they did not test. A worker that fails the same step twice stops and reports.
- Agent time and tokens are my own working time (never human hours). Opus orchestration is the costly part and Jah is not on Max: keep briefs short, do not re-read the repo.

### 1a. Core fork swap — DROPPED (Jah, 2026-10-28)
Why: core master (1.0.2, Java 21) no longer matches the 4.0.5 mod API (VehicleLiftResponse removed in 8501c8a, Vector became a record in d408c82); the matching old core (~cad99d9) needs a Java 8 toolchain. And ATR's web map is served from ATR's own `website/` (generated WebserverResources.java), not core's, so 1d does not need the swap. Revisit only for server-side core work (9a). Original goal kept below for reference.
Goal: mod/ builds against OUR Transport-Simulation-Core fork (core/), not the prebuilt `mod/libs/Transport-Simulation-Core-0.0.1.jar`, so the web map (source in `core/website/`, ~63 TS files, served on port 8888) can be changed.
- **Done when:** `./gradlew build` for mod/ uses the jar from core/, the class list still matches the 4.0.5 jar apart from core, the dev server starts, and `http://localhost:8888/` and `/mtr/api/map/stations-and-routes` return what they do today.
- **Model:** Sonnet, medium. Gradle plumbing and a diff of the two core jars.
- **Needs:** 1. Risk: core's build may need Node for the Angular site; CI must stay cheap (cache Node).

### 1b. Rain inside train cars
Goal: no rain, rain splash or rain sound while the player is inside a vehicle; a dev-panel switch turns the fix off.
- **Hypotheses (unverified):** (1) vanilla weather decides "covered" from the block heightmap above the player; a vehicle is an entity with no blocks, so rain falls through the roof; (2) the rain sound and splash particles use the same test; (3) a shader pack or add-on re-draws weather. ATR has no rain code today (grep found none).
- **Steps:** reproduce on the dev server with `/weather rain` and a screenshot (skill `sharp`), failing check first where logic is testable (an "is the camera inside a vehicle" function), then the fix, then before/after screenshots. The switch goes on the dev panel (Jah's rule); until 2b exists, the existing config screen.
- **Done when:** before/after screenshots inside a car in the rain, and a unit test for the inside-vehicle test. **Model:** Sonnet, high (render hooks). **Needs:** 1.

### 1c. MTR 4.1 web map research (read only)
Goal: a list of what 4.1's web map does that 4.0.5's does not, for Jah to pick from.
- Jah's video could not be watched by Claude and web search found no written 4.1 notes (only 4.0 pages). Sources to use instead: the `origin/stonecutter` branch of mod/ (1.21, 4.1 work), upstream core repo history, and Jah's list of timestamps or a transcript if he pastes one.
- **Done when:** a one-page feature list (what, where in the code, effort S/M/L) saved in `docs/` and shown to Jah. **Model:** Haiku/Sonnet, read only. **Needs:** none.

### 1d. Better web map
Goal: the picked features from 1c in the ATR web map, plus the gaps seen in testing (e.g. `/mtr/api/map/vehicles` returns `{}` in the dev world).
- **Done when:** screenshots of the new map on the dev server with trains moving, and `npm run build` plus the web tests in CI. **Model:** Sonnet, high. **Needs:** 1a, 1c, and Jah's choice of features. Folding MAGIC's dashboard ideas needs its source read first (not read yet).

### 1d.1 Web map: build pipeline + map canvas fixes (planned 2026-10-28, Jah picked these from screenshots)
Evidence: screenshots of MTR's map on the dev server, `docs/webmap-before-2026-10-28/` (01-start, 06-overview, 07-station, 08-menu, 05-phone; `screenshot.mjs` retakes them). Source: `core/website/src/app` (Angular + three.js + PrimeNG).
- **Step 1, pipeline (do first, stop if it fails twice):** find how the site served on :8888 gets into ATR (earlier research: generated `WebserverResources.java` in ATR's build, not a file in `mod/libs`). Get a dev loop: `npm start` in `core/website` with the API proxied to the dev server on :8888, or a build that ATR picks up. Check core master's site still talks to the 4.0.5 server (1a was dropped because core master no longer matches the 4.0.5 mod API).
- **Fixes:** (1) open zoomed to fit the whole network, not 3 stations; (2) labels thin out by zoom (interchanges first, then by route count; hide labels that would overlap), no unreadable clump when zoomed out; (3) thicker solid lines instead of thin double strokes, with the existing per-type line style setting kept.
- **Dev panel rule:** label density and line thickness become settings in the map's own menu.
- **Done when:** before/after screenshots at the same spots (start view, zoomed-out overview), `ng lint` and `ng build` pass, and the ATR dev server serves the new build.
- **Model:** Sonnet 5.5, high (three.js label logic + build plumbing). Tokens: reading `map.component.ts` (703 lines), `utility/drawing.ts`, the build path, a few builds and screenshots.

### 1d.2 Web map: station panel + menu
- **Station panel** (`component/station-panel`): next train first (destination big, one "2 min" countdown, platform); arrivals rows show destination + countdown + platform, not the full route name in tiny text and three stacked times; station name smaller (bilingual names are long: "奧林匹亞高速鐵路一號總站 | Olympia High Speed Rail Terminal 1"); coordinates, fare zones and directions folded under "Details".
- **Menu** (`component/main-panel`): dimension shown as "Overworld" / "Nether" / "End" (raw id only for modded dimensions); labels on the icon-only line style buttons; players not hidden in a fold.
- **Done when:** before/after screenshots of a station panel and the menu at 1440 px and 390 px wide, lint + build pass. **Model:** Sonnet 5.5, medium. **Needs:** 1d.1 (dev loop).

### 1d.3 AureliaMap: BlueMap-style 3D map with the ATR layer (planned 2026-10-28)
Jah's brief: one map that replaces SquareMap, BlueMap/Dynmap and, later, Xaero's, very light on the server, with the MTR layer (system map, arrivals, departures). Design mockup: https://claude.ai/artifact/B41Pxhc3jumzGCwfzWd75Q (sample data).

**Decisions (Jah's answers, 2026-10-28):**
- First usable thing: 3D buildings "like BlueMap with an MTR (ATR) layer". 2D top-down is a view mode of the same viewer (M5), not a separate renderer.
- Base: fork BlueMap (MIT licence: https://github.com/BlueMap-Minecraft/BlueMap), keep its notices, add the ATR layer. Not a from-scratch renderer.
- Renderer: a separate process next to the server that reads the saved world, run at low priority (`nice`); also able to run on another machine. The game server does no map work. Output is static tiles served from disk.
- Modded blocks: read block models and textures from the mod jars (ATR, ATA, Metropolis). OBJ vehicle models and block entities stay out.
- Home: inside the ATR repo (`mod/`), as its own folder with its own Gradle build so it does not touch Loom or ATR's drop-in `mtr` jar.
- Targets: Fabric 1.20.1 world data only. Audience: Jah's server and a few friends; a public release is not a goal yet.
- Not decided: the in-game map (1d.4). It gets its own plan after M6.

**How the estimates were made:** tokens = files read + edits + build output + screenshot reads, scaled from 1d.1/1d.2 (about 60 and 20 min of agent time; token use was not measured). BlueMap is a big codebase I have not read, so M1 to M4 carry the widest ranges. Times are my own working time.

**Common rules:** one phase per thread; dev panel rule (every visible feature gets its on/off, colour and size controls in the map's own menu); no spawned helpers except the code-review and verify-pr reviewers; stop if the same fix fails twice.

#### M0. BlueMap check (read, build, run)
- **Goal:** know what we are forking before touching anything.
- **Steps:** clone BlueMap and its web viewer into a scratch folder (not the repo); list modules, licence files and every third-party licence; build the CLI; run it on the dev world (`mod/fabric/run/world`); open the viewer in Playwright; time the render and record CPU and memory; note how markers/extensions work and whether the viewer is a separate repo.
- **Done when:** a screenshot of the dev world in 3D from BlueMap's own viewer, render time and peak memory for the dev world, a one-page note `docs/aureliamap-bluemap-check.md` (modules, licences, how markers and add-ons work, what to cut), and a go/no-go line.
- **Model:** Sonnet 5.5, medium: mostly reading and running commands. **Needs:** none. **Risk:** BlueMap may not read a flat dev world with mod blocks cleanly; that shows up here, cheaply.

#### M1. Fork in the ATR repo and a low-priority renderer
- **Goal:** `aureliamap/` in the ATR repo builds and renders a world into static tiles, as a separate low-priority process, and a tiny web server shows them.
- **Done when:** one command renders the dev world; `localhost:<port>` shows the 3D map; the process runs under `nice`; with the dev server running, game ticks do not slow while it renders (log TPS or tick times before and during); BlueMap's licence and notices are in the repo.
- **Model:** Sonnet 5.5, high: build plumbing and a large unfamiliar codebase. **Needs:** M0 go.

#### M2. Modded blocks from mod jars
- **Goal:** ATR, ATA and Metropolis blocks show with their real models and textures.
- **Done when:** before/after screenshots of an ATR station and a Metropolis building: before = missing/pink blocks, after = real blocks; a list of what still does not render (block entities, OBJ models).
- **Model:** Sonnet 5.5, high: model/blockstate parsing edge cases. **Needs:** M1.

#### M3. MTR data for the map
- **Goal:** the data the layer needs, in one place: stations, line geometry, live trains with positions and next arrivals.
- **Steps:** check what `/mtr/api/map/*` already gives (`vehicles` returned `{}` in the dev world: generate depot paths first so trains run); add what is missing as a small ATR-side endpoint or a file the renderer can read; keep server cost near zero (cached, no per-request world access).
- **Done when:** a JSON sample from the dev server with stations, lines and at least 2 moving trains, plus a test for any new ATR code; server tick times unchanged.
- **Model:** Sonnet 5.5, high: touches ATR mod code and needs a failing test first. **Needs:** M1 (for the run setup only); can run before M2.

#### M4. ATR layer in the 3D viewer
- **Goal:** lines drawn over the terrain, stations with labels, trains moving, click a station for the arrivals panel (1d.2 look).
- **Done when:** screenshots of the 3D map with lines, labelled stations and trains in two frames a few seconds apart (trains moved); the arrivals panel on click at 1440 and 390 px.
- **Model:** Sonnet 5.5, high: three.js, label logic, live data. **Needs:** M1, M3.

#### M5. Settings, flat view, phone, black theme
- **Goal:** the dev-panel switches, a top-down flat view, working on a phone, OLED black theme.
- **Done when:** screenshots at 1440 and 390 px in 3D and flat, light and dark; switches for line thickness, label density, layer on/off, train icon size.
- **Model:** Sonnet 5.5, medium. **Needs:** M4.

#### M6. Packaging, CI, licence notices, numbers
- **Goal:** a build anyone can run, CI that compiles and runs the tests, third-party notices, and measured performance.
- **Done when:** CI green on a PR; a README with the run command; a table of render time, memory and server tick impact on the dev world.
- **Model:** Sonnet 5.5, medium. **Needs:** M5.

#### Known risks
- BlueMap updates: a fork drifts. Keep our changes in a few files and record the BlueMap version we forked.
- Mod block entities and OBJ vehicles will not render; trains show as the live layer, not as models.
- Flat dev world makes terrain boring; use a copy of the real world for the final screenshots (needs Jah's OK to read it).
- ATR is MTR 4.0.5-compatible: any new endpoint must not break the drop-in `mtr` id.

### Dev panel (existing 2b)
2b now also means: ATU's hub becomes the one panel for every tool and every ATR switch, including 1b's. Keep ATU's saved settings.

### Known
- In the dev client, add-ons built for the real game crash: MAGIC (`NoSuchFieldError field_22764` in DashboardScreen) and JCM (`RenderRailsMixin` invalid descriptor, rails vanish). Dev runs use ATR alone; the add-on jars are in `mod/fabric/run/mods_parked/`. Final add-on checks belong in the real Prism pack.
- Dev world: flat, peaceful, view 6 / sim 4, copied from ATA's dev-server setup. Existing Metropolis depots have no generated paths, so no trains spawn until Generate is clicked in the in-game dashboard.

## Decision, 2026-10-28 (later): retire ATU into ATR
ATU is sunset: all of it (perf fixes in 2a, hub/tools/panel in 2b) moves into ATR. After 2b ships: one last ATU release that points users to ATR, and ATR imports saved ATU settings. The shared core below now only spans ATR and ATA. Goal driving it (Jah): make ATR itself cheap; 20 FPS on a 3070 with Complementary + Distant Horizons near OBJ trains is not acceptable. Perf scout list: `docs/perf-scout.md`.

## Decision, 2026-10-28: a shared Aurelia core (Jah picked option c)
- One shared core that ATR, ATU and ATA all use: the single dev panel (ATU hub becomes it), the web-map data (vehicles, arrivals, departures), and shared drawing for outlines. Each mod keeps its own jar; ATR stays the drop-in `mtr` jar and is not folded into anything. AureliaMap (separate project) shows the map and takes MTR data from ATR; AureliaEdit stays its own product, not part of this.
- Folded in with it (Jah, same message): the ATA tunnel creator (cut by height / across the tracks / along the tracks, building speed, survival handling, a Litematica-style outline preview before building) and ATU's track-rendering scaling setting (default cutoff 150, a slider).
- Open, to settle in Phase A before any code: where the core lives (own repo vs inside ATR), how ATU/ATA depend on it without breaking ATR's drop-in id, and whether the survival tunnel opt-in (materials check from inventory and chests, staged build) ships at all. The tunnel creator code stays in ATA's repo until A says otherwise; the render-scaling slider is built in 2a and shown on the 2b panel.
- Order: 1a-1c, then A (design), then 1d with the map data it needs, 2a, 2b. Phase A is read-and-write-a-doc only, by the Opus orchestrator.

## What MTR+ had planned (sneak peeks Jah shared, 2026-10-28; the project looks abandoned)
MTR+ was a fork on Minecraft 1.21.4, last seen as 5.0.0-alpha1 (test video from July 2026, a recording from 2026-06-20). Ideas worth taking:
- **Passengers (feeds 9a/9b):** an on/off switch on its own tab. Blocks for zone, pathfinding and destination, plus a selector: mark where passengers wait on the platform, then path from the station out to homes and landmarks. Train capacity is set per depot, and passengers wait for the next train when one is full. They claimed "essentially no perf drop".
- **Ride HUD:** an onboard overlay showing "Service to X", speed in km/h, route, next stop and "Arriving at", with Shift to list the stops and double Shift to hide it. It fits the ATU Announcer (2b) as a panel toggle.
- **Modern default PIDS** (Elizabeth line style), and the JCM PIDS merged in. For ATR, JCM stays a separate add-on (drop-in), so we would only add our own modern PIDS.
- **New default font:** they picked GT America, but that's a **paid Grilli Type font, which a free mod cannot ship**. If we do this, use an open-licence font (to be picked).
- **Building blocks:** metal grates, cream bricks and other modern station materials. These belong in ATA (3 or later).
- **Default trains and buses** whose logo and colour you can customise easily.
- MTR+ dropped JCM as a requirement. ATR never needed it.

## Phase 4 candidates from Jah's play (2026-10-28)
- Shaders in tunnels: 67 fps outside, 34-37 fps inside Jah's own tunnels, and they look odd (215 fps without shaders). Profile with spark; may be the shader pack (AureliaShaders), not ATR.
- Train spawn hitch with shaders (85 -> 45 fps).

## Launch overhaul (Jah, 2026-10-28): ATR must not look like MTR at public launch
Replaces "MTR's look and controls stay the same in 1.0". Kept for compatibility: mod id `mtr`, block/item ids, save format, the MTR 4.0.5 methods add-ons hook (DropInSignaturesTest).
Style: match AureliaMap (dark, clean, modern; one Aurelia style for mod, map and site). Default trains/signs stay MTR's for now.
| # | Phase | Est. agent time | Notes |
|---|---|---|---|
| O1 | Aurelia UI kit + screens: dashboard, station/route/depot/siding editors, options, panel | 6-10 h | screenshot per screen; style switches on the panel |
| O2 | Blocks, items, rails, signals: new textures and models (same ids) | 5-10 h + Jah's art review | needs Blockbench MCP (failed to connect today) or Blender; art quality is the risk |
| O3 | Building tools: guided station placement, better rail drawing, tunnel creator (creative/ops) | 5-8 h | |
| Z | Zip lines (handle + seats), creative/ops | 3-5 h | headline launch feature |
New order: 3 (ATA blocks) -> 4 (bugs + spawn drop) -> O1 -> O3 -> Z -> O2 -> 1d.4 -> release. Total before launch roughly 30-50 h agent time. Each phase gets its own plan and Jah's yes.

### O1. Aurelia UI kit and screens (plan)
Goal: the dashboard, the station, route, depot and siding editors, the options screen and the Aurelia panel use one dark flat look, the same colours as AureliaMap's dark theme (`aureliamap/common/webapp/src/scss/variables.scss`: background `#181818`, control `#222222`, border `#444444`, text `#ffffff`, dim text `#aaaaaa`, accent `#00489d`). Minecraft's font stays. Trains, signs, blocks and the world are not part of this phase.

The look is on by default, so a new install does not look like MTR. Each of the seven screens has its own switch on a new Look tab of the Aurelia panel. Off puts that screen back to MTR's widgets. The switches live in a new file, `config/aurelia/ui.json`. A missing file, a missing key, or a value that is not true or false keeps that screen on the Aurelia look. `config/mtr.json` is not read or written. Old worlds and old configs load unchanged.

What changes on a screen while its switch is on:
- Dashboard (`DashboardScreen`): the left panel fills `#181818` instead of `#121212`. Plain buttons become flat. The search box keeps MTR's cursor and gains an Aurelia border. Icon buttons (find, edit, map mode, the panel logo) and the route-colour squares stay as they are, because they draw their own textures.
- Station editor (`EditStationScreen`), route editor (`EditRouteScreen`), depot editor (`EditDepotScreen`), siding editor (`SidingScreen`, including the platform fields it shares with `SavedRailScreenBase`): the dirt background becomes a flat `#181818` fill. Buttons, checkboxes and sliders are flat. Text fields keep MTR's cursor and selection; the border is repainted.
- Options (`ConfigScreen`): same flat background, flat buttons and flat sliders. The logo, the row labels and the Patreon names stay.
- Panel (`AureliaPanelScreen`): same flat background and flat buttons and sliders, plus the Look tab.

Drawing goes through `AureliaChrome` and uses `DrawContext.fill` plus the widget's existing text. No new `Text`, `String`, `Identifier` or `GuiDrawing` per frame. The dwell slider's tick numbers use a static table of `"0"` to `"120"`. A number outside that table is skipped rather than allocated.

Files:
- `org.mtr.aurelia.client.ui.AureliaPalette`, `AureliaUiSettings`, `AureliaChrome`, `AureliaUi` (which screen is open, and whether its switch is on).
- Client mixins in `org.mtr.atr.mixin`: button, slider, checkbox, text-field border, screen background, dashboard panel colour, the short slider (`WidgetShorterSlider` draws its own textures, so `SliderWidget` alone does not reach it), and an accessor for `GraphicsHolder.drawContext`.
- `AureliaPanelScreen` grows the Look tab. `AureliaClient` loads the file on startup. Strings in `assets/aurelia/lang/en_us.json`. The mixins are registered in `atr.mixins.json`.

No method in `org.mtr.mod` changes its name, descriptor or lambda list. The mixins add behaviour beside the existing methods. `DropInSignaturesTest` still compares the class files with the MTR 4.0.5 jar.

Tests: `AureliaUiSettingsTest` only. One test checks that a missing key stays on, a non-boolean is ignored, and an unknown key is ignored. One test checks that a missing file and a broken file both leave every screen on the Aurelia look. No game test: the drawing needs a client, and this phase does not launch one.

Risks:
- A plain `ButtonWidget` that some other mod adds on top of one of these seven screens gets the flat look while that screen's switch is on. Textured buttons do not, because they override `renderButton`.
- The list rows on the dashboard and the station editor stay MTR's (colour chip and name). Restyling them would mean editing `DashboardList.render`, which already allocates every frame.
- The text-field border is painted after MTR's field, so the inner fill stays black. Cursor, selection and suggestion behaviour stay MTR's.
- Nothing here has been looked at in a running game.

## After 1.0: new features (Jah, 2026-10-28)
Picked so they don't compete with MTR, JCM, MAGIC, Tianjin, LU or ATA. Each gets its own phase plan when its turn comes. Not estimated yet.
- **Zip lines (moved to launch, see Z above):** a handle item for one-way downhill lines (gravity, the cable sags, you drop off at the end pole), plus hanging seats for lines that go both ways. Creative and operators only at first; survival recipes later if wanted. Switches on the panel (speed, brake, sag).
- **Terrain movers:** funicular/rack railway for track steeper than MTR allows, inclined elevator, moving walkways.
- **Bike/scooter share:** docks with rideable bikes or scooters, live counts on AureliaMap.
- **Journey planner:** pick a destination on the phone or AureliaMap; route with transfers; "change here" and "next stop: yours" prompts while riding.
- **Service alerts + fares:** alerts on boards, the map and the Announcer (overlaps phase 7); daily fare caps and a trip history.
Order: 3 -> 4 -> 1d.4 -> 1.0 release, then these.

## Later ideas (not planned)
- Passengers with destinations and ridership stats per route.
- Ride HUD, modern PIDS, open default font, modern station blocks, customisable default trains and buses (from MTR+, above).
- Fixing Nemo's resource pack updater fork. It's a separate project.
