# ATR launch plan (interview with Jah, 2026-11-07; direction v2 2026-11-08)

Approved by Jah 2026-11-07 as one plan ("Everything looks good"). Stop only at review checkpoints and the check-in page, not per phase.
Direction v2 (interview 2026-11-08, "Yes, write it up"): see "Direction v2" below and the new phases W, H, E, EW, AE, L. No code for v2 until Jah says go.
Picks (2026-11-09, Jah's in-game look at Utrecht Leidsche Rijn and Studio): 18 items in `docs/handoffs/PICKS-2026-11-09-station.md`, planned below as phases UI, S2, FG, AP2 and T0, ahead of E1. Waiting on his go.
Old plan and handoffs: `archive/2026-10/`. Research behind this plan: `docs/research-2026-11-07/` (player wishlist, livery gaps, station generator review, Indus Gaming notes).

## What ATR is
- Drop-in replacement for MTR 4.0.5 on Minecraft 1.20.1 Fabric, forever: mod id `mtr`, block/item ids, save format and the 4.0.5 methods add-ons hook (`DropInSignaturesTest`) never change. 1.20.1 only, no 1.21 port.
- Realistic but easy, and light on performance. Not hyper-real like Create.
- Audience: Jah's Aurelia Metropolis server first, then public (Modrinth, CurseForge, GitHub). Metropolis moves to the new build only after O2.
- Public launch only when everything is tested in game and there is a trailer.

## Rules for every phase
- Quality bar (Jah 2026-11-07): 9/10 realism for everything visible: airport, stations, passengers, signals, sounds, trains, block art. Each PR rates its result x/10 against real references and lists what keeps it below 9.
- Every visible feature gets its switches and sliders on the Aurelia dev panel. Heavy features can be turned off and drop detail with distance. Passenger defaults are set on the panel; no fixed frame budget.
- Decoration uses plain blocks. Block entities only where something changes live (PIDS, departure boards, clocks, gates).
- No operator logos or names in the ATR jar. All branded liveries (real operators and Stepford County Railway) go in a separate optional "ATR Liveries" resource pack. Jah decided: no permission request to the SCR devs.
- Jah's Metropolis material is private: it lives in his own pack (Prism instance `~/Library/Application Support/PrismLauncher/instances/Aurelia Metropolis`) and is never shipped.
- New code in new methods at the end of a class; no new lambdas or anonymous classes before existing ones.

## Team
- Opus 5.5 (orchestrator) plans each phase, reviews every diff and fixes what looks broken.
- All Blockbench work (models and textures for O2 and the LV trains, the 802-style pantograph parts, signal models) is done by Opus itself, never by DeepSeek (Jah 2026-11-07: does not trust the cheap model with art). DeepSeek may only wire finished models into JSON, ids and packaging.
- Up to 5 DeepSeek implementers at once: T3 `delegate_task`, provider `opencode`, model `opencode-go/deepseek-v4.1-flash`, variant `max`, each task starting with `$ds-help`. Every implementer gets its own separate worktree and branch (one phase each); no two agents share a worktree, and nobody works in `mod/` itself. Opus's own work (Blockbench, fixes) also goes in its own worktree.
- Scouts: same model, variant `low`, or Claude Haiku 5.5. Merges go through skill `verify-pr` (Haiku/Sonnet reviewer).

## Direction v2 (Jah, 2026-11-08)
- **Launch = everything.** Every phase below is launch-required, including the old Later list. Launch order: the open phases and the new ones, then T (test pass), then TR (trailer), then public.
- **Headline: an in-game station and airport editor.** It opens from the wrench as a native Minecraft screen with a 2D top-down plan and a 3D pane. Editing is full freehand: curved tracks, any platform shape, any building outline. Drawn tracks become real MTR rails (nodes, curves, switches) and snap to nearby lines. You draw the shape, and the generator's auto-detail fills in canopies, stairs, lifts, lights and furniture. Every piece can be moved or deleted afterwards.
- **Browser Studio stays.** It and the game read and write the same station file, both ways.
- **The word command goes.** `/ata_station <words>` is removed completely; the parser stays only where Studio or presets need it internally.
- **The preview becomes a hologram.** Litematica-style see-through real blocks replace today's particle outline (`station/world/StationPreview.java`). This was the "something's off" on card O2: it was about the wrench preview, not the block art.
- **Terrain: build anywhere, undo everything.** `replace_terrain` and the approval step go. Every build saves what was there before, in its own file, so it can be undone at any time, even after restarts. The ~23 game tests that check the approval rule are rewritten to check that undo restores the ground exactly (Jah OK'd this 2026-11-08; list them in the EW PR).
- **Earthworks:** level and slopes, retaining walls, digging underground, viaducts. Big jobs build over time, layer by layer, like a construction site, so the server doesn't lag.
- **Creative/ops only.**
- **Calm UI, no overload.** One wrench with modes (Station / Airport / Bridge / Earthworks). Old `station_wrench` and `airport_wrench` items still load and act as the new wrench. One ATR menu with tabs (Build, Guide, Settings, Dev) holds the Codex, the guide, the dev panel, the Studio link and wayfinding; the old commands still work. Each mode shows 4-6 main tools; the rest go in a More drawer, and extras like the hologram's red tint and layer slider stay off until switched on.
- **9/10 judge.** Claude scores, then a fresh reviewer scores the same shots blind. Both must give 9 or more.
- **Workers.** Jah: use as many DeepSeek (OpenCode) workers as useful (this overrides the 3-helper cap for DeepSeek only). Jah distrusts them since a cheap-model change bowed the platform rails (fixed in #65). So Opus owns anything that places rails or track geometry, and every worker diff gets an Opus review plus an in-game shot before merge.

## Phases
Agent time is rough. Jah expects about 4-5 h of agent time with parallel workers. Opus's agent-time guess for v2 is in the table. Record actuals beside each one.

| # | Phase | Est. | Wave | Status (2026-11-09) |
|---|---|---|---|---|
| F | Foundations: land the waiting branches | 2-3 h | 0 | done (#47-#50) |
| R | Rendering: rail cost with shaders, far rails and trains, block entity counter | 6-10 h | 1 | far rails merged (#51); bench measured 2026-11-09 (one train, shaders on, 40 m: 24 -> 54 fps) and counter seen in game, see `docs/perf-launch.md`; station, tunnel, four-rail and far-view scenarios not measured |
| S | Station generator: the most realistic in Minecraft | 10-15 h | 1 | merged (#62, 50f1842a0); Haiku audit 9.0 every category |
| AP | Airport generator (#61) to 9, merge as the base | 3-5 h | 1 | merged (#61, f5ca64a0d) |
| PX | Passengers finished (launch headline) | 5-8 h | 1 | merged (#49, #54) |
| SG | Signals and driving (SCR/TSW feel) | 8-12 h | 1 | merged (#55; cab HUD #60, 4e71e066d, seen in game) |
| SN | Sound profiles for any train | 6-10 h | 1 | merged (#52, #57) |
| O2 | Block, item, rail and signal art (Opus, Blockbench) | 5-10 h | 1 | merged (#53); the "something's off" was the wrench preview, now phase H |
| RT | Route control | 3-5 h | 2 (after SG) | merged (#59, d9575becb); trains follow route control |
| LV | ATR Liveries pack (separate) | 10-20 h | 2 | in progress in ATR-Liveries (350 LNR, 450 SWR); launch-required |
| PK | Private pack format | 2-4 h | 2 | merged (#56) |
| W | One wrench with modes, one ATR menu, remove the word command | 3-5 h | 4 | merged (#67, 8fc27bed0) |
| H | Hologram preview (Litematica style) | 4-6 h | 4 | merged (#68, 26bbe93dc) |
| UI | Menus that fit: wrench Station tab, Bridge tab wording, Aurelia panel restyle | 4-6 h | 4b | not started (picks 12-13, check-in 2026-11-09) |
| S2 | Station connections: fence, noise barrier, girders, help points, clock, lift tower, pylon | 4-6 h | 4b | not started (picks 1-9) |
| FG | Fare line: readers that block, station sealed with glass | 3-5 h | 4b | not started (picks 10-11) |
| AP2 | Airport one block into the ground; default A320 model pass (Blockbench) | 3-6 h | 4b | not started (picks 15-16) |
| T0 | Short guided look at UI, S2, FG, AP2 and today's merges, with Jah | 1-2 h | 4b | not started (pick 18) |
| E1 | Editor screen: 2D plan, 3D pane, shared station file with Studio | 6-10 h | 5 | not started |
| E2 | Freehand tracks that become real MTR rails (Opus only) | 6-10 h | 5 | not started |
| E3 | Freehand platforms and buildings, auto-detail fills them | 6-10 h | 5 | not started |
| EW1 | No approval, saved undo for every build, build over time, approval tests rewritten to undo | 4-6 h | 6 | merged (#66, e18957491) |
| EW2 | Earthworks: level/slopes, retaining walls, dig, viaducts | 6-10 h | 6 | not started |
| AE | Airports in the editor: runways, taxiways, terminals | 5-8 h | 7 | not started |
| L1 | Guard, dispatcher and signaller roles | 6-10 h | 8 | not started |
| L2 | Drivable vehicle base, then free-driving cars and rideable bike share (picks 17, 19) | 13-24 h | 8 | not started |
| L3 | Journey planner extras | 2-4 h | 8 | not started |
| L4 | In-game map (1d.4) | 5-8 h | 8 | not started |
| T | Full in-game test pass | 4-6 h | 9 | not started |
| TR | Trailer (editor + earthworks construction shots lead) | 4-8 h | 9 | recorder (#58) and scripted shots (#63) merged; trailer not started |

### F. Foundations
- Waiting branches (check each is still wanted, rebase on master, review, PR, merge):
  - `ATR-features` / `feat/passenger-doors`: passenger walking, skins, doors; 22 commits plus uncommitted edits. Commit the edits on its branch first.
  - `ATR-fixes` / `fix/scheduled-sound-once`: ScheduledSound plays once (1 commit).
  - `ATR-o1` / `review/o1`: UI screenshot tests (7 commits).
  - `ATR-rel` / `chore/release-1.1.0`: 1.1.0 notes (fold into the next release, GitHub only, no store upload).
  - `ATR-pr-work` / `fix/hamburg-beijing-amsterdam-sheets`: another agent touched it 2026-11-06. Ask Jah before touching.
- Replace the repo's old `mod/PLAN.md` with this file (docs PR).
- Done when: those branches are merged or parked with a reason, master is green, worktrees cleaned.

### R. Rendering and performance (headline: "rails don't kill FPS with shaders")
- Measure first: plain MTR 4.0.5 vs ATR on the bench world, with and without shaders (Jah's numbers: 67 fps outside, 34-37 fps in his tunnels with shaders; MTR issue #1287: 4 rails take 100 fps to 40).
- Cut rail drawing cost with shaders; check the shader tunnel drop.
- Separate view distance for rails and trains, past Minecraft's render distance (today `RenderRails.java:332` stops at render distance x 16). Far rails and trains drawn as cheap simple shapes. Must work with Distant Horizons and with Jah's AureliaLOD.
- Block entity counter on the dev panel, with a warning when too many are drawn.
- Done when: before/after numbers (ATR vs MTR, shaders on/off) in `docs/perf-launch.md`, panel sliders, unit tests for the distance and detail logic.

### S. Station generator
From `docs/research-2026-11-07/station-generator-review.md`. Creative/ops only. (v2: the word parser, preview and editing items below move to W, H and E.)
- Looks: log every block the one-block-per-cell rule skips (`StationPlanner.java:9-10`), then fix the causes; platform length vs train length check; tactile strips and edge lines; CCTV; per-country platform height.
- Realism target: `StationAudit` checks against the 26 measured stations in `ATA-research` (sizes, track counts).
- Blueprint preview: pick style and size, ghost over the tracks, adjust, confirm, builds over a few seconds (build on `client/StationPreview.java`, `station/world/PreviewGeometry.java`; faster batched `StationBuildJob`).
- Fine editing after build: saved per-cell override map, applied last.
- Pickers (preset, class, scale, era, tracks, platform length, roof) replace the word parser as the main input; the word command is removed in W.
- New types: small halt, bus/tram stop, curved platforms. (Underground, elevated, hub, airport already exist.)
- Decoration block entities become plain blocks.
- Done when: unit tests for planner, audit and overrides; screenshots (skill `sharp`) of each type and the preview.

### PX. Passengers (launch headline)
- Finish walking, boarding and doors from F; detail levels by distance; cost sliders and a live cost read-out on the panel; off by default until Jah decides.
- Done when: frame time measured on a busy station and a full train, screenshots, tests for walking and boarding logic.

### SG. Signals and driving
- Signals whose lights come from the real train spacing and set route (2/3/4-aspect), on real-looking signal models (O2).
- Driver at launch: notched throttle and brake, door control, stop markers per train length, AWS-style warning with acknowledge, TPWS-style overspeed/red-signal trip, a simple score per run.
- Guard, dispatcher and signaller roles: phase L1, launch-required (v2).
- Done when: unit tests for aspects and protection, a driven run in the bench world with screenshots.

### SN. Sound
- Sound profiles any train can use, from any pack (UK EMU, tube, DMU, NS, metro, tram...), chosen per train on the panel or in the vehicle editor.
- Sounds synthesised from the existing pitch/motor system, plus CC0/CC-BY recordings found online, credits listed in the jar.
- Done when: profiles load for a train from another pack, credits file, Jah hears them.

### O2. Art (Opus, Blockbench MCP)
- New textures and models for blocks, items, rails and signals, same ids. Jah reviews each batch.

### RT. Route control (after SG)
- Waypoints a route must pass, platform choice per route, a free platform when the planned one is taken. Low per-frame cost; cache paths per route.

### LV. ATR Liveries pack (separate resource pack)
- Fill gaps no 1.20.1 MTR 4 pack covers (`docs/research-2026-11-07/livery-gaps.md`): NS VIRM and SLT; UK Class 350/450, 153/156/170, 321/507/508; London Underground S Stock and 2024 Stock; Stepford County Railway classes. Most need new models, not just paint: Opus builds every model and texture in Blockbench; Jah reviews each train. Each gets a sound profile from SN.

### PK. Private pack format
- A pack format ATR reads for operators, liveries, sounds, station presets and timetables. Read Jah's Metropolis instance to decide the fields. ATR ships the format, never his content.

### T. Test pass
- Everything shipped since v1.0.2 checked in game, including two players: pantographs, Station Studio, sitting, chimes and pitch, timetables, passengers, zip lines, Aurelia UI, plus all new phases. Bug list cleared.

### TR. Trailer
- Dev-only camera recorder (Replay/Flashback style camera paths), in the dev source set only, never in the release jar; deleted after the trailer.
- Bench world with ATR-built stations; clips of every headline; the R benchmark numbers on screen.

### W. One wrench, one menu (wave 4)
- Station, Airport, Bridge and Earthworks become modes of one wrench. Scroll or press a key to switch. The old item ids stay registered so old worlds load.
- One ATR menu with tabs. `/ata_station <words>` is removed. `/ata_guide`, `/ata_studio` and `/atr_bridge` keep working.
- Done when: old-world items still load (game test), the menu shows every tab, screenshots.

### H. Hologram preview (wave 4)
- Client-side see-through copies of the planned blocks, shown only to the planning player. In the More drawer: a red tint on blocks that replace existing ones, and a one-level layer slider. The particle outline stays as a dev panel fallback for slow PCs. Detail drops with distance.
- Done when: before/after screenshots of the same station, frame time with the hologram on a big station.

### Picks round (wave 4b, before E1; from `docs/handoffs/PICKS-2026-11-09-station.md`)
One thread and one PR each, in this order. Every fix is shown with same-camera before/after shots in a copy of "ATR Visual".

**UI. Menus that fit** (4-6 h; Sonnet, skill `ui-critique` with a Haiku reviewer; needs nothing)
- Wrench Station tab: every row is on screen at 854x480 and at 1920x1080 on automatic GUI scale (scroll or two columns); no clipped labels ("Kuala Lumpur").
- Bridge tab: plain words in place of "Capture bridge" and the preview wording, and fewer buttons. Airport tab and the Earthworks "coming soon" stay.
- Aurelia panel: tab rows no longer cover the counter line, the ATR menu buttons no longer cover the setting labels, and the whole panel is restyled (Jah: "the UI is terrible"). Use the notes in `~/.t3/worktrees/mod/ui-study`.
- Done when: screenshots of every tab at both sizes with no overlap or clipping, and Jah has looked at them.

**S2. Station connections** (4-6 h; Claude itself, no worker; needs nothing)
- First: rebuild the pre-#65 station (x 304-368, z 56/70) on master and show the platform rails straight, before/after.
- The line-side fence runs unbroken past catenary masts. The noise barrier joins the mast.
- Girders reach the square steel column. Help points hang under the platform PIDS on the round steel column.
- The analog clock is more realistic and hangs from, or stands on, something.
- NS lift tower: its pole lands on a full block; above the canopy the tower is the dark steel panel only; inside the tower, and at the entrance looking in, the full block replaces the post.
- The entrance pylon no longer stands beside the ticket machine (turned 90 degrees or moved).
- Done when: before/after shots of each item on a fresh Leidsche Rijn build, and 9/10 from Claude and a blind reviewer.

**FG. Fare line** (3-5 h; Opus-class, it is game logic plus geometry; after S2, same files)
- A player who has not tapped in cannot pass the card readers; tapping lets them through.
- The paid area is closed all the way round with glass, so there is no way in past the readers on foot. Studio's Checks list gains "Fare line sealed".
- Dev panel: a switch for blocking, off for stations that should stay open.
- Open point: creative and op players can still fly or break in. The seal is for survival and adventure players.
- Done when: in game, a survival player without a tap is stopped at every entrance and gets through after tapping; the new check passes on every station type.

**AP2. Airport in the ground, A320** (3-6 h; airport: DeepSeek worker with an Opus review; A320: Claude itself in Blockbench; needs nothing)
- Runways, taxiways and aprons sit one block into the ground, not raised. Old saved airports are not moved.
- The default A320 model gets a Blockbench pass against photos.
- Done when: before/after shots of the airport edge at ground level; the A320 beside a reference photo, 9/10.

**T0. Guided look** (1-2 h; Jah plays, the agent moves him between spots; after the four above)
- Jah walks UI, S2, FG, AP2 and the 2026-11-09 merges (W, H, RT, SG, AP) in the dev client and picks again. New picks go in a new picks file, not straight into code.
- Done when: his picks are written down and each is either planned or dropped.

### E. In-game editor (wave 5; split in three)
- E1: native screen from the wrench with a 2D top-down plan and a 3D pane, and the same station file as browser Studio (both ways).
- E2: draw curved tracks freehand. They become real MTR rail nodes and connections, snapping to nearby lines. Opus only, with a geometry check that platform rails stay straight (#65).
- E3: draw platform and building outlines in any shape. Auto-detail (S) fills them, and any piece can then be moved or deleted.
- Done when: a station drawn in game, opened in Studio, edited and opened back in game unchanged; trains run on the drawn rails; 9/10 by Claude and a blind reviewer.

### EW. Earthworks and undo (wave 6)
- EW1: remove `replace_terrain` and the approval. Every build stores what was there before in its own file, under a new key, so old worlds are unchanged. Undo works any time, even after a restart. Builds run over time with a dev panel blocks-per-tick slider. The ~23 approval game tests are rewritten to check undo (Jah OK, 2026-11-08).
- EW2: level and slopes, retaining walls, digging underground (cut-and-cover and bored), viaducts, all from the Earthworks mode.
- Done when: a build plus undo restores the ground block for block (game test); a long viaduct builds with no server lag spike (measured); screenshots.

### AE. Airports in the editor (wave 7)
- Runways, taxiways, aprons and terminals drawn in the same editor; auto-detail from AP fills them.

### L. Former Later list (wave 8, launch-required)
- L1 guard, dispatcher and signaller roles (on top of SG). L3 journey planner extras. L4 in-game map 1d.4. Terrain movers are now EW.
- L2 (Jah 2026-11-09): one drivable-vehicle base (a new entity with real steering, separate from MTR's rail vehicles), then free-driving cars and rideable bicycles for bike share on it. Our own code and models only: MTR Traffic Addon is all rights reserved, nothing is copied from it. Done when: a player drives a car and rides a hired bike in survival, both saved and reloaded, with shots.

## Unattended run (Jah 2026-11-09)
- The loop runs every open phase in table order until launch, without stopping for Jah. State: `LOOP-atr-finish.md`.
- Every phase is built by Claude Opus 5.5 (Jah's pick; no DeepSeek workers in this run). Subagents as useful.
- The loop starts and closes its own perf-bench client for shots, in world copies only, and never touches a client Jah started.
- Each PR merges by itself after green CI and a Sonnet verify-pr PASS on the head commit.
- No fixed try cap. When usage passes 90%, write the handoff, wait for the window to reset, then resume.
- T0 and T: Jah plays when he can; the loop does not wait for them before moving on to E1.
