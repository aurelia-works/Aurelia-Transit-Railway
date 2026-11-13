# ATR audit, 2026-11-13

Audited `origin/master` at b7cc50de4. Nothing was changed while auditing.

How it was checked:
- Class reachability: `jdeps` over the compiled main classes, starting from the Fabric entrypoints, the four mixin configs and every class name written in a resource file. Classes that exist in the MTR 4.0.5 jar were never treated as dead, because add-ons may call them.
- Method reachability: `javap -v` over all 1846 main class files. A method counts as unused when no constant pool in main refers to its name and descriptor, it has no annotation, and its name is written nowhere else in `fabric/src` (tests, game tests, perf bench and camera included).
- Jar contents: the jar from master's CI run 43292002766 and the published v1.0.2 jar, against the MTR 4.0.5 jar.
- CI times: the last 40 runs of `ci.yml`.

## Summary
- The Java code has little dead weight: 1119 top-level classes, 2 unreachable, 64 unused methods (34 of them `*ForTest` hooks). The weight is in files inherited from upstream that ATR never builds or links: 74.7 MiB of 220.9 MiB tracked (`git ls-tree -r -l`).
- CI is already path-aware and quick (#102): about 4 minutes wall, 40 runner-minutes, 0.2 minutes for a docs-only PR.
- No runtime speed-up is proposed. Nothing in the audit showed a hot spot, and a change without a bench number is not worth making.
- Two things found on the way are bugs, not cleanup. Both are in "Needs Jah" below.

## 1. Delete outright

| What | Size | Why it exists | Evidence that nothing breaks |
|---|---|---|---|
| `forge/` | 6 files | MTR's Forge build | `settings.gradle` includes only `fabric`. Last commit touching it is upstream's. ATR is Fabric 1.20.1 only (PLAN.md). |
| 27 of the 34 jars in `libs/` (mappings for 1.16.5 to 1.20.4, Forge mappings, the non-dev 1.20.1 jar) | 19.1 MiB | MTR builds seven Minecraft versions | The build files name seven jars: mappings-common, mappings-fabric-1.20.1-dev, Shadow-Libraries net and util, Transport-Simulation-Core, its Build-Tools, Mod-API-Tools. The rest are never resolved. Proof: `:fabric:remapJar` without them gives a jar with the same 14,433 entries and the same CRC for each as master's CI jar. |
| `images/` | 55.2 MiB, 116 files | Pictures for upstream's wiki and README | No file in the repo mentions `images/`. CHANGELOG 1.0: "README no longer sends people to upstream images". |
| `examples/MTR Custom Resources.zip` | 273 KiB | Upstream sample pack | No mention anywhere in the repo. |
| `.github/workflows/crowdin.yml`, `crowdin.yml` | 2 files | Uploads strings to upstream's Crowdin project 455212 | Its own header says it must not run. ATR has no Crowdin project. |
| `buildSrc/.../ModUpload.java` | 73 lines | Uploads seven-version MTR jars to upstream's Modrinth and CurseForge projects | No Gradle file calls it. Release pages are filled by hand (`release.yml`). |
| `BuildTools.downloadTranslations`, the Gemini translation check, `getForgeVersion`, the Patreon fetch | about 190 lines | Upstream's release pipeline | `crowdinApiKey`, `geminiApiKey` and `patreonApiKey` are empty in `gradle.properties` and CI sets no secret, so these branches never run. The Patreon fetch reads upstream's campaign. `Patreon.java` is still generated (empty list), because the class is part of MTR 4.0.5. |
| 30 unused methods | about 140 lines | Left over from builders and screens | List in "Unused methods" below. None is named on `feat/v1-bridges`, `feat/trailer-shots`, `review/o1`, `feat/e1-editor` or `feat/e2-drawn-tracks`. |
| 34 of the 36 `*ForTest` hooks (32 on the Aurelia screens, `Announcer.historyForTest`, `ScriptingHandshake.publishedForTest`) | about 130 lines | Added with the panel in #8 for tests that were never written | No class in main refers to them, and no file in `fabric/src/test`, `ataTest`, `gametest`, `perfbench` or `camera` calls a `*ForTest` method, on master, `review/o1`, `feat/trailer-shots` or `feat/v1-bridges`. The two that stay are called from main. |

Unused methods: `AirportSpec.with*` (11: ApproachLights, ApronLanes, ApronMarkings, ControlTower, Forecourt, LandTransit, Perimeter, SupportBuildings, TaxiwayLights, TaxiwaySigns, TurnPads), `Landmark.Skin/Facade.with*` (8: Accent, Bands, Bay, Cornice, Relief, Storey, Wall, Window), `StylePalette.tactileId`, `AtrMenuScreen.openNamed`, `StationWrenchScreen.openStation/openBridge/openEarthworks`, `LiveClient.onboardBusy`, `DragListWidget.getSelected/currentOriginalIndices`, `PathSignals.metresToStop`, `BlockTrainCoupler.getMode/getKeep`.

## 2. Simpler once those are gone

| What | Now | After | Evidence |
|---|---|---|---|
| Dependency versions | Fabric Loader, Fabric API, Mod Menu and Jade are looked up on the network every time Gradle configures; `occlusionculling:+` (shaded into the jar), the shadow plugin, foojay, JUnit and buildSrc's libraries float | Pinned to what resolves today: loader 0.20.1, Fabric API 0.92.12+1.20.1, Mod Menu 7.2.2, Jade 11.13.3+fabric, occlusionculling 0.0.9-SNAPSHOT | If `meta.fabricmc.net` or Modrinth is down, `getJson` returns `{}` after five tries and configuration throws. Two builds of one commit can ship different shaded classes. |
| `BuildTools.java` | 455 lines, loader and version switches for seven versions and two loaders | Fabric 1.20.1 only | With Forge and the lookups gone, buildSrc drops the Crowdin client, httpmime and Mod-API-Tools. |
| `DevScreenshots` | 158 lines in the release jar, switched on by `-Data.devScreenshots` | In the perf bench source set, which never ships | No Gradle task sets the flag. Two handoffs use it by hand (`docs/handoffs/HANDOFF-2026-11-09-PHASE-1.md:17`, `PHASE-2.md:11`); the second starts `runPerfbench`. After the move the flag works with `runPerfbench` and no longer with `runClient`. |
| CI path filter | Docs-only PRs skip the suites | Also skip them for PRs that touch only `aureliamap/` or `tools/`, which have their own build or none | `aureliamap/` has its own workflow and is not in `settings.gradle`. Unsure still means run everything, and `ci-ok` stays. |
| PLAN.md | Points to `archive/` (not in the repo) and to `ATR-GOAL.md` (untracked) | Says only what is in the repo | `ls archive` fails; `git ls-files ATR-GOAL.md` is empty. |

## 3. Looked at and left alone

- Upstream MTR classes with no caller in ATR (`TrigCache`, `DebugKeys`, `TrainType`) and all `assets/mtr` files: add-ons and resource packs may use them.
- `org/mtr/libraries` (20.4 MB, 4514 classes): byte-for-byte the same set as MTR 4.0.5, and add-ons link against it.
- `RouteControl.toEntries/fromEntries`: no caller, but their comment says they are for the world save ("new keys only"), so they read as an unfinished part of route control, not a leftover.
- `CallingPages` and `LiftGlassRegions`, and 70 methods used only by tests: removing them means removing tests.
- The particle `StationPreview` next to the hologram: PLAN keeps it as the fallback for slow PCs.
- Old `station_wrench` and `airport_wrench` items, and the word parser (14 callers: Studio, presets, wrench screens).
- Nine settings classes that each read and write their own file (1185 lines): the files must stay as they are for old configs, and a shared helper would save about 200 lines for a lot of churn.
- Threads: ATR adds seven executors (map saver, Studio's pool of two and its planner, the station planner, the preview, the hologram, the model loader). An idle thread costs no CPU. Merging them needs a measured reason.
- `aureliamap/` (BlueMap fork, 998 files, own JDK 25 build): a shipped feature. Moving it to its own repo is a product call.
- `website/` (60 files): see the first bug below.
- `tools/*.py`: the scripts that made the A320, car and bike models. Not in the jar.
- Asset scan of the ATR namespaces: no file whose name is never mentioned, apart from the passenger skins in `assets/aurelia/textures/entity/passenger`, which `PassengerRenderer.java:148` loads by number (`p%02d.png`).

## 4. Needs Jah (found on the way, not cleanup)

1. **The Resource Pack Creator page is empty in every ATR jar.** MTR 4.0.5 carries the built website in `org/mtr/mod/generated/WebserverResources.class` (964,587 bytes). In ATR's v1.0.2 jar and in master's CI jar that class is 781 bytes: `get()` returns null for every path, so `http://localhost:<port>/creator/` serves nothing. Cause: nobody runs `ng build` in `website/` before Gradle, in CI or in `release.yml`. Choices: build the website in the release job (one Node step), or say the creator is not part of ATR and remove the item that opens it.
2. **ATR ships English only for MTR's own strings.** MTR 4.0.5 has 42 files in `assets/mtr/lang`; ATR's jar has 1 (`en_us.json`, the only one tracked). The others came from Crowdin at build time and are git-ignored. Choice: commit the 4.0.5 language files (MIT) so the 41 other languages come back, with ATR's new strings in English.
3. **`.github/FUNDING.yml` points the repo's Sponsor button at upstream's Patreon** (`minecraft_transit_railway`). Keep as credit, or remove.
4. **`ATR-GOAL.md` is not in the repo** though PLAN.md names it as the brief.

## 5. Measured

| | Value | Source |
|---|---|---|
| CI wall time, code PR | 3.2 to 7.8 min, typically 4 | last 40 runs of `ci.yml` |
| CI wall time, docs-only PR | 0.2 min | runs 43251742511, 43251446145 |
| CI runner time, code PR | 39.0 and 40.3 min over 17 jobs | runs 43290846812, 43292002766 |
| Release jar | 93,273,411 bytes (MTR 4.0.5: 80.9 MiB) | master CI artifact |
| Tracked files | 220.9 MiB, 9796 files | `git ls-tree -r -l origin/master` |
| Gradle configuration (`help --offline`) | 6.4 s, 5.2 s | two local runs |
