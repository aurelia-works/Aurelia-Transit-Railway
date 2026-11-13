# ATR plan to V1

Current as of 2026-11-13. The brief for the run to V1 is Jah's `ATR-GOAL.md`, which is not in the repo. Old handoffs are in `docs/handoffs/`; the long per-phase notes of finished phases are in this file's git history. What was removed in the 2026-11-13 cleanup, and why: `AUDIT.md`.

## What ATR is
- A drop-in replacement for MTR 4.0.5 on Minecraft 1.20.1 Fabric: mod id `mtr`, block and item ids, the save format and the 4.0.5 methods add-ons hook (`DropInSignaturesTest`) never change. 1.20.1 only.
- Generators for stations, airports, bridges, earthworks and rails. Stations and airports should look like real ones, not like valid block plans.
- Light: it has to run well on old PCs.
- Audience: Jah's Aurelia Metropolis server first, then public (Modrinth, CurseForge, GitHub). Public launch only after the in-game test pass (T) and the trailer (TR).

## Rules for every phase
- Anything visible ships at 9/10 or better, judged from same-camera before and after shots by the builder and by a blind reviewer. A PR lists what keeps each part under 9.5.
- Every visible feature gets its switches and sliders on the dev panel (Station Studio, the wrench screens). Heavy features can be turned off and drop detail with distance.
- Decoration uses plain blocks. Block entities only where something changes live (PIDS, departure boards, clocks, gates).
- Branded liveries live in the separate ATR Liveries pack. Jah's Metropolis material is private and never shipped.
- New saved data goes under new keys or in new files, so old worlds load unchanged.
- One phase per branch and PR. Merge commits, never squash. A PR merges after green CI (`ci-ok`) and a PASS from a fresh `verify-pr` reviewer on its head commit.
- Creative and op players only for the generators.

## Direction (Jah, 2026-11-08)
- The headline is the in-game station and airport editor: a 2D plan and a 3D pane from the wrench, freehand tracks, platforms and buildings, with the generator filling in the detail.
- Station Studio (browser) and the game read and write the same station file. Studio is where a station is planned; the game builds the finished plan.
- One wrench with modes (Station, Airport, Bridge, Earthworks) and one ATR menu. The old wrench items still load.
- Builds go anywhere and every build can be undone, also after a restart. Big jobs build over time.
- The preview is a hologram of the real blocks.

## Phases

| # | Phase | Status |
|---|---|---|
| F, R, S, AP, PX, SG, SN, O2, RT, PK | Foundations, rendering, station and airport generators, passengers, signals and driving, sound, art, route control, private packs | merged (#47 to #62). R: only the one-train bench is measured, see `docs/perf-launch.md` |
| W, H, UI, S2 | One wrench and menu, hologram preview, menus that fit, station connections | merged (#67, #68, #71, #72) |
| FG | Fare line: readers that block until a tap | merged (#73). Open: at a grand station the wing waiting rooms open onto both the hall and the platform, so the line can be walked round |
| AP2 | Airfield one block into the ground, A320 and A350 models | merged (#86) |
| E1, E2, E3 | Editor screen, drawn tracks as MTR rails, drawn platforms and buildings | merged (#76, #78, #83). E1 and E2 have not been tested in game |
| EW1, EW2 | Saved undo for every build, earthworks | merged (#66, #77) |
| AE | Airports in the editor | merged (#92, terminal buildings #95, curved taxiways #97) |
| L1, L2, L3, L4 | Crew roles, drivable cars and bike share, journey planner, in-game map | merged (#79, #91, #93, #80, #81) |
| DM | Train-door markers on platforms | merged (#98) |
| BS | Bus station at the station front | merged (#99) |
| E3+ | Street entrance, hall, gates and wing waiting rooms | merged (#96) |
| ES | Escalator look | Studio draws them right (#103). Three looks are on the check-in page; waiting for Jah's pick |
| V1 to V4 | Viaduct that follows the rail, overhead-line masts, suspension span, tunnel portal | not started. Jah approved this order on 2026-11-12 |
| AR | Airport train station, one walk from platform to check-in | not started |
| SC | Station size classes: halt, local, hub, terminal | not started |
| JB | Jet bridges from the gates to the parked planes | a pier cab that extends to a stopped plane exists (#26). Jah asked on 2026-11-11 for a bridge at every contact stand; not checked against that yet |
| AF | Airport passenger flow: check-in, security, shops, gates, baggage reclaim | not started |
| SR | Staff rooms behind locked doors | not started |
| BG | Bike garage under the station square | not started |
| PF | One shared, versioned plan format and a planning orchestrator in Studio (`ATR-GOAL.md` step 4) | not started |
| LV | ATR Liveries pack: NS SLT rebuild, then the other trains | in progress in the ATR-Liveries repo (350 LNR, 450 SWR, VIRM done) |
| T0 | Guided look with Jah at the picks-round work | not done |
| T | Full in-game test pass, two players | not started |
| TR | Trailer; editor and earthworks construction shots lead | recorder (#58) and scripted shots (#63) merged; trailer not started |

## Open phases

### V1 to V4. Bridges
- V1: a viaduct that follows the rail as laid, curves included: a smooth box-girder deck, square piers on footings, an in-game preview.
- V2: overhead-line masts with arms and wire.
- V3: a suspension span: towers with red lights, the main cable, hangers and railings.
- V4: a tunnel portal in the cliff.
- Each gets dev-panel sliders and a 9/10 on shots. One PR each.

### V1 additions (Jah, 2026-11-11)
- AR: an airport plan can carry a rail station under the terminal or beside the landside road, its exit leading straight to check-in. The station planner builds it as a normal station. Done when: one walk from platform to check-in in a shot sequence.
- SC: the planner picks a class from the spec. A halt gets a shelter and a ticket machine; a hub or terminal gets the full building, concourse and extras. Done when: one shot per class, side by side.
- JB: each contact stand gets a jet bridge from its gate to the plane's door height. Done when: a shot of a parked A320 at a bridge.
- AF: the terminal interior is laid out in walking order: check-in hall, security lanes, airside shops, gate lounges; arrivals through baggage reclaim to landside. Done when: a shot walk through the terminal in that order.
- SR: every station building gets a staff-only area behind a locked door, out of the passenger route. Done when: a shot of the door and rooms.
- BG: hub and terminal stations get a bike garage with racks in rows, a ramp or stair down and an entrance from the square. Done when: a shot of the garage.

### PF. Plan format and Studio orchestrator
- Studio and the game use one versioned plan format, so the Studio preview is exactly what gets built. Old saved designs still load.
- A planning orchestrator in Studio turns a request in plain words into a plan by calling the deterministic planners in turn (layout, track geometry, style, circulation, accessibility, validation), shows where they conflict and reruns only the part that changed. The AI only reads the request and proposes; Studio works fully without it.

### LV. ATR Liveries
- Gaps no 1.20.1 MTR 4 pack covers: NS VIRM and SLT; UK Class 350/450, 153/156/170, 321/507/508; London Underground S Stock and 2024 Stock; Stepford County Railway classes. Models and textures are built in Blockbench, each with a harsh reviewer pass and a sound profile. Jah reviews each train.

### T. Test pass
- Everything shipped since v1.0.2 checked in game, including two players: pantographs, Station Studio, sitting, chimes and pitch, timetables, passengers, zip lines, the Aurelia UI and every phase above. E1 and E2 first. Bug list cleared.

### TR. Trailer
- The dev-only camera recorder makes the clips; it never ships in the release jar. A bench world with ATR-built stations, clips of every headline, and the measured numbers from `docs/perf-launch.md` on screen.
