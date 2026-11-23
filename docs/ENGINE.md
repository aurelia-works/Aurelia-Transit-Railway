# The simulation engine jar

`libs/Transport-Simulation-Core-0.0.1.jar` is MTR's simulation engine (Transport Simulation Core): trains, depots,
signals, departures and saved data. Since 2026-11-23 we build it ourselves from our fork,
[aurelia-works/Transport-Simulation-Core](https://github.com/aurelia-works/Transport-Simulation-Core), on branch
`atr/engine`. That branch starts at upstream commit `ecde724` (2025-10-14), the commit the jar MTR shipped came from.

## Rebuilding the jar

The fork's `atrJar` task does the build. It copies the sources, points fastutil and Gson at the Shadow-Libraries
copies (`org.mtr.libraries.*`), swaps the few Java 21 calls for Java 17 forms, and turns records into classes with
public fields, as the old jar had them. It then compiles for Java 17 against ATR's own `libs/Shadow-Libraries-*.jar`.

```sh
cd <fork checkout, branch atr/engine>
./gradlew generateJavaSchemaClasses atrJar -PatrLibs=<ATR repo>/libs
cp build/atr/libs/Transport-Simulation-Core-0.0.1.jar <ATR repo>/libs/
```

The fork's build needs JDK 21 to run. The jar it makes runs on Java 17.

## How the first build was checked against the jar MTR shipped

- Classes and methods match. The differences:
  - Helper code that Java adds automatically. The new jar has no `Outer$1` helper classes (9 of them, such as
    `Depot$1`). They are empty: Java 8 needed them to reach private constructors of inner classes, Java 11 and later don't.
  - Inner code (lambdas) nested in other lambdas is named after its method instead of `lambda$null$`, with the same
    numbers. The numbers change only in `MessagePackWriter`.
  - Six data classes have public fields again, as in the old jar (`Vector`, `LatLon`, `Connection`,
    `IndependentConnection`, `Request`, `Simulator.FileLoaderHolder`).
- Every method makes the same calls, apart from differences in how javac compiles code: string joining, null checks,
  closing resources, and private calls. Two classes differ beyond that, and both behave the same:
  - `ConnectionScanAlgorithmProcessor` adds a `Long` through `add(Long)` instead of `add(long)`.
  - `MessagePackWriter` calls its private `serializePart` directly instead of through a wrapper.
- ATR compiles, and `:fabric:test -x runGameTest` passes: 355 tests, 0 failures, `DropInSignaturesTest` included.
- A real world (24 stations, 53 platforms, 7 sidings, 13 routes, 20 depots, 18 lifts, 305 rails; 440 objects) gives
  the same JSON and the same save-file bytes with both jars.

## Rules

- Any change to the engine goes on the fork's `atr/engine` branch. Then rebuild this jar in the same ATR PR.
- Old worlds must load unchanged, and MTR add-ons must keep working. Keep method names, keep public fields public,
  and don't add lambdas before existing ones in classes that ATR mixins target (`Siding`, `Depot`, `Vehicle`,
  `VehicleExtraData`, `PathData`, `SidingPathFinder`).
