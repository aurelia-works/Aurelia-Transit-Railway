# Passengers, phase 9a: simulation (2026-10-31)

Passengers are plain records, not mobs: `org.mtr.mod.passenger.PassengerNetwork`, one per dimension. `PassengerService` updates each network once a second on that dimension's MTR simulation thread.

- **Spawn.** Each platform a route stops at gets new passengers at the set rate. Each one heads for a station reachable by staying on one of that platform's routes.
- **Board.** A train standing on a route at a platform takes passengers whose station is ahead on its route. It takes 3 per car per second, up to seats per car times cars.
- **Leave.** Riders get off at their station, or at the end of the train's route if their station isn't on it.
- **Give up.** Anyone who waited 10 minutes goes home. Deleted trains and platforms take their passengers with them.
- **Caps** (Aurelia panel, Passengers tab, operators only): on/off (default off), new per platform per minute, most waiting per platform, most in the world, and seats per car. Saved in the overworld's `atr_passengers.dat`. Passengers themselves are not saved, so a restart empties the platforms.
- Everyone sees the same passengers: the server owns them and sends counts and the measured cost to every player each second. Drawing them is phase 9b.

## Cost

Measured with a synthetic load on the `PassengerNetwork` update (spawn, expire, and every train exchanging), 500 one-second updates after warm-up, on Jah's Mac:

| Platforms | Trains | Passengers | Average per update | Worst |
|---|---|---|---|---|
| 50 | 20 | 10,000 | 0.10 ms | 0.5 ms |
| 300 | 100 | 20,000 | 0.18 ms | 2.4 ms |
| 1,000 | 400 | 20,000 | 0.15 ms | 1.6 ms |

The update runs once a second, not every tick, and on MTR's simulation thread, so with threaded simulation (the default) it does not touch the server tick. The panel shows the live cost of the real update, including reading routes and trains, as a running average.

Not measured: the live cost on a real world with trains. That number is on the panel the first time passengers are switched on.
