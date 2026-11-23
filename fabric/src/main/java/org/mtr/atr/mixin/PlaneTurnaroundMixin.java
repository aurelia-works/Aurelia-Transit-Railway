package org.mtr.atr.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.data.Siding;
import org.mtr.core.data.TransportMode;
import org.mtr.core.data.VehicleCar;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.engine.EngineFeature;
import org.mtr.mod.engine.EngineSettings;
import org.mtr.mod.engine.PlaneTurnaround;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Airplanes stay at each gate on their route for a turnaround that fits their size (see {@link PlaneTurnaround}).
 * The longer stops are written into the siding's own copy of the depot path just after MTR copies it, before MTR works
 * out the timetable from it, so departures, arrival boards and the planes all agree. Nothing here is saved: the copy is
 * rebuilt every time the path is, so switching the feature off takes effect on the next path build.
 */
@Mixin(value = Siding.class, remap = false)
public abstract class PlaneTurnaroundMixin {

	@Shadow
	@Final
	private ObjectArrayList<PathData> pathMainRoute;

	@Inject(method = "generatePathDistancesAndTimeSegments", at = @At(value = "INVOKE", target = "Lorg/mtr/core/path/SidingPathFinder;overlappingPaths(Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;)Z", ordinal = 0))
	private void atr$planeTurnaround(CallbackInfo callback) {
		final Siding siding = (Siding) (Object) this;
		if (siding.getTransportMode() != TransportMode.AIRPLANE || !EngineSettings.enabled(EngineFeature.PLANE_TURNAROUND)) {
			return;
		}
		double planeLength = 0;
		for (final VehicleCar car : siding.getVehicleCars()) {
			planeLength += car.getLength();
		}
		for (int i = 0; i < pathMainRoute.size(); i++) {
			final PathData pathData = pathMainRoute.get(i);
			// a segment that ends at a gate carries that platform's id and its dwell
			if (pathData.getSavedRailBaseId() != 0 && pathData.getDwellTime() > 1) {
				final long dwell = PlaneTurnaround.dwellMillis(pathData.getDwellTime(), planeLength);
				if (dwell != pathData.getDwellTime()) {
					final PathDataSchemaAccessor accessor = (PathDataSchemaAccessor) pathData;
					pathMainRoute.set(i, new PathData(pathData.getRail(), pathData.getSavedRailBaseId(), dwell, pathData.getStopIndex(), pathData.getStartDistance(), pathData.getEndDistance(), accessor.atr$startPosition(), accessor.atr$startAngle(), accessor.atr$endPosition(), accessor.atr$endAngle()));
				}
			}
		}
	}
}
