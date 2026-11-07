# ATR P3.4 handoff (2026-11-01)
Branch: feat/passenger-doors. **Not committed**: P3.2, P3.3 and P3.4 are all in the worktree, waiting on Jah's say-so.

## Built
- Passengers tab rows 15-17: Real routes on/off, Route range (4-32, default 16), Longest route search (500-12000, default 6000). Saved in `config/aurelia/passenger-draw.json` under new keys.
- Routes off clears the walk maps and stops scanning: straight walks, new people just appear.
- Range changes reach a platform on its next 30 s rescan.
- `exitMicros` timer; `routeMicros` now also covers arrivals.
- `PassengerShots`: demo 60, 128 walkers, screenshot of the new rows.

## Evidence (`fabric/run/perf/passengershots.txt`)
- Fps 87 with 41 people, 97 with figures off. renderMicros 871, scanMicros 31. The earlier 38 fps did not come back; its cause is unknown, but figures were not it (that run had 32 with figures off).
- Worst routing frame 10.2 ms (112 walks, 128 walker cap); worst exit search 5.5 ms.
- routed 112, straight 0. Crossing a block 6.
- Passed: PassengerWalkGridTest, PassengerFiguresTest, DropInSignaturesTest.

## Known faults
- A burst is routed in one frame, so it can hitch (10 ms). Not fixed.
- P3.3 and P3.2 faults stand.

## Not tested
Routes off, slider extremes, a real busy station, Jah in game, the full suite. The screenshot shows the two sliders; the Real routes button was not seen.

Test: `./gradlew :fabric:test -x runGameTest --offline --tests '*PassengerWalkGridTest' --tests '*PassengerFiguresTest' --tests '*DropInSignaturesTest'`
Demo: `JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo --offline`
