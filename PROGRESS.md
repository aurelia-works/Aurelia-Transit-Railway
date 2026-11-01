# ATR feature batch: progress

## Goal
Implement the six approved features: custom bridge generator, finer node rotation, metallic block variants, sound-pitch decision, pluggable announcer voices, and passenger routing through lifts/stairs/escalators.
Acceptance: all six are implemented or explicitly resolved, `./gradlew :fabric:test -x runGameTest --offline` passes, the client boots without model errors, and 1.1 stays unpublished until Jah approves.

## Done
- **Finer rail node rotation** (`292431ee4`). `BlockNode` has `is_11_25`; 16 states; `rail.json` has 16 variants. Minecraft rejects 11.25° model rotations, so the marker reuses the nearest 22.5° model and the rail uses the finer angle. Client boot: no missing model errors.
- **Metallic panels** (`292431ee4`). Added `steel_panel`, `steel_panel_dark`, `steel_panel_perforated`, `steel_panel_galvanised`, `steel_panel_brushed`; textures/blockstates/models/lang. Client boot registered 274 ATA blocks, up from 269.
- **Voice packs** (`f0a4fd50d`, `2114cb5f4`). On-train next/arriving announcements use the voice-pack stack; legacy `aureliaex_ann_*` clips fall back; system TTS covers missing names. Added `docs/VOICE_PACKS.md` and on-train keys.
- **Sound pitch decision** (`da26aa028`). `docs/SOUND_PITCH_FEASIBILITY.md` records that 0–16× is unsafe; existing train-only 0.25–8 stays. A narrower opt-in 0.5–4× can be scheduled.
- **Verification.** `./gradlew :fabric:test -x runGameTest --offline` -> BUILD SUCCESSFUL (all unit tests). `JAVA_TOOL_OPTIONS="-Datr.panelshots=true" ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"` -> BUILD SUCCESSFUL, 274 blocks, no model errors, 23 screenshots.

## Next
1. **Start B1 custom bridge generator.** The “from tk” requirement is a template engine, not just supports/walls. Capture a custom bridge section, store it, sample a rail, stamp the template with rotation/height, preview and undo. Likely use `StructureTemplate`; keep `ItemBridgeCreator` for simple block bridges. This needs its own plan/commit.
2. **Then P3 passenger access.** Add a station graph using ATA stairs/lifts/escalators (`PlanStairs`, `PlanLifts`, `PlanMovingWalks`, `MtrLifts`), walking states in `PassengerNetwork`, render walkers, panel controls, tick measurement.
3. **After Jah approves**, push `feat/batch-1-6` / open a PR, and separately finish the paused 1.1 release.
4. **Dashboard/map/tunnel/track scaling** from the scout report are separate phases; do not start until the six-feature branch is reviewed.

## Open questions
- Custom bridge capture UX: command-based, structure block, or a new two-corner item; where templates live (`config/aurelia/bridges` vs datapack `structures/`).
- Survival material handling for bridge/tunnel jobs: inventory only, nearby chests, or creative-only.
- Passenger graph source: saved ATA station layout vs scanning placed blocks; what to do for hand-built stations with no layout.
- General sound pitch: keep train-only, or add the 0.5–4× opt-in.

## Key facts
- Active repo: `/Users/Boon/Downloads/PROJECTS/GITHUB REPOS/Aurelia Transit Railway/ATR-features`; branch `feat/batch-1-6`; local, 4 commits ahead of `origin/master`; not pushed.
- 1.1 release is paused in `/Users/Boon/Downloads/PROJECTS/GITHUB REPOS/Aurelia Transit Railway/ATR-rel`, branch `chore/release-1.1.0`; uncommitted `CHANGELOG.md`, `docs/release/*`, `gradle.properties`. Jar: `build/release/ATR-fabric-4.0.5+atr.1.1.0-mc1.20.1.jar`. Do not commit/push/tag it.
- `HANDOFF.md` is stale (v1.0.0 era). `HANDOFF-2026-10-31-PHASES-4-Z.md` is closer but says 9b is next; 9b merged as #40.
- Resource-pack compatibility: node marker models reuse existing `mtr:block/rail_node*`; old texture/blockstate packs still render. Three steel panels reuse `steel`, `steel_dark`, `steel_perforated`; two use new textures.
- Minecraft block-model element rotations only allow -45/-22.5/0/22.5/45; do not add other model angles.
- Build command: `./gradlew setupFiles` once per worktree; unit tests `./gradlew :fabric:test -x runGameTest --offline`; client screenshots `JAVA_TOOL_OPTIONS="-Datr.panelshots=true" ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"`.
- The scout report for dashboard/map/tunnel/track scaling is in the prior chat, not in a file. Key conclusions: dashboard map rescans 729 chunks every 2 s; track far tier needs section batching; tunnel bulk edits run on the server tick; embedding AureliaMap is not practical, use a cached 2D tile map + external 3D link.
