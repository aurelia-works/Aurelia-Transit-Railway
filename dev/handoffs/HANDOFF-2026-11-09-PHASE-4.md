# HANDOFF 2026-11-09, phase 4 (RT #59, trains follow route control): done

## Done
- #59 merged as d9575becb (squash). Sonnet verify-pr: round 1 FAIL (one important finding), round 2 PASS on e8ac2e3; CI all 9 green.
- `rt/trains-follow-2` (76428ad48, 43af158e8) fast-forwarded `feat/route-control`; master merged in twice (MtrBridge conflict: both sides only added methods, kept both).
- Mixin check: the RT mixins are runtime injections into MTR core's `Depot`, `Siding` and `SidingPathFinder` (in `org/mtr/atr/mixin`), with no MTR source edited. The new `org/mtr/mod/data/RouteControl*` classes follow `DepotOptionsSync` (#35). DropInSignaturesTest passes.
- In game: a copy of "ATR Visual" as "ATR RT", with `atr_route_control.dat` setting Route 1 stop 2 to Alpha platform 2. The depot regenerates at server start. Platforms along the saved depot path (decoded with scratchpad `mtrpath.py`, msgpack venv) changed only at Route 1's Alpha stop; Route 2 (no control) kept MTR's platform.
- Reviewer finding fixed in e8ac2e3aa: clearing a route's control left the chosen platform until the next sync. It now refills MTR's list in the `Depot.writeRouteCache` shape (checked against the core jar with javap).

## Not tested
- Waypoints, the free-platform fallback, the editor screen, a dedicated server.
- Clear-then-regenerate in game (code trace only).
- A moving train: the test world's siding is manual (`maxVehicles -1`), so MTR never dispatches it.

## Leftovers
- Local branch `feat/route-control` is still checked out in another worktree; left alone.
- The test world `perf/saves/ATR RT` (copy; depot switched to the frequency table) is still in place for SG shots.

## Next
Phase 5: SG #60 (`feat/driving-quality`, worktree `~/.t3/worktrees/mod/sg-60`, head 9671b28e1). Cab HUD seen in game; MTR's speed line overlapped the panel, fixed with `DriverSpeedMessageMixin`. Sonnet verify-pr running.
