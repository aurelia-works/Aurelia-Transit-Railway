# MTR Traffic Addon with ATR

ATR does not build road traffic. Servers that want it can add the MTR Traffic Addon (MTA) by CookieCraftMods. This page
records a static check of MTA against ATR and the in-game test that still has to be run.

Checked on 2026-11-24: MTA source at commit `ab03e7e` (mod version 26.10.3, the `fabric-1.20.1` build, built against
MTR 4.0.5) against ATR master `af5cc5ba`. MTA is All Rights Reserved: its source was read to check compatibility only,
and nothing from it is copied into ATR.

## Result

Nothing found that should stop MTA loading on ATR. It still needs the in-game test below, because a static read
cannot prove mixins apply or that the two simulations agree at run time.

- **Dependency:** MTA asks for `mtr >= 4.0.4`. ATR's version `4.0.5+atr.1.2.0` satisfies it, because ATR keeps the
  mod id `mtr`.
- **Mixin targets:** all nine of MTA's mixins find their target in ATR, with the shapes MTA expects:
  - core (`libs/Transport-Simulation-Core-0.0.1.jar`): `Simulator.tick()`, `Vehicle.simulate`,
    `Vehicle.railBlockedDistance`, `SidingPathFinder.lambda$getConnections$0`, `PathData.getHexId` (two calls to
    `getHexIdRaw`), `DeleteDataRequest.delete(Simulator)`, and the accessor fields on `RailSchema`, `VehicleSchema` and
    `DeleteDataResponseSchema`;
  - mod: `RailType.getRailColor(Rail)`.
- **Shared hooks:** ATR also hooks `Simulator.tick()` (engine lag readout, head and return) and `Vehicle.simulate`
  (express first at junctions, head). MTA injects at the return of both, so they stack without a clash. No ATR mixin
  touches `railBlockedDistance`, `getHexId`, `getConnections` or `getRailColor`.
- **MTR API it calls:** `IDrawing`, `PacketUpdateData.sendDirectlyToServerRail`, `CustomResourceLoader`,
  `ItemRailModifier`, `ItemSignalModifier`, `BlockNode.IS_CONNECTED`, `RailType.SIDING` and `Init.sendMessageC2S` all
  exist in ATR with matching signatures.
- **Ids:** MTA registers everything under `mtr-traffic-addon`. Its recipes use `mtr:dashboard`,
  `mtr:rail_connector_siding` and `mtr:signal_connector_red`, and its item models use MTR textures; ATR still has all of
  them. It registers the `mtr:selected` item predicate on its own items only.

## Weak spots

- `SidingPathFinder.lambda$getConnections$0` is a compiler-generated name. If ATR ever ships a rebuilt core jar with
  another lambda in `SidingPathFinder`, MTA's path blocker hook would land on the wrong lambda or fail to apply.
  `DropInSignaturesTest` does not cover the core jar.
- ATR's route control (`SidingPathFinderRouteControlMixin`) can cancel `findPathTick` before MTA's path blocker runs.
  That only matters on routes ATR's route control is steering.

## What it means for Stratos roads

MTA's cars drive on MTR rails, not on blocks. A Stratos road gets traffic only if its lanes are also laid as MTR
rails, with MTA's spawn and despawn connectors at the ends. MTA also has its own railway level crossings ("Train"
intersections that turn road signals red and stop road cars when an MTR train comes). Worth knowing before Stratos
builds its own.

## Test in game

Use a fresh 1.20.1 Fabric instance with Fabric API, the ATR jar from `build/release/` and MTA 26.10.3 for Fabric
1.20.1. No other add-ons.

1. Start the game. It reaches the title screen; `logs/latest.log` has no `Mixin apply failed` or
   `InvalidInjectionException` line naming `mtr-traffic-addon`.
2. Make a creative world and `/op` yourself. Build a loop of MTR rail as road lanes, put a spawn connector at one end
   and a despawn connector at the other, open the Traffic Dashboard and press Refresh Routes. Cars spawn and drive.
3. Hold an MTR rail connector. The spawn and despawn rails show MTA's colours.
4. Run an ATR train over a rail that crosses the road lanes, with a Train intersection set up there. Road signals go
   red for the train, and the train stops behind a road car that is still on the crossing.
5. With traffic running, open ATR's panel Engine readout and check the lag number is sane, and check express-first at
   a junction still holds an all-stops train.
6. Use MTA's path blocker on one rail of a depot route and regenerate the depot. MTR picks another rail when one
   exists.
7. Delete a connector rail. Its entry disappears from the Traffic Dashboard.
8. Save and quit, then reopen the world: traffic comes back. Load an existing ATR world with MTA added: it loads.
   Remove MTA and load the test world again: it loads, and the connector rails stay as plain MTR rails.
