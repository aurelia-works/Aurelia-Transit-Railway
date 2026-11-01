# Check-in: phases 4 to Z

One card per phase merged since the phase 4 work, plus two still in progress. Sources: `docs/handoffs/HANDOFF-2026-10-31-PHASES-4-Z.md`, `PLAN.md`, and the pull request descriptions. Nothing in this page was played in game by the people who merged these changes. The handoff says none of it was seen or heard.

The v1.0.2 jar is the tag on #37. #38 and #39 landed after that tag, so they are not in `ATR-fabric-4.0.5+atr.1.0.2-mc1.20.1.jar`. Later merges (#40, #42, #44, #45) are also after the tag.

## Known MTR bug (not fixed)

`ScheduledSound.play()` plays its own sound once per scheduled entry. Spotted while working on sound. Left as it is.

---

## Phase 4 — shortlist bugs

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/34
- **Merge SHA:** `07efa5a849c3e2b78957fec973c375443d649db4`
- **Status:** merged. Handoff: reviewer PASS, CI green.

**What changed.** The bugs in `docs/phase-4-shortlist.md`, items 1–9. Results are in `docs/phase-4-bugs.md`. Item 10 (lift rows) had already shipped as #15.

- PIDS show a route's edited destination without a path refresh (`LiveDestinationMixin`, `LiveDestination`).
- Replacing a rail node block deletes its rails. Breaking a node deletes them once, not twice (`BlockNode.onStateReplaced2`).
- A long low-FPS step out of a doorway no longer leaves the player inside the car (`RidingStep`). Lifts use the same step.
- TTS announcements stay silent when Master or Voice volume is 0 (`TtsVolume`). Volumes between 0 and 100% still speak at the system volume, because the narrator API has no volume argument.
- Items 1, 2, 5 and 8 were closed with evidence. Item 9 was measured: idle server heap stayed flat within 0.2% over 12 minutes. The client, and a server with trains, were not measured.

**Evidence.** Failing first, then passing: `LiveDestinationTest`, `RidingStepTest`, `TtsVolumeTest`, and the game test `RailNodeGameTests.railsGoWhenANodeIsReplaced`. `DropInSignaturesTest` passes. A game-test probe confirmed the mixin at runtime and was removed. PR checks all passed: `build`, `ata-unit` (1/3–3/3), `ata-slow-unit` (CityLandmarks, RealCitiesA, StationPlanner), `ata-gametest` (AirportMtrRebuild and the rest). PIDS, riding and TTS were not checked in a real client.

**In the test world**

- [ ] Edit a route destination and read a PIDS without refreshing the path.
- [ ] Replace a rail node (`/setblock` or `/fill`) and confirm its rails go. Break a node by hand and confirm the rails go once.
- [ ] Step out of a train, and a lift, at low FPS. You should land where the step took you, not inside the car with no floor.
- [ ] Mute Master or Voice and confirm TTS stays silent. Partial volume still speaks at system volume.

---

## Phase 6 — depot controls

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/35
- **Merge SHA:** `0193a14f5c725cdb363104d6214fe54157dc4830`
- **Status:** merged. Handoff: reviewer PASS, CI green.

**What changed.** Depot controls on the Depot Health screen of the Aurelia panel.

- Instant deploy and Clear vehicles (`MtrBridge`).
- Per-depot **All doors** and **Announcements**, saved in their own file `atr_depot_options`. Clients get them from `DepotOptionsSync`. Creative and survival players can change them.
- All doors ORs the switch into `canOpenDoors`, so every doorway opens, including ones MTR would keep shut. That is by design.
- Announcements off silences MTR's chat/narration announcement and the Aurelia announcer for that depot's trains.
- A "path last refreshed" line.
- Old worlds have no options file, so every switch starts off. `config/mtr.json` is untouched.

**Evidence.** `DepotOptionsTest`: save round trip, and switching both off forgets the depot. `DropInSignaturesTest` passes. The `RenderVehicles` change stays after MTR's call so the lambda capture order stays the same. Same CI set as #34, all pass. Buttons and toggles were not clicked in a real client.

**In the test world**

- [ ] Instant deploy and Clear vehicles from the depot screen.
- [ ] All doors: doorways MTR would keep shut also open.
- [ ] Announcements off: that depot's trains stay quiet (MTR announcement and the Aurelia announcer).
- [ ] "Path last refreshed" shows a time and date.
- [ ] Restart. The switches are still there.

---

## Phase 8 — sound

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/36
- **Merge SHA:** `9b8435c0d51d3db31e46e01f94bf811425426a1a`
- **Status:** merged. Handoff: reviewer PASS, CI green. Flange squeal on curves had already shipped in #20.

**What changed.** A **Train sound** tab. Settings are new keys in the client config.

- **Wide pitch range** (on by default) and **Highest train pitch** (2–8). A client mixin on `SoundSystem.getAdjustedPitch` lets `mtr` sounds use pitch 0.25 up to that maximum, instead of vanilla's 0.5–2. BVE motor tables that ask for more than 2 play as written.
- **Motor pitch bend** (0–40%, 0 = off). The handoff records the shipped default as 15%, so existing trains sound different. Old-style (non-BVE) trains bend the pitch across each speed step. Rounded to 0.01 so the scheduler still merges cars.
- **Door chime** (Off / Two-tone / Three-tone) and volume. Plays once per train as the doors leave fully open. Two new `.ogg` files, synthesized with ffmpeg. Default Off, which matches MTR.
- **Curve squeal volume** (0 = off).
- Both wide pitch and the motor bend can be switched off. Not done: a per-train chime choice, and a squeal speed threshold.

**Evidence.** `SoundTuningTest`: a pitch over 2 is kept; motor pitch rises across a step and holds at the top; the moment the doors start closing is detected. `DropInSignaturesTest` passes. The refmap resolves `getAdjustedPitch` to `method_4849`. Same CI set as #34, all pass. No client was started. Chimes, pitch range and motor bend have not been heard.

**In the test world**

- [ ] Listen to an existing train with wide pitch on (default) and motor bend at 15%. It will not sound like stock MTR. Turn both off and compare.
- [ ] A BVE motor that asks for pitch above 2.
- [ ] Door chime, two-tone and three-tone, once as the doors start to close. Off stays silent.
- [ ] Curve squeal volume, including 0.
- [ ] Pitches over 2 on OpenAL Soft. Some packs will sound higher until the switch is off.

---

## Phase 5 — release 1.0.2

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/37
- **Merge SHA:** `0ee679f61955aae25b2d10bcdfa5ea7b6350f783`
- **Status:** merged. Tag `v1.0.2` is on this commit. The release jar is `ATR-fabric-4.0.5+atr.1.0.2-mc1.20.1.jar`.

**What changed.** Version `4.0.5+atr.1.0.2` in `gradle.properties`. CHANGELOG 1.0.2 covers #19 to #29, plus #32 and the phase 4 fixes (#34). Store text in `docs/release/` (GitHub, Modrinth, CurseForge) says 1.0.2 and keeps the note that real-world station and operator looks are included. No code changes. Modrinth and CurseForge are not published: the repo has no tokens. `PLAN.md` still says those two stores are waiting for a test.

**Evidence.** CI builds the jar. Same CI set as #34, all pass on the PR. After merge, `release.yml` was meant to publish the GitHub Release from the tag.

**In the test world**

- [ ] The installed jar reports `4.0.5+atr.1.0.2`.
- [ ] The GitHub Release `v1.0.2` has `ATR-fabric-4.0.5+atr.1.0.2-mc1.20.1.jar`.
- [ ] Anything from #38 onward is missing from that jar. Those need a later release.
- [ ] Modrinth and CurseForge pages are still unpublished until you publish them.

---

## Phase 7 — timetables and cancellations

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/38
- **Merge SHA:** `992ba4904d521289bdd4b4197b4d6798dc802da1`
- **Status:** merged. Not in the v1.0.2 jar.

**What changed.** Weekly timetable per depot on the Depot Health screen, and cancelled runs.

- "Save as weekday" and "Save as weekend" store the depot's current MTR timetable (departures or hourly frequencies). A toggle turns switching on.
- Real-time depots follow the real calendar (Saturday and Sunday are the weekend). Other depots use world days: days 6 and 7 of each 7 are the weekend.
- Every second, the server writes today's plan into the depot when the day type changes. Suspended depots are skipped. When switching is on, this overwrites that depot's MTR timetable with the saved plan.
- Cancel next run / Restore runs. A cancelled departure does not leave the depot (`CancelledRunMixin` on `Siding.matchDeparture`). Arrivals read "Cancelled" on PIDS, rewritten inside `LiveDestinationMixin`'s single handler so it does not fight the #34 destination fix. A cancellation expires once its time has passed.
- Saved in `atr_depot_schedules`. MTR's depot data format is untouched. Reverting leaves depots on whatever timetable was last written.

**Evidence.** `DepotSchedulesTest`: day type, weekend falling back to the weekday plan, save round trip, a cancellation lasting only until its time. `LiveDestinationTest` and `DropInSignaturesTest` pass after the rebase onto #35. Game-test boot `RailNodeGameTests` passes with no mixin errors. An earlier probe confirmed both Siding mixins merge. Same CI set as #34, all pass. Weekday/weekend switching over a real day change, and "Cancelled" on a real PIDS, were not tested in game.

**In the test world**

- [ ] Save weekday and weekend plans, turn switching on, and watch the depot follow the day type (real calendar, or world days 6 and 7).
- [ ] A suspended depot does not get its plan rewritten.
- [ ] Cancel the next run. The train stays in the depot. PIDS says "Cancelled". Restore runs brings it back.
- [ ] After the departure time, the cancellation expires.
- [ ] Restart. `atr_depot_schedules` still has the plans.

---

## Phase 9a — passengers (simulation)

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/39
- **Merge SHA:** `cd25a9eb7fcd4e6e2620a608837619a00f7b2544`
- **Status:** merged. Not in the v1.0.2 jar. Both server registrations were kept when this conflicted with #38.

**What changed.** Passengers are plain records, not mobs. Design and the cost table are in `docs/passengers-9a.md`.

- They appear on platforms that routes serve, each heading for a station reachable on one of those routes.
- They board a stopped, on-route train whose route goes to their station: 3 per car per second, up to the seats.
- They get off at their station or at the train's terminus, and give up after 10 minutes.
- Deleted trains and platforms take their passengers with them.
- `PassengerService` runs once a second on each dimension's MTR simulation thread. Caps live in `atr_passengers.dat`. Caps, counts and the measured cost go to every player.
- Aurelia panel, Passengers tab: on/off (default **off**), new per platform per minute, most waiting per platform, most in the world, seats per car, and a live line (waiting, riding, boarded, got off, server cost). Only operators (permission level 2) can change it.
- Passengers are not saved. A restart empties platforms.

**Evidence.** `PassengerNetworkTest`: A-B-C line (board at A; B riders leave at B, the rest at terminus C); platform and world caps; a full car stops boarding; a deleted train or platform removes its passengers; passengers who give up go home. `DropInSignaturesTest` passes. Game-test server boot `RailNodeGameTests` passes with the service registered. Cost, a one-off synthetic run that was not committed: 0.10–0.18 ms average per update and 2.4 ms worst, for 50–1,000 platforms, 20–400 trains and 20,000 passengers. Same CI set as #34, all pass. A real world with trains was not tested.

**In the test world**

- [ ] Passengers stay off until you turn them on. An old world should run as before.
- [ ] Turn them on. Watch people board and leave. The Passengers tab shows the live cost.
- [ ] Caps: per platform, world, seats per car.
- [ ] Restart. Platforms are empty. The caps in `atr_passengers.dat` are still there.

---

## Phase 9b — passengers (drawing)

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/40
- **Merge SHA:** `129e53d2421468858cbf60977048fec71d99b160`
- **Status:** merged. The earlier branch `feat/passengers-draw` (`87c556db`) only sent counts and was left as it is. This merge is `feat/passengers-draw-2` from master, with that commit cherry-picked onto the squashed #39.

**What changed.** Waiting and riding passengers are drawn as boxes. No mobs.

- The server still sends waiting-per-platform and riding-per-train counts when passengers are on, and empty maps when they are off.
- Waiting figures stand along the platform. Ends are `SavedRailBaseSchema.position1` and `position2`, read by reflection. If those fields are missing, the figure stands at `getMidPosition()`.
- Riding figures use `Vehicle.getVehicleCarsAndPositions()`, split across cars on the bogie centerline. Past the detail distance, one figure at the head stands in for the train.
- Blue boxes wait. Orange boxes ride. Each box is 0.44 wide and 1.5 tall. Platform figures sit 0.9 blocks off the rail.
- LOD: nothing past the draw distance, one figure in the middle band, up to the cap up close. A frame stops at 256 boxes.
- Panel switches (anyone can change them; they are not sent to the server) live in `config/aurelia/passenger-draw.json`. Defaults: draw **off**, draw distance 32 (0–128, step 8), detail distance 12 (0–64, step 4, never farther than the draw distance), most drawn in one place 8 (0–40). `config/mtr.json` and `atr_passengers.dat` are not touched.
- Frame time was not measured. Debug lines can bother a shader pack when drawing is on. Default is off for that reason.

**Evidence.** `./gradlew setupFiles :fabric:test -x runGameTest --offline --tests org.mtr.aurelia.passenger.PassengerFiguresTest --tests org.mtr.test.DropInSignaturesTest` — `BUILD SUCCESSFUL`. `PassengerFiguresTest`: nothing past 32 blocks, one figure in the middle band, cap of 8 up close, fewer people than the cap, draw distance 0, platform figure 0.9 off the rail, car figure on the centerline, empty json equals the defaults, detail distance cannot exceed draw distance. `./gradlew setupFiles build -x test` — `BUILD SUCCESSFUL` (jar `:fabric:remapJar` into `build/release/`; that build was before the last centerline tweak; the unit-test run after the tweak compiled `PassengerRenderer` again). Same CI set as #34, all pass. Boxes have not been looked at in a world.

**In the test world**

- [ ] Turn passengers on (9a) and drawing on. Blue boxes on a platform, orange boxes in a train.
- [ ] Draw distance, detail distance, and the per-place cap. Past the draw distance, nothing. In the middle band, one figure.
- [ ] A shader pack with drawing on. Note frame time against drawing off.
- [ ] If boxes clip the car, they are on the bogie centerline, not a seat.
- [ ] Missing `position1` / `position2` stacks figures at the platform midpoint.

---

## Phase Z — zip lines

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/42
- **Merge SHA:** `5599fda3866e44ea789d6b58632342a8382cff35`
- **Status:** merged. Concrete plan is the **Z. Zip lines** section of `PLAN.md`. Creative mode and operators (permission level 2) only. Survival recipes are out of this phase.

**What changed.** A cable between two poles. The cable is not a block. No new entity: the rider is teleported, and a seat is a point on the cable.

- `mtr:zip_pole`: place two, then use the pole item on the first and the second to link them. Span 4–96 blocks. One cable per pole. At most 64 cables. Breaking either pole removes the cable.
- `mtr:zip_handle`: use it on the upper pole of a gravity line. You slide down the sag curve, capped by the panel speed, slowed by the panel brake, and you are set down beside the lower pole. Sneak lets go. A flat or uphill start is refused. A drop of at least 0.5 blocks is required.
- `mtr:zip_seat`: use it on a pole to make that line two-way. Right-click the pole the seat is waiting at to ride to the other pole. The seat stops on the pole. It waits until someone rides it back. The seat item is consumed outside creative.
- Panel tab **Zip lines**: enable, speed (1–30 blocks/s, default 12), brake (0–2, default 0.4), sag (0–0.25, default 0.06). Greyed out below permission level 2. Turning it off drops current riders. Cables stay saved and are not drawn.
- World file `atr_zip` only. Riders are not saved. `config/mtr.json` is untouched. An old world with no file has no cables.
- Cables are debug lines: not drawn past 96 blocks, 4 segments from 48 to 96, 12 inside 48. A seat is one box under the cable. Handle and seat items reuse a tripwire hook and an oak boat.

**Evidence.** `./gradlew setupFiles :fabric:test -x runGameTest --offline --tests org.mtr.test.ZipMathTest` — `BUILD SUCCESSFUL`. `ZipMathTest`: 2 tests, 0 failures (`sagCurveStaysOnThePolesAndHangsInTheMiddle`, `downhillSpeedsUpAndASeatStopsAtThePole`). `DropInSignaturesTest` was not part of that filtered run. Same CI set as #34, all pass. No client was launched. No game test.

**In the test world**

- [ ] Place two poles and link them. Span under 4 or over 96 is refused. A second cable on the same pole is refused.
- [ ] Handle from the upper pole: sag, speed cap, brake, set down beside the lower pole. Sneak lets go. A flat start is refused.
- [ ] Seat both ways. It stops at the far pole and waits.
- [ ] Second player on a busy cable is told the line is in use.
- [ ] Break a pole while someone is riding.
- [ ] Panel switches (operator). Off drops riders and hides cables. Restart: cables remain, the ride does not resume.
- [ ] Cable LOD and a shader pack (the line layer may vanish).
- [ ] An old world with no `atr_zip` file loads as before.

---

## Phase O3 — building tools

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/44
- **Merge SHA:** `345586cca3cc9c8ac3328ca4dddca722cc2db2d3`
- **Status:** merged. Concrete plan is the **O3. Building tools** section of `PLAN.md`. Creative and operators only. Existing tunnel creators and rail connectors stay. Screens are not restyled.

**What changed.** Three new items and a **Building** tab.

- `mtr:station_guide`: two corners, sneak-right-click to confirm. The server creates an MTR station column (every height) through the existing update path. A station that already covers the footprint is left alone. The side is capped by the panel.
- `mtr:rail_stylus`: right-click a train rail node to start. The next click places another node of the same block along the snapped heading and connects one 80 km/h two-way quadratic rail. Sneak starts again. Slope at most 8 blocks. Reach is a slider. It does not open the rail style screen, and it only accepts train nodes.
- `mtr:box_tunnel`: two corners, sneak-right-click to confirm. Modes: full dig, cut above the floor, or a stone-brick shell with air inside. One job server-wide, a few blocks per tick. Sneak-use in the air undoes the last job, including one still running. Bedrock and barriers are skipped. Each side at most 48. Block count within the panel maximum (default 512, at most 4096).
- Each tool refuses to run when its panel switch is off.
- World file `atr_build`. In-progress corner marks are not saved. Undo entries already written are saved. `config/mtr.json` is untouched.
- Preview is ordinary lines (footprint, box, eight samples of the rail curve), skipped past 96 blocks.
- Item models reuse vanilla textures (recovery compass, iron pickaxe, golden shovel).

**Evidence.** `./gradlew setupFiles :fabric:test -x runGameTest --offline --tests org.mtr.test.BuildMathTest` — `BUILD SUCCESSFUL`. `BuildMathTest`: 2 tests, 0 failures (`volumeStaysInsideTheCapAndCornersMatchTheIndex`, `snapAimAndCurve`). Sources live in `org.mtr.mod.building` because a directory named `build` is gitignored. Same CI set as #34, all pass. No client was launched. No game test.

**In the test world**

- [ ] Station guide: two corners, confirm, a station column appears. A second confirm on the same ground is left alone.
- [ ] Rail stylus on a train node: node, 80 km/h two-way rail, snap (off / 15° / 45° / 90°), reach, and the 8-block slope cap. A non-train node is refused.
- [ ] Box tunnel: full dig, cut above the floor, stone-brick shell. Bedrock and barriers stay. A second confirm while a job runs is told to wait.
- [ ] Sneak-use in the air undoes the last job, including after a relog (marks are gone; saved undo remains).
- [ ] Each tool does nothing when its switch is off.
- [ ] An old world with no `atr_build` file loads as before.
- [ ] Line preview with a shader pack, and the Building tab on a short window.

---

## Phase O2 — blocks, items, rails, signals (art plan)

- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/45
- **Merge SHA:** `15739e57e9c40f2badb7cccc025e7508856c8555`
- **Status:** merged. Docs only. No png, model, pack, config key, or panel row. Stop point: **Jah reviews art.**

**What changed.** `PLAN.md` has a concrete O2 section: style guide, an inventory counted on `master` `345586cca`, how a builtin pack would ship later, and the owner's drawing steps. The files under `assets/mtr/` stay byte-for-byte. Ids stay `mtr:<same>`. Trains, gangways, sounds and fonts stay MTR (62 vehicle pngs, 44 gangway pngs, 43 bbmodels, 213 vehicle property jsons, 1703 ogg files).

Style, from AureliaMap's black theme: `#000000` / `#111111` ground, `#1a1a1a` / `#262626` panel, `#3a3a3e` metal with `#8a9098` crown, text `#dddddd`, muted `#888888`, accent `#00489d` for the Aurelia mark only. 16×16 cubes and inventory icons. 32×32 for PSD doors, APG glass, signal heads and lift doors. Rails stay a tiled OBJ (`repeatInterval` 0.5). Paint a lighter top face. Do not bake shadows.

Counts under `assets/mtr/`: 195 blockstates, 578 block models, 390 item models, 258 block pngs, 157 item pngs, 4 rail files, 7 block `.mcmeta`, 3 item `.mcmeta`.

| Priority | What | blockstates |
|---|---|---:|
| P1 rails | Rail mesh, node models, rail tools | 1 |
| P1 signals | Heads, semaphores, poles, coloured connectors | 11 |
| P2 | Platforms, PSD, APG, tickets, escalators, lifts, ceilings, PIDS, sign frames | 86 |
| P3 | Glyphs, flowers, materials, glass | 84 |
| P4 | Nodes, hand tools, Aurelia items from Z and O3 | 13 |

Later, after accepted art (not in this PR): pack id `aurelia_look`, pack format 15, builtin TOP, off until the art exists. Client key `aureliaLook` on the generated client schema. Missing key means false, so today's installs keep MTR's look. Rendering tab toggle "Aurelia block look". Default flips to true for new installs only after checkpoint 3 is accepted.

**Evidence.** Docs only. No game launch, no unit tests, no resource-pack reload. `DropInSignaturesTest` was untouched because no Java changed. Same CI set as #34, all pass.

**Owner's art steps**

- [ ] Style board first: the eight swatches, plus one mock with a rail crown, a platform edge, a PSD door and a three-aspect signal. No further drawing until that board is accepted.
- [ ] Blockbench: import the current json (File, Import, Minecraft Java Block/Item). Reskin first. Remodel only when the silhouette is the MTR look (signal housing, platform edge, PSD top). Keep texture path keys `mtr:block/...`. Do not change blockstate property names. Do not stick a model out of the collision box. Export into `resourcepacks/aurelia_look/` at the same relative path. Do not edit files under `assets/mtr/`.
- [ ] Signal connectors: edit `textures/item/signal_connector.png` only. The dye layer stays vanilla.
- [ ] Blender, rails only: import `models/rail/rail.obj` and `rail_siding.obj` (Metasequoia; note the axis). Keep the mesh length `repeatInterval` 0.5 expects. Paint `rail.png` and `rail_siding.png` in place, same size and UV, or ship a new OBJ with them. Do not edit `mtr_custom_resources_template.json` unless the model path changes.
- [ ] P3 materials are a recolor of the seven source pngs, not one drawing per variant. Glyphs (`textures/block/sign/`) stay MTR until a later pass.
- [ ] P1 in the pack, still default-off: screenshots flat, then with the shader pack you actually use.
- [ ] P2 station shell: platform, PSD, APG, a lift, an escalator, a ceiling, one PIDS. Same two lighting setups. Copy PIDS and escalator `.mcmeta` with the png.
- [ ] Say whether glyphs, flowers and materials are in or stay MTR.
- [ ] Only after that: the agent wires the pack, the `aureliaLook` key, and the toggle. Default stays off until checkpoint 3 is accepted.

---

## O1 — Aurelia UI kit (in progress)

- **Status:** in progress.
- **Branch:** `feat/o1-ui-kit` (head `c72fb76cdef75be172d72cd00a291e029d5ea11f` when this page was written).
- **PR:** https://github.com/aurelia-works/Aurelia-Transit-Railway/pull/43 (open, not merged). No merge SHA.
- **Plan:** `PLAN.md` launch overhaul. Dashboard, station/route/depot/siding editors, options, and the Aurelia panel. Screenshot per screen. Style switches on the panel. Mod id, block and item ids, the save format, and the methods add-ons hook stay. The handoff (2026-10-31) still listed O1 as not started; the open PR is later than that note.

**In the test world** (once it merges)

- [ ] Dashboard, station, route, depot and siding editors, options, and the Aurelia panel in the new look.
- [ ] The style switches on the panel.
- [ ] An old world and an add-on pack still load.

---

## 1d.4 — in-game map (in progress)

- **Status:** in progress.
- **Branch:** `feat/1d4-ingame-map`.
- **PR:** none. No merge SHA.
- **Plan:** `PLAN.md` lists 1d.4 as "In-game map (Xaero replacement)", its own plan after AureliaMap M6, and not decided when the 1d.3 decisions were written. The 2026-10-31 handoff listed it as not started. When this page was written, GitHub had no branch named `feat/1d4-ingame-map`.

**In the test world** (once it exists)

- [ ] The in-game map opens and shows the network you already have.
- [ ] Whatever on/off and style switches the phase plan puts on the panel.
