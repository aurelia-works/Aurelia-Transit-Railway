# ATR feature batch: progress

## Goal
Implement the six approved features: custom bridge generator, finer node rotation, metallic block variants, sound-pitch decision, pluggable announcer voices, and passenger routing through lifts/stairs/escalators.
Acceptance: all six are implemented or explicitly resolved, `./gradlew :fabric:test -x runGameTest --offline` passes, the client boots without model errors, and 1.1 stays unpublished until Jah approves.

## Done
- **Finer rail node rotation** (`292431ee4`). `BlockNode` has `is_11_25`; 16 states; `rail.json` has 16 variants. Minecraft rejects 11.25° model rotations, so the marker reuses the nearest 22.5° model and the rail uses the finer angle. Client boot: no missing model errors.
- **Metallic panels** (`292431ee4`). Added `steel_panel`, `steel_panel_dark`, `steel_panel_perforated`, `steel_panel_galvanised`, `steel_panel_brushed`; textures/blockstates/models/lang. Client boot registered 274 ATA blocks, up from 269.
- **Voice packs** (`f0a4fd50d`, `2114cb5f4`). On-train next/arriving announcements use the voice-pack stack; legacy `aureliaex_ann_*` clips fall back; system TTS covers missing names. Added `docs/VOICE_PACKS.md` and on-train keys.
- **Sound pitch decision** (`da26aa028`). `docs/SOUND_PITCH_FEASIBILITY.md` records that 0–16× is unsafe; existing train-only 0.25–8 stays. A narrower opt-in 0.5–4× can be scheduled.
- **B1 custom bridge generator, first slice** (`869c6a55d`). `/atr_bridge capture` stores a bridge section as JSON under `config/aurelia/bridges`; `/atr_bridge preview` and `/atr_bridge build` stamp it along an MTR rail between two rail nodes with quarter-turn rotation, block-state rotation and a height offset; `/atr_bridge undo` restores the last build in its dimension. Work runs in 10 ms ticks. `docs/B1_CUSTOM_BRIDGE.md` records the format, commands and limits; 90° turns only, cross-sections repeat best, undo is in memory. Game proof (`20a647902`): a headless server test captures, saves/loads, samples a real rail, builds and undoes real blocks.
- **Station preset realism pass** (`9cc19b548`). Teddington is 6 wide with lifts; Modern is named and has escalators; Frankford and 69th Street no longer promise escalators they did not build; Rosenthaler Platz has escalators; West Kowloon has a glass hall roof. Station Studio presets now clear stale controls, locks and the previous planner seed. `StationPresetsTest` also requires a name, name signs and real escalators when asked. Independent Muse Spark review: **PASS, 7.6/10**, every changed preset at least 7.0. See `docs/STATION_PRESETS.md`.
- **Verification.** Earlier features: `./gradlew :fabric:test -x runGameTest --offline` -> BUILD SUCCESSFUL; `JAVA_TOOL_OPTIONS="-Datr.panelshots=true" ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"` -> BUILD SUCCESSFUL, 274 blocks, no model errors, 23 screenshots. B1: `./gradlew :fabric:test -x runGameTest --offline` -> BUILD SUCCESSFUL, including `BridgeTemplateTest` and `BridgeStampPlanTest`; `./gradlew :fabric:ataTest --offline --tests 'com.aureliatransit.architecture.bridge.world.BridgeStateCodecTest'` -> BUILD SUCCESSFUL; `./gradlew :fabric:runGameTest -Pata.gametests=BridgeGameTests --offline` -> 1 game test passed (capture, save/load, rail sampling, build, undo). Presets: `StationPresetsTest`, `StationRealismTest` and `PassengerWalkTest` -> BUILD SUCCESSFUL. AI passengers: `PassengerNetworkTest` results show 2 tests, 0 failures; the Gradle invocation itself hung after the tests and timed out, so no in-game visual passenger test exists yet.

## Next
1. **Continue B1 custom bridge generator.** First slice is in. Remaining: a clearer capture/build UX (two-corner item or structure-block style), template sections with a support interval instead of repeating piers, finer than 90° turns if the block set can support them, persisted undo, block-entity capture, and a survival material policy. Test in game: capture a real bridge cross-section, preview/build/undo on a curved rail, and check rotated stairs/panels.
2. **Grow the station preset library.** Use real-station references and images to add more presets one by one, keep the `StationPresetsTest` / `StationRealismTest` bar, and fix the Studio "Build it" button so it builds the previewed plan instead of the previous pending plan. Then apply the same quality gate to freeform Studio combinations.
3. **Then P3 passenger access.** Add a station graph using ATA stairs/lifts/escalators (`PlanStairs`, `PlanLifts`, `PlanMovingWalks`, `MtrLifts`), walking states in `PassengerNetwork`, render walkers, panel controls, tick measurement. Today AI passengers are aggregate waiting/riding counts drawn as boxes, not individual walkers.
4. **After Jah approves**, push `feat/batch-1-6` / open a PR, and separately finish the paused 1.1 release.
5. **Dashboard/map/tunnel/track scaling** from the scout report are separate phases; do not start until the six-feature branch is reviewed.

## Open questions
- Station references and images for the next presets: which real stations, which source/licence, and whether the preset catalogue should live in `StationPresets.java` or a data file.
- Custom bridge UX beyond the command: two-corner item, structure-block style capture, or keep commands for operators; whether supports should be a separate interval rather than repeated with the cross-section.
- Survival material handling for bridge/tunnel jobs: inventory only, nearby chests, or creative-only.
- Passenger graph source: saved ATA station layout vs scanning placed blocks; what to do for hand-built stations with no layout. AI passengers are currently aggregate counts, not pathfinding walkers.
- General sound pitch: keep train-only, or add the 0.5–4× opt-in.

## Key facts
- Active repo: `/Users/Boon/Downloads/PROJECTS/GITHUB REPOS/Aurelia Transit Railway/ATR-features`; branch `feat/batch-1-6`; local, 10 commits ahead of `origin/master`; not pushed.
- 1.1 release is paused in `/Users/Boon/Downloads/PROJECTS/GITHUB REPOS/Aurelia Transit Railway/ATR-rel`, branch `chore/release-1.1.0`; uncommitted `CHANGELOG.md`, `docs/release/*`, `gradle.properties`. Jar: `build/release/ATR-fabric-4.0.5+atr.1.1.0-mc1.20.1.jar`. Do not commit/push/tag it.
- `HANDOFF.md` is stale (v1.0.0 era). `HANDOFF-2026-10-31-PHASES-4-Z.md` is closer but says 9b is next; 9b merged as #40.
- Resource-pack compatibility: node marker models reuse existing `mtr:block/rail_node*`; old texture/blockstate packs still render. Three steel panels reuse `steel`, `steel_dark`, `steel_perforated`; two use new textures.
- Minecraft block-model element rotations only allow -45/-22.5/0/22.5/45; do not add other model angles.
- Build command: `./gradlew setupFiles` once per worktree; unit tests `./gradlew :fabric:test -x runGameTest --offline`; ATA tests `./gradlew :fabric:ataTest`; client screenshots `JAVA_TOOL_OPTIONS="-Datr.panelshots=true" ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"`.
- Bridge templates: operator-only `/atr_bridge` (level 2), files in `config/aurelia/bridges`, commands and limits in `docs/B1_CUSTOM_BRIDGE.md`. Build/undo are in-memory undo and only the last build per player.
- The scout report for dashboard/map/tunnel/track scaling is in the prior chat, not in a file. Key conclusions: dashboard map rescans 729 chunks every 2 s; track far tier needs section batching; tunnel bulk edits run on the server tick; embedding AureliaMap is not practical, use a cached 2D tile map + external 3D link.
