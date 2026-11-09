# ATR P3.1 handoff (2026-11-01)
Branch: feat/passenger-doors (pushed).

Built: `fabric/src/main/java/org/mtr/aurelia/client/PassengerWalkGrid.java` (sliced scan through a Probe, A* over 4 sides with +/-1 steps, node limit) and `PassengerWalkGridTest` (round a wall, up stairs, node limit). Not wired into the game yet.

Decisions: grid is pure Java; the scan runs in slices on the render thread so no world access happens off-thread.

Not done, moved to P3.2: the real Probe (solid below, two clear cells above, using PassengerRenderer.floor rules) and scan cost on real blocks.

Next, P3.2: in PassengerRenderer.startWalks, build a grid per platform, scan in slices, and use its path for the leg from the spot to the cell outside the door (toCar). Keep the straight leg when there is no path. Add a pillar to the demo in PassengerDemoClient and prove it with PassengerShots.

Test: `./gradlew :fabric:test -x runGameTest --offline --tests '*PassengerWalkGridTest'`
Demo: `JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo --offline`
