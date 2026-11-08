# ATR launch plan (interview with Jah, 2026-11-07)

Approved by Jah 2026-11-07 as one plan ("Everything looks good"). Stop only at review checkpoints and the check-in page, not per phase.
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

## Phases
Agent time is rough. Total about 65-110 h.

| # | Phase | Est. | Wave | Status (2026-11-08) |
|---|---|---|---|---|
| F | Foundations: land the waiting branches | 2-3 h | 0 | done (#47-#50) |
| R | Rendering: rail cost with shaders, far rails and trains, block entity counter | 6-10 h | 1 | far rails merged (#51); perf numbers and block entity counter not checked |
| S | Station generator: the most realistic in Minecraft | 10-15 h | 1 | PR #62 open, 7.5/10, CI failing; fixing to 9 |
| PX | Passengers finished (launch headline) | 5-8 h | 1 | merged (#49, #54) |
| SG | Signals and driving (SCR/TSW feel) | 8-12 h | 1 | merged (#55); #60 (cab HUD) open, not seen in game |
| SN | Sound profiles for any train | 6-10 h | 1 | merged (#52, #57) |
| O2 | Block, item, rail and signal art (Opus, Blockbench) | 5-10 h | 1 | merged (#53); Jah: "something's off", waiting on which part |
| RT | Route control | 3-5 h | 2 (after SG) | PR #59 open: editor and saved data only, trains don't use it yet |
| LV | ATR Liveries pack (separate) | 10-20 h | 2 | in progress in ATR-Liveries (350 LNR, 450 SWR) |
| PK | Private pack format | 2-4 h | 2 | merged (#56) |
| T | Full in-game test pass | 4-6 h | 3 | not started |
| TR | Trailer | 4-8 h | 3 | recorder (#58) and scripted shots (#63) merged; trailer not started |

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
From `docs/research-2026-11-07/station-generator-review.md`. Creative/ops only, instant.
- Looks: log every block the one-block-per-cell rule skips (`StationPlanner.java:9-10`), then fix the causes; platform length vs train length check; tactile strips and edge lines; CCTV; per-country platform height.
- Realism target: `StationAudit` checks against the 26 measured stations in `ATA-research` (sizes, track counts).
- Blueprint preview: pick style and size, ghost over the tracks, adjust, confirm, builds over a few seconds (build on `client/StationPreview.java`, `station/world/PreviewGeometry.java`; faster batched `StationBuildJob`).
- Fine editing after build: saved per-cell override map, applied last.
- Pickers (preset, class, scale, era, tracks, platform length, roof) replace the word parser as the main input; the parser stays as an advanced box.
- New types: small halt, bus/tram stop, curved platforms. (Underground, elevated, hub, airport already exist.)
- Decoration block entities become plain blocks.
- Done when: unit tests for planner, audit and overrides; screenshots (skill `sharp`) of each type and the preview.

### PX. Passengers (launch headline)
- Finish walking, boarding and doors from F; detail levels by distance; cost sliders and a live cost read-out on the panel; off by default until Jah decides.
- Done when: frame time measured on a busy station and a full train, screenshots, tests for walking and boarding logic.

### SG. Signals and driving
- Signals whose lights come from the real train spacing and set route (2/3/4-aspect), on real-looking signal models (O2).
- Driver at launch: notched throttle and brake, door control, stop markers per train length, AWS-style warning with acknowledge, TPWS-style overspeed/red-signal trip, a simple score per run.
- Guard, dispatcher and signaller roles come after launch, not now.
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

## Later (not in this plan)
Guard, dispatcher and signaller roles; terrain movers; bike share; journey planner extras; in-game map 1d.4.
