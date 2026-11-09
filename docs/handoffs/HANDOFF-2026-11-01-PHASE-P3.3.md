# ATR P3.3 handoff (2026-11-01)
Branch: feat/passenger-doors. **Not committed**: P3.2 and P3.3 are both in the worktree, waiting on Jah's say-so.

## Built
- `PassengerWalkGrid`: `Climb` kinds (flat, both ways, up only, down only), `scan` overload, `canStep`, `exits`.
- `PassengerRenderer`: grid is 8 down, 8 up, margin 16. `climb` reads stairs, slabs, escalator steps. Handrails do not block. `withExits` stores ways off the floor plus lift landings. `startArrivals` walks new waiting people in from exits. `fromCar` walks alighters out to exits, forwards. Exits are shared round robin.
- `PassengerWalk`: `arriving`, `fade`.
- Demo: deck three up, stairs x 262, escalator x 270 (runs down at stop 1, up at stop 2), real MTR lift x 282, at both stops; floor z 10-12.
- `PassengerShots`: counts by exit, lift list, skips sloped legs and handrails in the crossing check.
- `PassengerWalkGridTest`: new `levelsChangeOnlyOnStepsAndEscalatorsGoOneWay`.

## Decisions
- A level changes only on stairs, slabs or escalators. That is what keeps people off the track now the grid goes below the platform.
- A lift landing counts only if it connects to the platform both ways.
- An exit is a way 3 levels off the platform floor.

## Evidence (last demo run, `fabric/run/perf/passengershots.txt`)
- arrived by stairs 24, escalator 10, lift 10; left by stairs 4, escalator 4, lift 4.
- routed 56, straight 0, worst routeMicros 3814, scanMicros 49.
- crossing a block 5: all end at a waiting spot inside a pillar (P3.2 known fault), z about 3.
- "straight arrival" lines in that file are routed one-leg walks, not failures: the check is on point count.
- `PassengerWalkGridTest`, `DropInSignaturesTest` passed before the last two-way landing and demo edits; the grid did not change after.

## Known faults
- Lift walkers fade out or in at the landing. They do not wait for, ride or match the real car.
- A walker can step sideways off an escalator where a floor is level with it. Seen once in the run: (271.5 -58 11.5) to (272.5 -59 11.5) on the demo's added floor.
- Walkers ride escalators at walking pace.
- `withExits` search and lift paths run in one frame; not timed separately.
- Fps in the last run was 38 with 40 people (92 in P3.2's run). Cause not checked; other apps may have been running. Measure in P3.4.
- Only exits within 16 blocks of the rail and 8 levels are seen.
- `:fabric:test` without `-x runGameTest` fails 5 ATA `loot_steel_panel*` game tests (missing loot tables). Not from this work; not investigated.
- Earlier P3.2 faults stand.

## Not tested
Other trains, real stations, deep stations, curved platforms, multiplayer, the full suite. Jah has not seen it in game. Only the final alighting screenshot was looked at; it does not show anyone on an exit.

## Next, P3.4: panel and cost
Sliders, then route cost at a busy station, and the fps drop above.

Test: `./gradlew :fabric:test -x runGameTest --offline --tests '*PassengerWalkGridTest' --tests '*DropInSignaturesTest'`
Demo: `JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo --offline`
