# ATR P3.2 handoff (2026-11-01)
Branch: feat/passenger-doors. **Not committed**: five files are changed in the worktree, waiting on Jah's say-so.

## Built
- `PassengerRenderer`: real probe `standable` (solid below no taller than a block, two clear cells above). `scanWalkMaps` scans the nearest platform with a missing or 30s-old map, 400 cells a frame, on the render thread. `leg` routes the walk from a spot to the cell outside the door; `toCar` adds the door and inside points. `nearestDoor` skips doors with something built in front of them unless all have. `reach` shortens the walk away so it stops where the floor ends.
- `PassengerWalkGrid`: `standable`, `nearest`, and `turns` (cuts corners only when a person's width stays on standable cells of one level).
- `PassengerWalkGridTest`: new `cornersAreCutButNeverThroughAPillar`.
- Demo (`PassengerDemoClient`): four pillars at the demo platform, plus a floor and four pillars at the next stop (x 128-164, force-loaded).
- `PassengerShots`: checks every walk against the blocks it passes and against the straight leg it replaced; overhead camera at both stops; new `passenger-demo-alighting.png`.

## Decisions
- The grid starts at the platform's floor level and has 3 levels. Nothing goes below it, so people never route over the trackbed. P3.3 has to widen this for stairs down.
- Dev counters and `walkListener` are public statics on `PassengerRenderer`, set only by the dev run.
- Panel sliders stay in P3.4, as planned. Grid limits are constants for now.

## Evidence (last demo run, `fabric/run/perf/passengershots.txt`)
- routes 60 (18 alighting), crossing a block 0, routed 60, straight 0, most turns 2.
- The straight leg would have crossed 1 of those 60. Doors behind pillars were the bigger cause: 6 and 3 crossings in the two runs before the door fix.
- scanMicros 65 on frames that scan (cost on real blocks, carried over from P3.1). Worst frame of working out routes: 3262us for an 18-person batch; 538-1119us for boarding batches.
- `PassengerWalkGridTest` and `DropInSignaturesTest` pass.

## Known faults
- Route cost lands in one frame and grows with walkers. 3.3ms at 18; the 128-walker cap is not measured. Measure in P3.4; if it hurts, spread routes over frames.
- Waiting figures can still stand inside a pillar when spots are dense (`PassengerFigures.spots` draws them there when a column has no floor). Their walk leaves from the nearest free cell.
- A map can be up to 30s old, and walkers before the first scan take the straight leg.
- Flat paths only have been proven in game. Steps exist in the grid (unit test only).
- Platforms level with the track have no pit to keep people out; not handled.
- One unexplained flat stone-brick shape shows in `passenger-demo-alighting.png` near x 140-150. I did not place it, and did not check what it is.

## Not tested
Other trains or door layouts, curved or diagonal platforms, slabs and stairs on a real platform, multiplayer, the full test suite, and any real station. Jah has not seen it in game.

## Next, P3.3: stairs, escalators, lifts
Walkers leave and arrive by real exits. Start with `nextWalkMap` (grid levels) and `leg`. Plan table in `PLAN.md`, section P3.

Test: `./gradlew :fabric:test -x runGameTest --offline --tests '*PassengerWalkGridTest' --tests '*DropInSignaturesTest'`
Demo: `JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo --offline`
