# Passenger demo

This is the quickest way to see the AI passengers working in game. It uses the real `PassengerNetwork` and
`PassengerService`, not a separate renderer or a mock.

## Commands

Operator only (permission level 2):

```
/atr_passengers demo [count]   # default 20, max 60
/atr_passengers clear          # removes the demo and turns passengers off
```

Client-side switch for the figures:

```
/atr_passenger_draw on
/atr_passenger_draw off
/atr_passenger_draw            # toggle
```

`demo` turns the passenger service on, finds the nearest MTR platform within 128 blocks, and adds waiting people through
`PassengerNetwork.spawn`. The next service tick keeps them alive and boards them when a train stops. The blue boxes are
waiting passengers; orange boxes are passengers inside a train.

## Ready-to-test instance

For a one-command morning test, run:

```bash
./gradlew :fabric:runPassengerDemo
```

That boots `run/perf/saves/ATU Test`, sets daylight and clear weather, puts the camera above the platform at
`276 -58 8`, turns passenger drawing on, and runs `/atr_passengers demo 40`. The game stays open so you can walk around
and watch. Open the Aurelia panel's **Passengers** tab to see the counts.

To stop the demo: `/atr_passengers clear`.

## Bench-world tour

The `ATU Test` bench world has MTR platforms around `x=276, y=-58, z=0`.

1. Launch the client with the bench world:
   ```bash
   ./gradlew :fabric:runPerfbench -Patr.world="ATU Test"
   ```
2. Teleport to the platform:
   ```mcfunction
   /tp @s 276 -58 8 facing 276 -59 0
   ```
3. Run:
   ```mcfunction
   /atr_passengers demo 40
   /atr_passenger_draw on
   ```
4. Look along the platform. You should see up to eight blue wireframe figures at the platform ends, with more waiting
   than that when the draw cap allows.
5. Run `/atr_passengers clear` when done.

The automated proof is `PassengerShots` (`src/perfbench`):

```bash
JAVA_TOOL_OPTIONS="-Datr.passengershots=true" \
  ./gradlew :fabric:runPerfbench -Patr.world="ATU Test" --offline
```

It saves `fabric/run/perf/screenshots/passenger-demo-1.png` through `-3.png` and writes `passengershots.txt`.

## What this proves, and what it does not

Proves:
- the server spawns passengers from the real network;
- the counts reach the client;
- the client renderer draws them at the real platform;
- the service can board and alight them when a train stops.

Does not prove:
- individual pathfinding or walking animation;
- passengers using lifts, stairs or escalators.

Passengers are aggregate counts per platform/train, drawn as figures. The station graph and walkers are P3 work.
