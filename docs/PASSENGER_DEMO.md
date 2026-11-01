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

`demo` turns the passenger service on, finds the nearest MTR platform within 128 blocks, and adds waiting people. Chat
says how many, how far away the platform is, and whether a route stops there. A platform with no route keeps its people,
but nobody can board. `demo` and `clear` change only the on switch; the caps on the panel stay as they were.

Passengers show as simple solid people on the floor beside the rail, clear of the train. Size, colours, outlines and
distances are on the Aurelia panel's **Passengers** tab. Figures count distance to the nearest point of the platform: up
to 16 inside 32 blocks, one past that, none past 64 (all on the panel).

## Ready-to-test instance

```bash
./gradlew :fabric:runPassengerDemo
```

The bench world `ATU Test` is bare track with a manual train, so this copies it once to `run/perf/saves/ATR Passenger
Demo` and sets the copy up: daylight running, a platform beside the rail, the train switched to automatic, the depot
regenerated, and `/atr_passengers demo 40`. The game stays open. Wait about two minutes for the first train.

## Automated proof

```bash
JAVA_TOOL_OPTIONS="-Datr.passengershots=true" ./gradlew :fabric:runPassengerDemo --offline
```

`PassengerShots` saves `fabric/run/perf/screenshots/passenger-demo-1.png` to `-3.png` (middle of the platform, far end,
train boarding), writes the counts to `passengershots.txt`, and closes the game.

## What this proves, and what it does not

Proves:
- the server fills a real platform and the counts reach the client;
- figures stand on the platform, and show from its far end;
- a real automatic train stops and people board it (48 boarded, 1.5 ms per update, 2026-11-01).

Does not prove:
- people getting off: the proof run ended before the train reached their stations;
- individual pathfinding or walking animation;
- passengers using lifts, stairs or escalators.

Passengers are counts per platform and train, drawn as figures. The station graph and walkers are P3 work.
