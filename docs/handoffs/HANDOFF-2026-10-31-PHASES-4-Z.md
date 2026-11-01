# ATR handoff: phases 4 to Z (2026-10-31, stopped at 98% weekly usage)

Repo: aurelia-works/Aurelia-Transit-Railway. mod/ belongs to another session (chore/1.0.2, an uncommitted 1.0.2 bump; PR #37 supersedes it, so discard it). Work only in worktrees.

## Done (merged, reviewer PASS, CI green)
- Phase 4 shortlist bugs: #34 (07efa5a84). Results in docs/phase-4-bugs.md.
- Phase 6 depot controls: #35 (0193a14f5).
- Phase 8 sound (pitch range, motor bend, door chimes, squeal volume): #36 (9b8435c0d).

## Also merged (finished by Grok Bot)
- #37 release 1.0.2: 0ee679f. Tag v1.0.2 is on it, with ATR-fabric-4.0.5+atr.1.0.2-mc1.20.1.jar attached.
- #38 timetables: 992ba49. #39 passengers 9a: cd25a9e (both server registrations kept in the conflict).
- #38 and #39 landed after the tag, so they are not in the v1.0.2 jar. They go in the next release.

## In progress
- 9b feat/passengers-draw (ATR-p9b, stacked on #39), head 87c556db9.
  - Only the server sends waiting-per-platform and riding-per-train counts. No drawing yet, and it is not compiled.
  - Plan: WorldRenderEvents box figures along platforms (platform ends via reflection on SavedRailBaseSchema.position1/2) and in cars (getVehicleCarsAndPositions); LOD by distance; client panel sliders (draw on/off, distance, detail distance, max per platform).
  - Done when needs Jah: frame time in game.

## Not started
1d.4, O1, O2 (Blender; stop at "Jah reviews art"), O3, Z. Each needs its own plan in PLAN.md first. Also the check-in page (https://claude.ai/artifact/Tzp4F7R2fsimcsPN9zEDgM, one card per phase): not published.

## Needs Jah (in game, nothing was seen or heard)
- #34: PIDS live destination, low-FPS doorway step, TTS mute.
- #35: depot buttons; All doors also opens doorways MTR would keep shut (by design).
- #36: chimes and pitch. Wide pitch is ON by default and the motor bend is 15%, so existing trains sound different (both switchable). Not done: a per-train chime choice and a squeal speed threshold.
- #38: weekday/weekend switch, "Cancelled" on PIDS.
- #39: passengers default OFF; live cost on the Passengers tab.
- MTR bug spotted, not fixed: ScheduledSound.play() plays its own sound once per scheduled entry.

## Resume
Next is 9b: rebase ATR-p9b (feat/passengers-draw) onto master, then build the drawing.
Worktrees ATR-p4, p6, p7, p8, p9 and rel are all merged and can be removed. Dev server and caffeinate are stopped.
