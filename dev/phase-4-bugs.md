# Phase 4: 1.0 bug fixes, results (2026-10-31)

Shortlist: `docs/phase-4-shortlist.md` in the project folder (MTR issue numbers there). Item 10 shipped in #15.
Core is the `Transport-Simulation-Core-0.0.1.jar` in `libs/`; every core line below was checked against that jar with `javap`, not only the newer `core/` source.

| # | Bug | Result | Evidence |
|---|---|---|---|
| 1 | Depot "Generate" says success when a siding can't reach the main path | Closed: already fixed in the 4.0.5 core | A siding that fails either way calls `Depot.sidingPathGenerationFailed()`; when all sidings finish, `Depot.finishGeneratingPath` sets `PATH_NOT_FOUND` if the count is above 0. The count is saved in `DepotSchema` (7 uses in the jar), the depot screen prints it (`EditDepotScreen` "N sidings failed") and the Aurelia depot checker reports `sidings_failed`. The reports are from MTR 3.x. |
| 2 | Changing a colour softlocks the game | Closed: not reproduced | The colour screen's text fields call each other back through RGB to HSB and back. That loop only ends if the round trip is exact; it is exact for all 16,777,216 colours (checked with `java.awt.Color`, 0 mismatches). Escape calls `onClose2`, which opens the parent screen once and doesn't call `super`, so there is no close loop. |
| 3 | PIDS shows the old destination until the path is refreshed | Fixed | The core copies `route.getDestination(i)` into each stop time at generation (`Siding` RoutePlatformInfo). `LiveDestinationMixin` makes arrivals read it from the live route (route edits update the same object in place). `LiveDestinationTest` failed (`expected New Town but was Old Town`), passes now. The mixin was confirmed merged into `Siding` on a game-test server. |
| 4 | Force-removing a rail node leaves its rails behind | Fixed | Only `onBreak2` (a player breaking it) deleted the rails. `BlockNode.onStateReplaced2` now deletes them when a node turns into a block that isn't a node (/setblock, /fill, other mods). Game test `RailNodeGameTests.railsGoWhenANodeIsReplaced` failed ("the node is gone but its rail is still in MTR"), passes now. |
| 5 | Lift doors stay open | Closed: not reproduced | The door opening is a pure function of `stoppingCoolDown` (core `Lift.getDoorValue`). The cooldown counts down every tick to 0 on the server, and on the client to 1, which reads as closed (below `DOOR_DELAY`). No path leaves it in the open range. |
| 6 | Player falls out when leaving a train or lift at low FPS | Fixed | One walking step is `millisElapsed * 0.005` blocks: about 1 block per frame at 10 FPS while sprinting. When that step crossed the doorway, the player was let go at their old spot inside the car, with no floor under them. The step now lives in `RidingStep` and lets the player go where the step took them. `RidingStepTest` failed ("let go inside the car at x 0.9"), passes now. Lifts use the same code. |
| 7 | Text-to-speech ignores game volume | Fixed (as far as the API allows) | Minecraft's narrator library (text2speech 1.17.9) has only `say(String, boolean)`, with no volume. Announcements and the ATA system voice now stay silent when Master or Voice is at 0 (`TtsVolume`). `TtsVolumeTest` failed, passes now. Volumes between 0 and 100% still speak at the system volume. |
| 8 | Rails stop drawing after long placing sessions | Closed: not reproduced | The rails to draw are rebuilt every frame from `railWrapperList` (`RenderRails.render`), with no growing cache. The rail culling queue is capped at 2 and drained every 10 ms, and each task's errors are caught (`WorkerThread`). One suspect is left: those queues are plain lists that the render thread and the worker share without a lock. Not changed without a repro. |
| 9 | Memory grows over a long session | Measured, see below | |

## 9. Memory
Heap used after a full GC (`jcmd <pid> GC.heap_info` after `GC.run`) on an idle dev server (flat world, creative, no trains), every 3 minutes:

| Time | Used |
|---|---|
| 14:09 | 368877K |
| 14:12 | 369059K |
| 14:15 | 369576K |
| 14:18 | 369061K |
| 14:21 | 368990K |

Flat within 0.2% over 12 minutes, so no server-side leak shows at idle. Not measured: the client, and a server with running trains and players. If the growth Jah saw is real, it is most likely on the client (render or culling queues, see item 8); the next check is a client heap dump after an hour of riding.
