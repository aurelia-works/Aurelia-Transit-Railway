# Add-on compatibility notes

A static check of add-on jars against ATR. It covers every call they make into `org.mtr`, every mixin target class and
method, and every `INVOKE`/`FIELD` injection point and `@ModifyConstant` value. Where a point does not resolve in ATR, it
was compared with MTR 4.0.5. It also checked for ATR mixins that redirect or change the same call. A static check cannot
prove the game launches, and none of these add-ons has been launched with these fixes yet. MTR Traffic Addon has its own page: [mtr-traffic-addon.md](mtr-traffic-addon.md).

Checked on 2026-11-26 against ATR master `abaeb0dd6`.

| Add-on | Version checked | Result |
|---|---|---|
| MTR Rail Works (`mrw`) | 0.2.3+1.20.1, Fabric | Crashed at launch (reported). Fixed, not launch-tested. |
| MAGIC (`jme`) | 1.5.1-1.20.1, Fabric | Crashed at launch (reported). Fixed, not launch-tested. |
| MTR France Addon (`mtrfranceaddon`) | fabric-2.0.1+1.20.1 | Nothing found. |

## MTR Rail Works

- MRW reads `ItemNodeModifierSelectableBlockBase.radius` with an `@Accessor`. ATR dropped the field when the Rail Builder
  got its per-stack width, so Mixin threw `InvalidAccessorException` at launch. The field is back with MTR 4.0.5's name
  and type, set to `width / 2` as before. `DropInSignaturesTest.everyMtr405FieldStillExists` now guards every MTR 4.0.5
  field.
- MRW injects at the tail of `RailAction.<init>` with the 7-argument MTR handler. ATR's added Rail Builder constructor
  comes second in the class, and a bare `<init>` selector only takes the first match, so MRW still lands on MTR's.
  Keep MTR's constructor first.
- The Rail Builder keeps its width and height on the item stack. MRW reads the item's fixed `radius` and `height`
  instead, so MRW's multi-node clicks build the Rail Builder at its default size, not the size set on the stack.

## MAGIC

- MAGIC's `VehicleMinimumDwellMixin` redirects the first `Math.min(long, long)` in `Vehicle.simulateStopped`, the call
  ATR's `TerminusCatchUpMixin` also redirected. Two redirects on one call crash the game at launch. ATR's is now a
  `@WrapOperation`. MixinExtras applies wraps after every redirect (`WrapOperationInjectionInfo` is a late injector), so
  ATR's wrap runs around MAGIC's handler: at a terminus ATR widens how much delay the stop may take back, then MAGIC
  caps it for routes that have a MAGIC minimum dwell.
- MAGIC's `DepotCronTimetableMixin` changes the argument of the second `Collections.sort` in
  `Depot.generatePlatformDirectionsAndWriteDeparturesToSidings`, the call ATR's `ClockFaceTimetableMixin` redirected.
  A `@ModifyArg` and a `@Redirect` on one call can clash depending on which applies first. ATR's is now a
  `@WrapOperation` too, applied after MAGIC's change. When ATR's clock-face
  timetable is on, it still replaces the departure list, so MAGIC's change to that list does not show on those depots.
- MAGIC's `VehicleSpeedLimitMixin` redirects two calls in `Vehicle.simulateMoving`; ATR's `HillSlowdownMixin` wraps one
  of them, and a wrap and a redirect stack.
- Eight of MAGIC's injection points do not resolve in ATR (for example `RenderRails.lambda$null$16` and
  `DashboardScreen.mouseClicked`). They do not resolve in MTR 4.0.5 either, so they are MAGIC's own targets for other MTR
  versions, not something ATR removed.

## MTR 4.0.5 members ATR still lacks

Every MTR 4.0.5 method and field in `org.mtr.mod` exists (`DropInSignaturesTest`), except as below. The test leaves out
`org.mtr.mod.generated`, members whose descriptor names a Minecraft type, and the core jar. Two fields changed type and
cannot come back under the same name:

- `BveVehicleSound$VehicleLoopingSoundHolder.soundLoopRun`: one sound, now a map of sounds by speed.
- `BveVehicleSound$VehicleLoopingSoundHolder.soundLoopFlange`: one sound, now an array.

Both are in a private nested class; none of the add-ons checked here reads them.
