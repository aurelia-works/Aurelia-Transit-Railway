# HANDOFF 2026-11-09, phase 5 (SG #60, cab HUD): done

## Done
- #60 merged as 4e71e066d (squash). Sonnet verify-pr PASS on 9671b28; CI all 9 green.
- Seen in game: perfbench client in "ATR RT" (copy of ATR Visual), riding the parked manual NS Civity at Depot (siding x 328-372, z 0) with `mtr:creative_driver_key`.
- Bug found and fixed (DriverSpeedMessageMixin): MTR's "Speed: x m/s" action-bar line was drawn across the desk panel. A vanilla `InGameHud.setOverlayMessage` mixin drops only `gui.mtr.vehicle_speed`, and only while `DriverClient.isDriving()`.
- Shots: before `vis-sg1-cab-hud.png`; after `vis-sg2-cab-hud.png`, `-cab-hud-car`, `-no-key`, `-train-outside` (perf/screenshots).

## Not tested
- A driven, moving run; AWS/TPWS at real signals and their sounds; door keys; the stop board; junction route following.

## Known nits (not fixed)
- Door messages and Announcer action-bar text can briefly cover the panel.
- `isDriving()` stays true with a screen open (the speed line is dropped then too).
- `SignalLookahead.preBlocked` trusts any train's reservation at a junction.

## Next
Phase 6: R. Perfbench numbers into `docs/perf-launch.md` plus a RENDERING-tab panel shot of the block-entity counter; docs PR. The ATU Test copy has no trains, so the bench runs in "ATR Bench" (a copy of ATR RT with the player next to the parked train).
